#!/usr/bin/env python3
"""Fail CI when required suites are absent, skipped, empty, or unsuccessful."""
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def check(directory, required, policy):
    found = set()
    executed = skipped = 0
    allowed_classes = set(policy.get('allowed_skipped_classes', []))
    allowed_tests = set(policy.get('allowed_skipped_tests', []))
    reports = list(Path(directory).glob('TEST-*.xml'))
    if not reports:
        raise ValueError('No test reports found')
    for path in reports:
        for case in ET.parse(path).getroot().iter('testcase'):
            classname = case.get('classname', '')
            name = case.get('name', '')
            if case.find('failure') is not None or case.find('error') is not None:
                raise ValueError('Failed test: ' + classname + '#' + name)
            if case.find('skipped') is not None:
                skipped += 1
                if classname not in allowed_classes and classname + '#' + name not in allowed_tests:
                    raise ValueError('Unexpected skipped test: ' + classname + '#' + name)
            else:
                executed += 1
                found.add(classname)
    if not executed or set(required) - found:
        raise ValueError('Required test suite did not execute: ' + ', '.join(sorted(set(required) - found)))
    print(json.dumps({'executed': executed, 'allowed_skipped': skipped, 'required_suites': len(required)}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--policy', type=Path)
    parser.add_argument('--required', action='append', default=[])
    args = parser.parse_args()
    try:
        policy = json.loads(args.policy.read_text()) if args.policy else {}
        check(args.directory, args.required + policy.get('required_classes', []), policy)
    except (ValueError, OSError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
