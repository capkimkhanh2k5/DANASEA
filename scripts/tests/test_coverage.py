import copy
import json
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'qa' / 'check_coverage.py'
CLASS = 'example/BusinessUseCase'


def report(line=(80, 20), branch=(7, 3), method=(9, 1), class_line=(8, 2), class_branch=(3, 1)):
    root = ET.Element('report', name='backend')
    package = ET.SubElement(root, 'package', name='example')
    target = ET.SubElement(package, 'class', name=CLASS)
    for owner, values in ((root, {'LINE': line, 'BRANCH': branch, 'METHOD': method}),
                          (target, {'LINE': class_line, 'BRANCH': class_branch, 'METHOD': (1, 0)})):
        for metric, counts in values.items():
            if counts is not None:
                covered, missed = counts
                ET.SubElement(owner, 'counter', type=metric, covered=str(covered), missed=str(missed))
    return ET.tostring(root, encoding='unicode')


def policy():
    return {'minimum_ratios': {'LINE': 0.8, 'BRANCH': 0.7, 'METHOD': 0.9},
            'minimum_covered': {'LINE': 80, 'BRANCH': 7, 'METHOD': 9},
            'required_classes': {CLASS: {'minimum_ratios': {'LINE': 0.8, 'BRANCH': 0.75},
                                         'minimum_covered': {'LINE': 8}}}}


