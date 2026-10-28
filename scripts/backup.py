#!/usr/bin/env python3
"""Back up PostgreSQL locally and prove restore before a release changes the schema."""
import argparse
import datetime
import hashlib
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
from pathlib import Path


def run(args, **kwargs):
    return subprocess.run(args, check=True, stderr=subprocess.PIPE,
                          stdout=kwargs.pop('stdout', subprocess.PIPE), **kwargs).stdout


def restore(archive, container, database):
    if not re.fullmatch(r'danasea_restore_[a-z0-9_]{1,48}', database):
        raise ValueError('Restore destination must be a disposable danasea_restore_* database')
    with Path(archive).open('rb') as source:
        run(['docker', 'exec', '-i', container, 'sh', '-ec',
             'exec pg_restore -U "$POSTGRES_USER" --no-owner --no-privileges --exit-on-error -d "$1"',
             'sh', database], stdin=source)


def verify_restore(archive, container):
    database = 'danasea_restore_' + secrets.token_hex(8)
    run(['docker', 'exec', container, 'sh', '-ec', 'createdb -U "$POSTGRES_USER" "$1"', 'sh', database])
    try:
        restore(archive, container, database)
        result = run(['docker', 'exec', container, 'sh', '-ec',
                      '''psql -U "$POSTGRES_USER" -d "$1" -At -v ON_ERROR_STOP=1 -c "SELECT to_regclass('public.payments') IS NOT NULL AND to_regclass('public.flyway_schema_history') IS NOT NULL"''',
                      'sh', database])
        if result.strip() != b't':
            raise ValueError('Restored backup lacks application/migration tables')
    finally:
        run(['docker', 'exec', container, 'sh', '-ec', 'dropdb --force -U "$POSTGRES_USER" "$1"', 'sh', database])


def backup(config):
    container = config.get('container', 'danasea-postgres')
    directory = Path(config['directory']).expanduser().resolve()
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if directory.stat().st_mode & 0o077:
        raise ValueError('Backup directory must have mode 700')
    keep = config.get('keep', 7)
    if type(keep) is not int or keep < 1:
        raise ValueError('Keep at least one verified local backup')
    # The owner's maximum also applies to an older host policy with a larger value.
    keep = min(keep, 7)
    with tempfile.TemporaryDirectory(prefix='.backup-', dir=directory) as temporary:
        archive = Path(temporary) / 'database.dump'
        with archive.open('wb') as output:
            os.chmod(archive, 0o600)
            run(['docker', 'exec', container, 'sh', '-ec',
                 'exec pg_dump -Fc --no-owner --no-privileges -U "$POSTGRES_USER" "$POSTGRES_DB"'], stdout=output)
            output.flush()
            os.fsync(output.fileno())
        if archive.stat().st_size < 64:
            raise ValueError('Empty backup')
        verify_restore(archive, container)
        with archive.open('rb') as source:
            digest = hashlib.file_digest(source, 'sha256').hexdigest()
        name = 'verified-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + secrets.token_hex(4) + '.dump'
        final = directory / name
        os.replace(archive, final)
        os.chmod(final, 0o600)
    metadata = {'archive': final.name, 'restore_verified': True, 'sha256': digest,
                'bytes': final.stat().st_size, 'scope': 'local server backup, as requested'}
    with (directory / (name + '.json')).open('w') as output:
        os.fchmod(output.fileno(), 0o600)
        output.write(json.dumps(metadata) + '\n')
        output.flush()
        os.fsync(output.fileno())
    descriptor = os.open(directory, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    previous = sorted((path for path in directory.glob('verified-*.dump') if path != final),
                      key=lambda path: (path.stat().st_mtime_ns, path.name), reverse=True)
    for old in previous[keep - 1:]:
        old.unlink()
        Path(str(old) + '.json').unlink(missing_ok=True)
    print(json.dumps({'backup': final.name, 'restore_verified': True, 'storage': 'local'}))


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True, type=Path)
    args = parser.parse_args()
    try:
        backup(json.loads(args.config.read_text()))
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError):
        print('Backup failed: no restore-verified local recovery point was established. Inspect configuration locally.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
