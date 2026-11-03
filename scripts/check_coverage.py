#!/usr/bin/env python3
"""Validate JaCoCo XML against strict global and required-class coverage floors."""

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

METRICS = {'INSTRUCTION', 'BRANCH', 'LINE', 'COMPLEXITY', 'METHOD', 'CLASS'}
FLOORS = {'minimum_ratios', 'minimum_covered'}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate key ' + key)
        result[key] = value
    return result


def validate_floors(value, scope, allow_classes=False):
    if not isinstance(value, dict):
        raise TypeError(scope + ' must be an object')
    unknown = set(value) - (FLOORS | ({'required_classes'} if allow_classes else set()))
    if unknown:
        raise ValueError(scope + ' unknown keys: ' + ', '.join(sorted(unknown)))
    for field in FLOORS:
        thresholds = value.get(field, {})
        if not isinstance(thresholds, dict):
            raise TypeError(scope + ' ' + field + ' must be an object')
        for metric, minimum in thresholds.items():
            if metric not in METRICS:
                raise ValueError(scope + ' unknown metric ' + metric)
            if field == 'minimum_ratios':
                valid = (type(minimum) in (int, float) and 0 <= minimum <= 1
                         and math.isfinite(minimum))
            else:
                valid = type(minimum) is int and minimum >= 0
            if not valid:
                raise ValueError(scope + ' invalid ' + field + ' threshold for ' + metric)


def read_policy(path):
    try:
        policy = json.loads(path.read_text(), object_pairs_hook=unique_object)
        validate_floors(policy, 'policy', allow_classes=True)
        required = policy.get('required_classes', {})
        if not isinstance(required, dict):
            raise TypeError('required_classes must be an object')
        for classname, floors in required.items():
            if not re.fullmatch(r'[\w$]+(?:/[\w$]+)*', classname):
                raise ValueError('invalid slash class name ' + classname)
            validate_floors(floors, 'required class ' + classname)
        if not any(policy.get(field) for field in FLOORS) and not required:
            raise ValueError('at least one coverage floor or required class is required')
        return policy
    except (OSError, TypeError, ValueError) as error:
        raise ValueError('Invalid policy: ' + str(error)) from error


def counters(element, scope):
    observed = {}
    for counter in element.findall('counter'):
        metric = counter.get('type')
        if metric not in METRICS:
            raise ValueError(scope + ' unknown counter metric ' + str(metric))
        if metric in observed:
            raise ValueError(scope + ' duplicate counter ' + metric)
        counts = {}
        for field in ('covered', 'missed'):
            raw = counter.get(field, '')
            if not re.fullmatch(r'[0-9]+', raw):
                raise ValueError(scope + ' malformed counter ' + metric + ' ' + field + ': ' + repr(raw))
            counts[field] = int(raw)
        total = counts['covered'] + counts['missed']
        observed[metric] = {**counts, 'total': total,
                            'ratio': counts['covered'] / total if total else 1.0}
    return observed


def read_report(path):
    try:
        root = ET.parse(path).getroot()
        if root.tag != 'report':
            raise ValueError('root must be report')
        classes = {}
        # Validate all counters, including nonrequired classes and nested methods.
        for element in root.iter():
            if element.tag == 'counter':
                continue
            scope = element.get('name', element.tag)
            observed = counters(element, scope)
            if element.tag == 'class':
                name = element.get('name')
                if not name:
                    raise ValueError('class name missing')
                if name in classes:
                    raise ValueError('duplicate class ' + name)
                classes[name] = observed
        return counters(root, 'global'), classes
    except (OSError, ValueError, ET.ParseError) as error:
        raise ValueError('Invalid report: ' + str(error)) from error


def check_floors(observed, floors, scope, errors, required_class=False):
    requested = set(floors.get('minimum_ratios', {})) | set(floors.get('minimum_covered', {})) | {'LINE'}
    if required_class and 'BRANCH' in requested and 'BRANCH' not in observed:
        observed['BRANCH'] = {'covered': 0, 'missed': 0, 'total': 0, 'ratio': 1.0}
    for metric in sorted(requested):
        if metric not in observed:
            errors.append(scope + ' missing counter ' + metric)
    line = observed.get('LINE')
    if line is not None:
        if not line['total']:
            errors.append(scope + ' LINE has no executable items')
        if required_class and not line['covered']:
            errors.append(scope + ' LINE covered must be positive')
    for field, observation in (('minimum_ratios', 'ratio'), ('minimum_covered', 'covered')):
        for metric, minimum in floors.get(field, {}).items():
            counter = observed.get(metric)
            if counter is not None and counter[observation] < minimum:
                errors.append(f'{scope} {metric} {observation} {counter[observation]} below minimum {minimum}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--policy', type=Path, required=True)
    args = parser.parse_args()
    summary = {'passed': False, 'global': {}, 'required_classes': {}, 'errors': []}
    try:
        policy = read_policy(args.policy)
        observed, classes = read_report(args.report)
        summary['global'] = observed
        check_floors(observed, policy, 'global', summary['errors'])
        for classname, floors in policy.get('required_classes', {}).items():
            if classname not in classes:
                summary['errors'].append(classname + ' missing from report')
                continue
            class_counters = classes[classname]
            summary['required_classes'][classname] = class_counters
            check_floors(class_counters, floors, classname, summary['errors'], required_class=True)
        summary['passed'] = not summary['errors']
    except ValueError as error:
        summary['errors'].append(str(error))
    print(json.dumps(summary, sort_keys=True, allow_nan=False))
    return 0 if summary['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
