#!/usr/bin/env python3
"""Guard grouped Dependabot configuration; GitHub remains the full schema authority."""
import argparse
import sys
from pathlib import Path

import yaml

GROUP_ONLY = {'open-pull-requests-limit', 'milestone', 'commit-message', 'target-branch', 'pull-request-branch-name'}
INTERVALS = {'daily', 'weekly', 'monthly', 'quarterly', 'semiannually', 'yearly', 'cron'}


class UniqueKeysLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        keys = [self.construct_object(key, deep=deep) for key, _ in node.value]
        if any(keys.count(key) > 1 for key in keys):
            raise ValueError('Duplicate Dependabot YAML key')
        return super().construct_mapping(node, deep=deep)


def load_config(text):
    return yaml.load(text, Loader=UniqueKeysLoader)


def validate(config):
    if not isinstance(config, dict) or config.get('version') != 2:
        raise ValueError('Dependabot version must be 2')
    groups = config.get('multi-ecosystem-groups', {})
    updates = config.get('updates')
    if not isinstance(groups, dict) or not isinstance(updates, list) or not updates:
        raise ValueError('Dependabot updates/groups must be nonempty structured configuration')
    for name, group in groups.items():
        if not isinstance(group, dict) or not isinstance(group.get('schedule'), dict):
            raise ValueError('Group schedule is required: ' + str(name))
        if group['schedule'].get('interval') not in INTERVALS:
            raise ValueError('Invalid group schedule interval')
        limit = group.get('open-pull-requests-limit', 5)
        if type(limit) is not int or limit < 0:
            raise ValueError('Group open-pull-requests-limit must be a nonnegative integer')
    for update in updates:
        if not isinstance(update, dict) or not isinstance(update.get('package-ecosystem'), str):
            raise ValueError('Invalid ecosystem entry')
        group = update.get('multi-ecosystem-group')
        if group is None:
            continue
        if not isinstance(group, str) or group not in groups:
            raise ValueError('Unknown multi-ecosystem group')
        misplaced = GROUP_ONLY.intersection(update)
        if misplaced:
            raise ValueError(', '.join(sorted(misplaced)) + ' must be configured on the multi-ecosystem group')
        patterns = update.get('patterns')
        if not isinstance(patterns, list) or not patterns or any(not isinstance(p, str) or not p for p in patterns):
            raise ValueError('Grouped updates require nonempty patterns')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('config', nargs='?', type=Path, default=Path('.github/dependabot.yml'))
    args = parser.parse_args()
    try:
        validate(load_config(args.config.read_text()))
    except (ValueError, OSError, yaml.YAMLError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print('Dependabot grouped configuration guard passed; full GitHub validation is separate.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