class CoverageGateTest(unittest.TestCase):
    def run_gate(self, xml=None, rules=None, raw_policy=None, extra_args=()):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_path = root / 'jacoco.xml'
            if xml is not None:
                report_path.write_text(xml)
            policy_path = root / 'policy.json'
            policy_path.write_text(raw_policy if raw_policy is not None else json.dumps(rules or policy()))
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(report_path), '--policy', str(policy_path), *extra_args],
                capture_output=True, text=True, check=False)
            return result

    def assert_rejected(self, result, message):
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(result.stdout.startswith('{'), 'Missing JSON decision: ' + result.stderr)
        summary = json.loads(result.stdout)
        self.assertFalse(summary['passed'])
        self.assertIn(message, '\n'.join(summary['errors']))
        return summary

    def test_accepts_exact_thresholds_and_reports_observed_counters(self):
        result = self.run_gate(report())
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        summary = json.loads(result.stdout)
        self.assertTrue(summary['passed'])
        self.assertEqual(summary['global']['LINE'], {'covered': 80, 'missed': 20, 'total': 100, 'ratio': 0.8})
        self.assertEqual(summary['required_classes'][CLASS]['BRANCH']['covered'], 3)

    def test_line_ratio_drop_fails_even_with_covered_count_preserved(self):
        summary = self.assert_rejected(self.run_gate(report(line=(80, 21))), 'global LINE ratio')
        self.assertEqual(summary['global']['LINE']['covered'], 80)

    def test_branch_ratio_drop_fails_independently(self):
        self.assert_rejected(self.run_gate(report(branch=(7, 4))), 'global BRANCH ratio')

    def test_covered_count_drop_fails_even_when_ratios_improve(self):
        self.assert_rejected(self.run_gate(report(line=(79, 0))), 'global LINE covered')

    def test_required_class_cannot_disappear_or_have_zero_executed_lines(self):
        missing = ET.fromstring(report())
        missing.find('package').remove(missing.find('package/class'))
        self.assert_rejected(self.run_gate(ET.tostring(missing, encoding='unicode')), CLASS + ' missing')
        self.assert_rejected(self.run_gate(report(class_line=(0, 10))), CLASS + ' LINE covered must be positive')

    def test_required_class_thresholds_apply_independently_of_global_coverage(self):
        for xml, expected in ((report(class_line=(8, 3)), 'LINE ratio'),
                              (report(class_line=(7, 0)), 'LINE covered'),
                              (report(class_branch=(3, 2)), 'BRANCH ratio')):
            with self.subTest(expected=expected):
                self.assert_rejected(self.run_gate(xml), CLASS + ' ' + expected)

    def test_branchless_required_class_satisfies_ratio_but_not_positive_covered_floor(self):
        result = self.run_gate(report(class_branch=None))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(json.loads(result.stdout)['required_classes'][CLASS]['BRANCH']['ratio'], 1.0)
        rules = policy()
        rules['required_classes'][CLASS]['minimum_covered']['BRANCH'] = 1
        self.assert_rejected(self.run_gate(report(class_branch=None), rules), CLASS + ' BRANCH covered')

    def test_required_global_branch_counter_cannot_be_omitted(self):
        self.assert_rejected(self.run_gate(report(branch=None)), 'global missing counter BRANCH')

    def test_missing_or_malformed_report_fails_closed(self):
        for xml in (None, '<report>', '<not-a-report/>'):
            with self.subTest(xml=xml):
                self.assert_rejected(self.run_gate(xml), 'report')

    def test_missing_counter_and_empty_executable_line_counters_fail(self):
        for xml, message in ((report(line=None), 'global missing counter LINE'),
                             (report(class_line=None), CLASS + ' missing counter LINE'),
                             ('<report/>', 'global missing counter LINE'),
                             (report(line=(0, 0)), 'global LINE has no executable items')):
            with self.subTest(message=message):
                self.assert_rejected(self.run_gate(xml), message)

    def test_malformed_negative_unknown_and_duplicate_counters_fail(self):
        for attribute, value in (('covered', '-1'), ('missed', '-2'), ('covered', '1.5'),
                                 ('covered', 'NaN'), ('missed', ''), ('type', 'TYPO')):
            with self.subTest(attribute=attribute, value=value):
                root = ET.fromstring(report())
                root.find('counter').set(attribute, value)
                self.assert_rejected(self.run_gate(ET.tostring(root, encoding='unicode')), 'counter')
        root = ET.fromstring(report())
        root.append(copy.deepcopy(root.find('counter')))
        self.assert_rejected(self.run_gate(ET.tostring(root, encoding='unicode')), 'duplicate counter LINE')

    def test_bad_counter_in_unrequired_class_cannot_hide_in_valid_global_totals(self):
        root = ET.fromstring(report())
        unrelated = ET.SubElement(root.find('package'), 'class', name='example/OtherUseCase')
        ET.SubElement(unrelated, 'counter', type='LINE', covered='-1', missed='1')
        self.assert_rejected(self.run_gate(ET.tostring(root, encoding='unicode')), 'counter')

    def test_duplicate_required_class_is_ambiguous_and_rejected(self):
        root = ET.fromstring(report())
        root.find('package').append(copy.deepcopy(root.find('package/class')))
        self.assert_rejected(self.run_gate(ET.tostring(root, encoding='unicode')), 'duplicate class')

    def test_invalid_policy_structure_metric_and_thresholds_fail_closed(self):
        cases = [[], {}, {'exclusions': []}, {'minimum_ratios': {'LINES': 0.8}},
                 {'minimum_ratios': {'LINE': -0.1}}, {'minimum_ratios': {'LINE': 1.1}},
                 {'minimum_ratios': {'LINE': True}}, {'minimum_ratios': {'LINE': '0.8'}},
                 {'minimum_ratios': {'LINE': float('nan')}},
                 {'minimum_ratios': {'LINE': float('inf')}},
                 {'minimum_ratios': {'LINE': 10 ** 500}},
                 {'minimum_covered': {'LINE': -1}}, {'minimum_covered': {'LINE': 1.5}},
                 {'minimum_covered': {'LINE': True}}, {'minimum_covered': []},
                 {'required_classes': []}, {'required_classes': {'example.Bad': {}}},
                 {'required_classes': {CLASS: {'excluded': True}}}]
        for rules in cases:
            with self.subTest(rules=rules):
                self.assert_rejected(self.run_gate(report(), raw_policy=json.dumps(rules)), 'policy')

    def test_unattainable_covered_floor_is_rejected(self):
        rules = policy()
        rules['minimum_covered']['LINE'] = 101
        self.assert_rejected(self.run_gate(report(), rules), 'global LINE covered')

    def test_malformed_and_duplicate_json_policy_keys_fail(self):
        for raw in ('{', '{"minimum_ratios":{"LINE":0.8,"LINE":0.1}}'):
            with self.subTest(raw=raw):
                self.assert_rejected(self.run_gate(report(), raw_policy=raw), 'policy')

    def test_cli_requires_policy_and_exposes_no_skip_or_exclusion_switches(self):
        result = subprocess.run([sys.executable, str(SCRIPT), 'missing.xml'],
                                capture_output=True, text=True, check=False)
        self.assertNotEqual(result.returncode, 0)
        for arguments in (['--skip'], ['--exclude', CLASS]):
            result = self.run_gate(report(), extra_args=arguments)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('unrecognized arguments', result.stderr)


if __name__ == '__main__':
    unittest.main()
