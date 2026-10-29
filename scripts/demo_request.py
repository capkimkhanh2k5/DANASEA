#!/usr/bin/env python3
"""Create and verify short-lived demo-data requests with isolated SSH signatures."""
import argparse
import base64
import binascii
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import time
from pathlib import Path

MAX_ENVELOPE = 65536
MAX_REQUEST = 8192
NAMESPACE = 'danasea-demo-data'
IDENTITY = 'github-actions'
FIELDS = {'version', 'repository', 'sha', 'package_sha256', 'action', 'run_id', 'run_attempt',
          'issued_at', 'nonce', 'confirmation'}


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON field')
        result[key] = value
    return result


def _json(data):
    try:
        return json.loads(data, object_pairs_hook=_object)
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise ValueError('Invalid request JSON') from error


def _matches(value, pattern):
    return isinstance(value, str) and re.fullmatch(pattern, value) is not None


def validate(body, sha=None, repository=None, now=None):
    if not isinstance(body, dict) or not FIELDS <= set(body) or set(body) - FIELDS - {'backup_id'}:
        raise ValueError('Invalid demo request fields')
    if type(body['version']) is not int or body['version'] != 1:
        raise ValueError('Unsupported demo request version')
    if not _matches(body['sha'], r'[0-9a-f]{40}'):
        raise ValueError('Invalid demo request SHA')
    if not _matches(body['repository'], r'[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_-][A-Za-z0-9_.-]*'):
        raise ValueError('Invalid demo request repository')
    if not _matches(body['package_sha256'], r'[0-9a-f]{64}'):
        raise ValueError('Invalid demo package checksum')
    for name in ('run_id', 'run_attempt'):
        if type(body[name]) is not int or not 1 <= body[name] <= 2**63 - 1:
            raise ValueError('Invalid demo workflow identifier')
    now = time.time() if now is None else now
    issued = body['issued_at']
    if type(issued) is not int or issued > now + 30 or now - issued > 600:
        raise ValueError('Expired or future demo request')
    if not _matches(body['nonce'], r'[0-9a-f]{32}'):
        raise ValueError('Invalid demo request nonce')
    confirmations = {'inspect': '', 'replace': 'REPLACE_DEMO_DATA', 'restore': 'RESTORE_DEMO_DATA'}
    action = body['action']
    if not isinstance(action, str) or action not in confirmations or body['confirmation'] != confirmations[action]:
        raise ValueError('Invalid demo action confirmation')
    if action == 'restore':
        if not _matches(body.get('backup_id'), r'[A-Za-z0-9][A-Za-z0-9_.-]{0,191}') or '..' in body['backup_id']:
            raise ValueError('Invalid demo backup identifier')
    elif 'backup_id' in body:
        raise ValueError('Backup identifier is allowed only for restore')
    if sha is not None and body['sha'] != sha:
        raise ValueError('Demo request SHA mismatch')
    if repository is not None and body['repository'] != repository:
        raise ValueError('Demo request repository mismatch')
    return body


def _password(value):
    if value is not None and (not isinstance(value, str) or not 1 <= len(value.encode()) <= 72
                              or any(ord(char) < 32 or ord(char) == 127 for char in value)):
        raise ValueError('Invalid private demo password')
    return value


def verify(envelope, signers, sha, repository):
    if not isinstance(envelope, str) or len(envelope.encode()) > MAX_ENVELOPE:
        raise ValueError('Demo envelope too large')
    payload = _json(envelope)
    if not isinstance(payload, dict) or not {'request', 'signature'} <= set(payload) or set(payload) - {'request', 'signature', 'password'}:
        raise ValueError('Invalid demo envelope')
    try:
        data = base64.b64decode(payload['request'], validate=True)
        signature = base64.b64decode(payload['signature'], validate=True)
    except (ValueError, TypeError, binascii.Error) as error:
        raise ValueError('Invalid demo envelope encoding') from error
    if not 1 <= len(data) <= MAX_REQUEST or not 1 <= len(signature) <= 16384:
        raise ValueError('Demo request or signature size invalid')
    with tempfile.TemporaryDirectory(prefix='danasea-demo-signature-') as directory:
        path = Path(directory) / 'request.sig'
        path.write_bytes(signature)
        result = subprocess.run(['ssh-keygen', '-Y', 'verify', '-f', str(signers), '-I', IDENTITY,
                                 '-n', NAMESPACE, '-s', str(path)], input=data, capture_output=True, check=False)
        if result.returncode:
            raise ValueError('Demo signature verification failed')
    body = validate(_json(data), sha, repository)
    password = _password(payload.get('password'))
    if body['action'] == 'replace' and (password is None or len(password) < 12):
        raise ValueError('Replacement requires a private demo password')
    return body, password


def _private_write(path, content):
    fd, temporary = tempfile.mkstemp(prefix='.' + path.name, dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    create = sub.add_parser('create')
    create.add_argument('--action', required=True, choices=('inspect', 'replace', 'restore'))
    for name in ('sha', 'repository', 'package-sha256'):
        create.add_argument('--' + name, required=True)
    for name in ('run-id', 'run-attempt'):
        create.add_argument('--' + name, required=True, type=int)
    create.add_argument('--confirmation', default='')
    create.add_argument('--backup-id')
    create.add_argument('--output', required=True, type=Path)
    envelope = sub.add_parser('envelope')
    envelope.add_argument('--request', required=True, type=Path)
    envelope.add_argument('--signature', required=True, type=Path)
    envelope.add_argument('--password-env')
    args = parser.parse_args()
    try:
        if args.command == 'create':
            body = {'version': 1, 'repository': args.repository, 'sha': args.sha,
                    'package_sha256': args.package_sha256, 'action': args.action,
                    'run_id': args.run_id, 'run_attempt': args.run_attempt, 'issued_at': int(time.time()),
                    'nonce': secrets.token_hex(16), 'confirmation': args.confirmation}
            if args.backup_id:
                body['backup_id'] = args.backup_id
            _private_write(args.output, json.dumps(validate(body), sort_keys=True) + '\n')
        else:
            with args.request.open('rb') as source:
                data = source.read(MAX_REQUEST + 1)
            with args.signature.open('rb') as source:
                signature = source.read(16385)
            if len(data) > MAX_REQUEST or not 1 <= len(signature) <= 16384:
                raise ValueError('Demo request or signature size invalid')
            body = validate(_json(data))
            payload = {'request': base64.b64encode(data).decode(), 'signature': base64.b64encode(signature).decode()}
            if args.password_env:
                payload['password'] = _password(os.environ[args.password_env])
            if body['action'] == 'replace' and len(payload.get('password') or '') < 12:
                raise ValueError('Replacement requires a private demo password')
            print(json.dumps(payload))
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError):
        print('Demo request rejected: invalid request, signature or private input.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
