#!/usr/bin/env python3
"""Fail CI when required suites are absent, skipped, empty, or unsuccessful."""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def source_suites(directory):
    root = Path(directory)
    if not root.is_dir():
        raise ValueError('Test source directory does not exist: ' + str(root))
    suites = set()
    for path in root.rglob('*.java'):
        # Ignore examples in Java strings/comments when recognizing JUnit annotations.
        source = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*', '', path.read_text())
        if re.search(r'\babstract\s+class\s+' + re.escape(path.stem) + r'\b', source):
            continue
        if not re.search(r'@(?:org\.junit\.jupiter\.(?:api|params)\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b', source):
            continue
        package = re.search(r'\bpackage\s+([\w.]+)\s*;', source)
        suites.add((package.group(1) + '.' if package else '') + path.stem)
    if not suites:
        raise ValueError('No executable JUnit source suites found')
    return suites


def check(directory, required, policy, test_source=None):
    found = set()
    case_counts = {}
    executed = skipped = 0
    allowed_classes = set(policy.get('allowed_skipped_classes', []))
    allowed_tests = set(policy.get('allowed_skipped_tests', []))
    reports = list(Path(directory).glob('TEST-*.xml'))
    if not reports:
        raise ValueError('No test reports found')
    for path in reports:
        suite = ET.parse(path).getroot()
        cases = list(suite.iter('testcase'))
        if not cases:
            raise ValueError('Empty test report: ' + str(path))
        for counter in ('failures', 'errors', 'flakes'):
            if int(suite.get(counter, '0')) != 0:
                raise ValueError('Unsuccessful suite report: ' + str(path))
        if suite.get('tests') is not None:
            declared = int(suite.get('tests'))
            # Surefire can merge nested testcases while retaining the last nested count.
            # Fewer actual cases than declared still indicates an incomplete report.
            if declared < 0 or declared > len(cases):
                raise ValueError('Incomplete test report: ' + str(path))
        for case in cases:
            classname = case.get('classname', '')
            name = case.get('name', '')
            if case.find('failure') is not None or case.find('error') is not None:
                raise ValueError('Failed test: ' + classname + '#' + name)
            if any(case.find(tag) is not None for tag in ('flakyFailure', 'flakyError', 'rerunFailure', 'rerunError')):
                raise ValueError('Retried/flaky test is not a clean pass: ' + classname + '#' + name)
            if case.find('skipped') is not None:
                skipped += 1
                if classname not in allowed_classes and classname + '#' + name not in allowed_tests:
                    raise ValueError('Unexpected skipped test: ' + classname + '#' + name)
            else:
                executed += 1
                identity = classname + '#' + name
                case_counts[identity] = case_counts.get(identity, 0) + 1
                found.add(classname)
                # Surefire may use @DisplayName for nested testcase classnames.
                found.add(suite.get('name', ''))
    discovered = source_suites(test_source) if test_source else set()
    # Optional live-provider suites may be entirely skipped, but must still have reports.
    optional = {case.rsplit('#', 1)[0] for case in allowed_tests} | allowed_classes
    reported = {ET.parse(path).getroot().get('name', '') for path in reports}
    missing = (set(required) | (discovered - optional)) - found
    missing |= (discovered & optional) - reported
    if not executed or missing:
        raise ValueError('Required test suite did not execute: ' + ', '.join(sorted(missing)))
    expected = policy.get('required_tests', {})
    for identity, count in expected.items():
        if type(count) is not int or count < 1 or case_counts.get(identity, 0) != count:
            raise ValueError('Required testcase execution count mismatch: ' + identity)
    if policy.get('exact_cases') and set(case_counts) != set(expected):
        raise ValueError('Unexpected testcase in targeted acceptance report')
    summary = {'executed': executed, 'allowed_skipped': skipped, 'required_suites': len(required),
               'source_suites': len(discovered), 'case_counts': case_counts}
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--policy', type=Path)
    parser.add_argument('--required', action='append', default=[])
    parser.add_argument('--test-source', type=Path)
    args = parser.parse_args()
    try:
        policy = json.loads(args.policy.read_text()) if args.policy else {}
        summary = check(args.directory, args.required + policy.get('required_classes', []), policy, args.test_source)
        print(json.dumps({key: value for key, value in summary.items() if key != 'case_counts'}))
    except (ValueError, OSError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
