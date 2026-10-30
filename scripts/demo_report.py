#!/usr/bin/env python3
"""Render only bounded operation metadata, never raw server output."""
import argparse
import json
import re
from pathlib import Path


def render(body):
    lines = ['### Demo Data', '']
    for key in ('status', 'action', 'operation_id', 'backup_id'):
        value = body.get(key)
        if isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9_.-]{1,160}', value):
            lines.append(f'- {key}: `{value}`')
    for key, value in (body.get('counts') or {}).items():
        if re.fullmatch(r'[a-z_]{1,40}', key) and type(value) is int and 0 <= value <= 10**12:
            lines.append(f'- {key}: {value}')
    if type(body.get('canonical_records')) is int and 0 <= body['canonical_records'] <= 10**12:
        lines.append(f'- canonical_records: {body["canonical_records"]}')
    return '\n'.join(lines) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        with args.report.open() as source:
            raw = source.read(65537)
        if len(raw) > 65536:
            raise ValueError('Report too large')
        body = json.loads(raw)
        if not isinstance(body, dict) or not isinstance(body.get('counts', {}), dict):
            raise ValueError('Invalid report')
        result = render(body)
    except (OSError, ValueError, TypeError):
        result = '### Demo Data\n\nNo valid operation report received. Inspect the restricted server operation locally.\n'
    with args.output.open('a') as output:
        output.write(result)


if __name__ == '__main__':
    main()
