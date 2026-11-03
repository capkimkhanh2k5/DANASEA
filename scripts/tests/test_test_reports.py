import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'check_test_reports.py'


class TestReportGateTest(unittest.TestCase):
    def check(self, xml, policy=None, sources=None, other_reports=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'TEST-example.xml').write_text(xml)
            for name, content in (other_reports or {}).items():
                (root / name).write_text(content)
            command = [sys.executable, str(SCRIPT), str(root), '--required', 'CriticalTest']
            if policy is not None:
                path = root / 'policy.json'
                path.write_text(json.dumps(policy))
                command += ['--policy', str(path)]
            if sources is not None:
                source_root = root / 'sources'
                source_root.mkdir()
                for name, content in sources.items():
                    path = source_root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(content)
                command += ['--test-source', str(source_root)]
            return subprocess.run(command, capture_output=True, text=True, check=False)

    def test_passing_critical_test_is_accepted(self):
        result = self.check('<testsuite><testcase classname="CriticalTest" name="works"/></testsuite>')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_nested_display_names_still_identify_the_required_report_suite(self):
        result = self.check('<testsuite name="CriticalTest"><testcase classname="Repository scenarios" name="works"/></testsuite>')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_named_required_suite_must_execute_a_successful_test(self):
        for cases in ['', '<testcase classname="Repository scenarios" name="works"><skipped/></testcase>',
                      '<testcase classname="Repository scenarios" name="works"><error/></testcase>']:
            with self.subTest(cases=cases):
                result = self.check('<testsuite name="CriticalTest">' + cases + '</testsuite>')
                self.assertEqual(result.returncode, 1)

    def test_missing_skipped_or_failed_critical_test_is_rejected(self):
        for xml in ['<testsuite/>', '<testsuite><testcase classname="OtherTest" name="works"/></testsuite>',
                    '<testsuite><testcase classname="CriticalTest" name="works"><skipped/></testcase></testsuite>',
                    '<testsuite><testcase classname="CriticalTest" name="works"><failure/></testcase></testsuite>']:
            with self.subTest(xml=xml):
                self.assertEqual(self.check(xml).returncode, 1)

    def test_suite_failure_counters_cannot_be_hidden_by_a_passing_case(self):
        for counter in ('failures', 'errors', 'flakes'):
            with self.subTest(counter=counter):
                result = self.check(f'<testsuite {counter}="1"><testcase classname="CriticalTest" name="works"/></testsuite>')
                self.assertEqual(result.returncode, 1)

    def test_retrying_a_failed_test_does_not_turn_it_into_a_clean_pass(self):
        for element in ('flakyFailure', 'flakyError', 'rerunFailure', 'rerunError'):
            with self.subTest(element=element):
                result = self.check(f'<testsuite><testcase classname="CriticalTest" name="works"><{element}/></testcase></testsuite>')
                self.assertEqual(result.returncode, 1)

    def test_incomplete_report_counts_are_rejected(self):
        for count in ('2', '-1', 'invalid'):
            with self.subTest(count=count):
                result = self.check(f'<testsuite tests="{count}"><testcase classname="CriticalTest" name="works"/></testsuite>')
                self.assertEqual(result.returncode, 1)

    def test_merged_nested_cases_are_authoritative_when_surefire_retains_a_nested_count(self):
        result = self.check('<testsuite name="CriticalTest" tests="1"><testcase classname="First scenario" name="first"/><testcase classname="Second scenario" name="second"/></testsuite>')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)['executed'], 2)

    def test_an_unexpected_skip_still_fails_beside_a_passing_required_test(self):
        result = self.check('<testsuite><testcase classname="CriticalTest" name="works"/><testcase classname="OtherTest" name="missing"><skipped/></testcase></testsuite>')
        self.assertEqual(result.returncode, 1)

    def test_only_the_exact_optional_provider_case_can_skip(self):
        policy = {'allowed_skipped_tests': ['ProviderTest#live']}
        for name, expected in [('live', 0), ('newBusinessCase', 1)]:
            with self.subTest(name=name):
                result = self.check(f'<testsuite><testcase classname="CriticalTest" name="works"/><testcase classname="ProviderTest" name="{name}"><skipped/></testcase></testsuite>', policy)
                self.assertEqual(result.returncode, expected, result.stderr)

    def test_an_allowed_skipped_case_cannot_satisfy_a_required_suite(self):
        result = self.check('<testsuite name="CriticalTest"><testcase classname="ProviderTest" name="live"><skipped/></testcase></testsuite>',
                            {'allowed_skipped_tests': ['ProviderTest#live']})
        self.assertEqual(result.returncode, 1)
        self.assertIn('Required test suite did not execute', result.stderr)

    def test_missing_noncritical_source_suite_is_rejected(self):
        result = self.check('<testsuite name="CriticalTest"><testcase classname="CriticalTest" name="works"/></testsuite>', sources={
            'CriticalTest.java': 'class CriticalTest { @Test void works() {} }',
            'OtherTest.java': 'class OtherTest { @Test void missing() {} }',
        })
        self.assertEqual(result.returncode, 1)
        self.assertIn('OtherTest', result.stderr)

    def test_source_suite_discovery_handles_nested_tests_without_counting_helpers(self):
        result = self.check('<testsuite name="example.CriticalTest"><testcase classname="CriticalTest" name="works"/></testsuite>', sources={
            'example/CriticalTest.java': 'package example; class CriticalTest { @Nested class Scenario { @ParameterizedTest void works() {} } }',
            'example/BaseTest.java': 'package example; abstract class BaseTest { @Test void inherited() {} }',
            'example/Fixture.java': 'package example; class Fixture { String example = "@Test"; /* @Test */ }',
        })
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_entire_optional_source_suite_requires_its_own_report_and_exact_skip_allowance(self):
        for method, expected in [('live', 0), ('newBusinessCase', 1)]:
            with self.subTest(method=method):
                result = self.check('<testsuite name="CriticalTest"><testcase classname="CriticalTest" name="works"/></testsuite>',
                                    policy={'allowed_skipped_tests': ['ProviderTest#live']},
                                    sources={'CriticalTest.java': 'class CriticalTest { @Test void works() {} }',
                                             'ProviderTest.java': 'class ProviderTest { @Test void live() {} }'},
                                    other_reports={'TEST-provider.xml': f'<testsuite name="ProviderTest"><testcase classname="ProviderTest" name="{method}"><skipped/></testcase></testsuite>'})
                self.assertEqual(result.returncode, expected, result.stderr)
                if expected == 0:
                    self.assertEqual(json.loads(result.stdout)['allowed_skipped'], 1)
                    self.assertEqual(json.loads(result.stdout)['source_suites'], 2)

    def test_missing_optional_source_report_cannot_be_hidden_by_another_passing_suite(self):
        result = self.check('<testsuite name="CriticalTest"><testcase classname="CriticalTest" name="works"/></testsuite>',
                            policy={'allowed_skipped_tests': ['ProviderTest#live']},
                            sources={'CriticalTest.java': 'class CriticalTest { @Test void works() {} }',
                                     'ProviderTest.java': 'class ProviderTest { @Test void live() {} }'})
        self.assertEqual(result.returncode, 1)
        self.assertIn('ProviderTest', result.stderr)


    def test_empty_optional_provider_report_cannot_hide_missing_execution(self):
        result = self.check('<testsuite name="CriticalTest"><testcase classname="CriticalTest" name="works"/></testsuite>',
                            policy={'allowed_skipped_tests': ['ProviderTest#live']},
                            sources={'CriticalTest.java': 'class CriticalTest { @Test void works() {} }',
                                     'ProviderTest.java': 'class ProviderTest { @Test void live() {} }'},
                            other_reports={'TEST-provider.xml': '<testsuite name="ProviderTest" tests="0"/>'})
        self.assertEqual(result.returncode, 1)
        self.assertIn('Empty test report', result.stderr)



class AcceptanceReportContractTest(unittest.TestCase):
    def test_check_returns_summary_and_requires_exact_cases(self):
        from scripts.check_test_reports import check
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'TEST-CriticalTest.xml'
            path.write_text('<testsuite name="CriticalTest"><testcase classname="CriticalTest" name="ordinary"/></testsuite>')
            policy = {'required_tests': {'CriticalTest#live': 1}, 'exact_cases': True}
            with self.assertRaisesRegex(ValueError, 'Required testcase'):
                check(Path(d), {'CriticalTest'}, policy)
            path.write_text('<testsuite name="CriticalTest"><testcase classname="CriticalTest" name="live"/></testsuite>')
            result = check(Path(d), {'CriticalTest'}, policy)
            self.assertEqual(result['executed'], 1)
            self.assertEqual(result['case_counts'], {'CriticalTest#live': 1})
            path.write_text('<testsuite name="CriticalTest"><testcase classname="CriticalTest" name="live"/><testcase classname="CriticalTest" name="extra"/></testsuite>')
            with self.assertRaisesRegex(ValueError, 'Unexpected testcase'):
                check(Path(d), {'CriticalTest'}, policy)


if __name__ == '__main__':
    unittest.main()
