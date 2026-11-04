#!/usr/bin/env python3
"""Validate signed releases and preserve immutable, digest-addressed release state."""
import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

SERVICES = ('backend', 'frontend', 'quyet')
SHA = re.compile(r'[0-9a-f]{40}')


def validate(body, sha=None, repository=None, action=None):
    if not isinstance(body, dict) or body.get('version') != 1:
        raise ValueError('Unsupported release manifest')
    if not isinstance(body.get('sha'), str) or not SHA.fullmatch(body['sha']):
        raise ValueError('Invalid release SHA')
    repo = body.get('repository', '')
    if not isinstance(repo, str) or not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
        raise ValueError('Invalid release repository')
    if type(body.get('run_id')) is not int or body['run_id'] < 1:
        raise ValueError('Invalid release workflow run')
    if body.get('action') not in ('deploy', 'rollback'):
        raise ValueError('Invalid release action')
    for expected, actual, label in [(sha, body['sha'], 'SHA'), (repository, repo, 'repository'),
                                    (action, body['action'], 'action')]:
        if expected is not None and expected != actual:
            raise ValueError('Release ' + label + ' mismatch')
    images = body.get('images')
    if not isinstance(images, dict) or set(images) != set(SERVICES):
        raise ValueError('Release must contain all three application images')
    for name in SERVICES:
        prefix = f'ghcr.io/{repo.split("/")[0].lower()}/danasea-{name}@sha256:'
        if not isinstance(images[name], str) or not re.fullmatch(re.escape(prefix) + '[0-9a-f]{64}', images[name]):
            raise ValueError('Invalid or mutable release image: ' + name)
    return body


def read(path, **kwargs):
    return validate(json.loads(Path(path).read_text()), **kwargs)


