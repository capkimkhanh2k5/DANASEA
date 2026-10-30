#!/usr/bin/env python3
"""Check the live GitHub release boundary; API/plan failures block deployment."""
import argparse
import json
import re
import subprocess
import sys


def validate_controls(branch, environment):
    checks = branch.get('protection', {}).get('required_status_checks', {})
    names = set(checks.get('contexts', [])) | {item['context'] for item in checks.get('checks', [])}
    if branch.get('protected') is not True or 'Required CI gate' not in names:
        raise ValueError('main must be protected and require the Required CI gate check')
    if not (environment.get('deployment_branch_policy') or {}).get('protected_branches'):
        raise ValueError('production must restrict deployments to protected branches')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', required=True)
    args = parser.parse_args()
    try:
        if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repository):
            raise ValueError('Invalid repository')
        def fetch(path):
            result = subprocess.run(['gh', 'api', 'repos/' + args.repository + '/' + path], check=True, capture_output=True, text=True)
            return json.loads(result.stdout)
        validate_controls(fetch('branches/main'), fetch('environments/production'))
        print('GitHub branch/status/environment release boundaries verified')
    except (ValueError, OSError, KeyError, subprocess.SubprocessError):
        print('Repository release policy failed or is unavailable. Configure branch/environment protection and token read permissions.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