def atomic_write(path, content):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.' + path.name, dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            os.fchmod(output.fileno(), 0o600)
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        os.replace(name, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        Path(name).unlink(missing_ok=True)


def git(root, *args):
    return subprocess.run(['git', '-C', str(root), *args], capture_output=True, text=True, check=True).stdout.strip()


def history(root, sha, previous):
    if not SHA.fullmatch(sha) or previous and not SHA.fullmatch(previous):
        raise ValueError('Invalid history SHA')
    git(root, 'merge-base', '--is-ancestor', sha, 'origin/main')
    if previous:
        git(root, 'merge-base', '--is-ancestor', previous, sha)


def compatible(root, sha, previous):
    if not previous or not SHA.fullmatch(sha) or not SHA.fullmatch(previous):
        raise ValueError('No verified migration baseline')
    if git(root, 'diff', '--name-only', previous, sha, '--', 'backend/src/main/resources/db/migration'):
        raise ValueError('Migrations changed; automatic application rollback is blocked. Recover forward.')


def verify(envelope, signers, sha, repository, action):
    if len(envelope) > 65536:
        raise ValueError('Release envelope too large')
    payload = json.loads(envelope)
    data = base64.b64decode(payload['manifest'], validate=True)
    signature = base64.b64decode(payload['signature'], validate=True)
    with tempfile.TemporaryDirectory(prefix='danasea-signature-') as directory:
        path = Path(directory) / 'release.sig'
        path.write_bytes(signature)
        result = subprocess.run(['ssh-keygen', '-Y', 'verify', '-f', str(signers), '-I', 'github-actions',
                                 '-n', 'danasea-release', '-s', str(path)], input=data, capture_output=True, check=False)
        if result.returncode:
            raise ValueError('Release signature verification failed')
    body = validate(json.loads(data), sha, repository, action)
    validate_rehearsal(body, body.get('rehearsal', {}), body.get('baseline_sha'))
    return body


def validate_rehearsal(body, evidence, baseline):
    if not isinstance(baseline, str) or baseline and not SHA.fullmatch(baseline):
        raise ValueError('Invalid rehearsal baseline')
    required = ('passed', 'restore_verified', 'existing_data_preserved', 'booking_order_verified', 'worker_inference_verified')
    if any(evidence.get(key) is not True for key in required) or evidence.get('images') != body['images'] or evidence.get('baseline_sha') != baseline:
        raise ValueError('Missing or mismatched release rehearsal')


def publish(root, body):
    validate(body)
    root = Path(root)
    saved = root / '.releases' / (body['sha'] + '.json')
    if saved.exists():
        old = read(saved)
        if old['images'] != body['images'] or old['repository'] != body['repository']:
            raise ValueError('A release SHA cannot be rebound to different images')
    else:
        atomic_write(saved, json.dumps(body, sort_keys=True) + '\n')
    atomic_write(root / '.deploy-state', body['sha'] + '\n')


def adopt_running(root, repository, run_id):
    sha = (root / '.deploy-state').read_text().strip()
    if not SHA.fullmatch(sha):
        raise ValueError('Invalid legacy release state')
    body = {'version': 1, 'sha': sha, 'repository': repository, 'run_id': run_id,
            'action': 'deploy', 'legacy_adopted': True, 'images': {}}
    for name in SERVICES:
        result = subprocess.run(['docker', 'inspect', 'danasea-' + name], capture_output=True, check=True, text=True)
        container = json.loads(result.stdout)[0]
        prefix = f'ghcr.io/{repository.split("/")[0].lower()}/danasea-{name}'
        if container['Config']['Image'] != prefix + ':' + sha or container['State'].get('Health', {}).get('Status') != 'healthy':
            raise ValueError('Legacy container is not the expected healthy SHA release')
        result = subprocess.run(['docker', 'image', 'inspect', container['Image']], capture_output=True, check=True, text=True)
        candidates = [ref for ref in json.loads(result.stdout)[0].get('RepoDigests', []) if ref.startswith(prefix + '@sha256:')]
        if len(candidates) != 1:
            raise ValueError('Cannot establish the running image digest')
        body['images'][name] = candidates[0]
    publish(root, validate(body))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    for name in ('validate', 'verify'):
        p = sub.add_parser(name)
        if name == 'validate':
            p.add_argument('manifest', type=Path)
        else:
            p.add_argument('--signers', required=True, type=Path)
            p.add_argument('--output', required=True, type=Path)
        p.add_argument('--sha', required=True)
        p.add_argument('--repository', required=True)
        p.add_argument('--action', choices=('deploy', 'rollback'), required=True)
    for name in ('history', 'compatible'):
        p = sub.add_parser(name)
        p.add_argument('--root', required=True, type=Path)
        p.add_argument('--sha', required=True)
        p.add_argument('--previous', default='')
    p = sub.add_parser('publish')
    p.add_argument('manifest', type=Path)
    p.add_argument('--root', required=True, type=Path)
    p = sub.add_parser('images')
    p.add_argument('manifest', type=Path)
    p = sub.add_parser('envelope')
    p.add_argument('manifest', type=Path)
    p.add_argument('--signature', required=True, type=Path)
    p = sub.add_parser('create')
    p.add_argument('--sha', required=True)
    p.add_argument('--repository', required=True)
    p.add_argument('--run-id', required=True, type=int)
    p.add_argument('--directory', required=True, type=Path)
    p.add_argument('--output', required=True, type=Path)
    p = sub.add_parser('promote')
    p.add_argument('manifest', type=Path)
    p.add_argument('--rehearsal', required=True, type=Path)
    p.add_argument('--baseline-sha', required=True)
    p.add_argument('--action', choices=('deploy', 'rollback'), required=True)
    p.add_argument('--output', required=True, type=Path)
    p = sub.add_parser('adopt-running')
    p.add_argument('--root', required=True, type=Path)
    p.add_argument('--repository', required=True)
    p.add_argument('--run-id', required=True, type=int)
    args = parser.parse_args()
    try:
        if args.command == 'validate':
            print(json.dumps(read(args.manifest, sha=args.sha, repository=args.repository, action=args.action)))
        elif args.command == 'verify':
            body = verify(sys.stdin.read(65537), args.signers, args.sha, args.repository, args.action)
            atomic_write(args.output, json.dumps(body, sort_keys=True) + '\n')
        elif args.command == 'history':
            history(args.root, args.sha, args.previous)
        elif args.command == 'compatible':
            compatible(args.root, args.sha, args.previous)
        elif args.command == 'publish':
            publish(args.root, read(args.manifest))
        elif args.command == 'images':
            for name, ref in read(args.manifest)['images'].items():
                print(name.upper() + '_IMAGE=' + ref)
        elif args.command == 'envelope':
            read(args.manifest)
            print(json.dumps({'manifest': base64.b64encode(args.manifest.read_bytes()).decode(),
                              'signature': base64.b64encode(args.signature.read_bytes()).decode()}))
        elif args.command == 'create':
            body = {'version': 1, 'sha': args.sha, 'repository': args.repository, 'run_id': args.run_id,
                    'action': 'deploy', 'images': {}}
            for name in SERVICES:
                image = json.loads((args.directory / (name + '.json')).read_text())
                if image['sha'] != args.sha or image['run_id'] != args.run_id:
                    raise ValueError('Candidate image belongs to another release/run')
                body['images'][name] = image['image']
            atomic_write(args.output, json.dumps(validate(body), sort_keys=True) + '\n')
        elif args.command == 'promote':
            body = read(args.manifest)
            evidence = json.loads(args.rehearsal.read_text())
            validate_rehearsal(body, evidence, args.baseline_sha)
            body.update({'action': args.action, 'baseline_sha': args.baseline_sha, 'rehearsal': evidence})
            atomic_write(args.output, json.dumps(body, sort_keys=True) + '\n')
        elif args.command == 'adopt-running':
            adopt_running(args.root, args.repository, args.run_id)
    except (ValueError, OSError, KeyError, TypeError, subprocess.CalledProcessError):
        print('Release rejected: invalid evidence, signature, history or immutable release state.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
