"""Exercise the real deploy shell and signature verifier; isolate host/Docker side effects."""
import base64
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def simulate(infrastructure_changed=False, previous_worker=True, deploy_fails=True,
             acceptance_fails=False, backup_fails=False, preflight_fails=False, migrations_changed=False, app_dir_in_environment=True, availability_fails=False):
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        binary = root / 'bin'
        binary.mkdir()
        (root / 'frontend').mkdir()
        (root / '.releases').mkdir()
        (root / '.deploy-state').write_text('b' * 40)
        (root / '.env').write_text('fixture')
        (root / '.env').chmod(0o600)
        log = root / 'calls.jsonl'
        key = root / 'key'
        subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(key)], check=True)
        signers = root / 'signers'
        signers.write_text('github-actions ' + key.with_suffix('.pub').read_text())
        body = {'version': 1, 'sha': 'a' * 40, 'repository': 'capkimkhanh2k5/DANASEA', 'run_id': 1,
                'action': 'deploy', 'images': {name: f'ghcr.io/capkimkhanh2k5/danasea-{name}@sha256:' + 'c' * 64 for name in ('backend', 'frontend', 'quyet')}}
        body['baseline_sha'] = 'b' * 40
        body['rehearsal'] = {'passed': True, 'images': body['images'], 'baseline_sha': 'b' * 40,
            'restore_verified': True, 'existing_data_preserved': True, 'booking_order_verified': True, 'worker_inference_verified': True}
        manifest = root / 'manifest.json'
        manifest.write_text(json.dumps(body))
        (root / '.releases' / ('b' * 40 + '.json')).write_text(json.dumps({**body, 'sha': 'b' * 40}))
        subprocess.run(['ssh-keygen', '-Y', 'sign', '-f', str(key), '-n', 'danasea-release', str(manifest)], check=True, capture_output=True)
        envelope = {'manifest': base64.b64encode(manifest.read_bytes()).decode(),
                    'signature': base64.b64encode(Path(str(manifest) + '.sig').read_bytes()).decode()}
        mock = '''import json, os, pathlib, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
root = pathlib.Path(os.environ['DANASEA_TEST_ROOT'])
log = pathlib.Path(os.environ['DANASEA_TEST_CALLS'])
def record(value):
    with log.open('a') as output: output.write(json.dumps(value) + '\\n')
record([name, *args])
if name == 'git':
    if args[0] == 'checkout':
        path = root / 'frontend/nginx.prod.conf'; path.write_text('fixture'); path.chmod(0o600)
    if args[0] == 'show': print('fixture compose')
    if 'diff' in args and os.environ['MIGRATIONS_CHANGED'] == '1': print('backend/src/main/resources/db/migration/V99.sql')
if name == 'python3':
    if args[0] == '-' and args[1] == os.environ['DEPLOY_POLICY'] and not os.environ.get('APP_DIR'):
        print(root)
    elif args[0] == '-' and args[1] == os.environ['DEPLOY_POLICY']:
        print('capkimkhanh2k5/DANASEA\\nhttps://example.invalid\\n' + str(root / 'signers') + '\\n' + str(root / 'backup.json') + '\\n30\\n60')
    elif args[0] == 'scripts/production.py':
        record(['preflight-modes', (root / 'frontend/nginx.prod.conf').stat().st_mode & 0o777, (root / '.env').stat().st_mode & 0o777])
        sys.exit(17 if os.environ['PREFLIGHT_FAILS'] == '1' else 0)
    elif args[0].endswith('/availability.py'):
        pathlib.Path(args[args.index('--output')+1]).write_text('{}')
        sys.exit(1 if os.environ['AVAILABILITY_FAILS'] == '1' else 0)
    elif args[0].endswith('/backup.py'): sys.exit(17 if os.environ['BACKUP_FAILS'] == '1' else 0)
    elif args[0].endswith('/acceptance.py'): sys.exit(17 if os.environ['ACCEPTANCE_FAILS'] == '1' else 0)
    else: os.execv(sys.executable, [sys.executable, *args])
if name == 'docker':
    if 'config' in args:
        previous = any('rollback-source.yml' in a for a in args)
        redis = 'redis:old' if previous and os.environ['INFRA_CHANGED'] == '1' else 'redis:current'
        payload = {'services': {'postgres': {'image': 'postgres:17-alpine'}, 'redis': {'image': redis}, 'rabbitmq': {'image': 'rabbitmq:pinned'}, 'quyet': {'image': 'quyet:old' if previous else 'quyet:new', 'read_only': True}}, 'networks': {}, 'volumes': {}}
        if previous and os.environ['PREVIOUS_WORKER'] == '0': payload['services'].pop('quyet')
        print(json.dumps(payload))
    if 'up' in args:
        calls = [json.loads(l) for l in log.read_text().splitlines()]
        count = sum(row[0] == 'docker' and 'up' in row for row in calls)
        sys.exit(1 if count == 1 and os.environ['DEPLOY_FAILS'] == '1' else 0)
'''
        for name in ('git', 'flock', 'python3', 'docker'):
            path = binary / name
            path.write_text('#!' + sys.executable + '\n' + mock)
            path.chmod(0o700)
        environment = {**os.environ, 'APP_DIR': str(root) if app_dir_in_environment else '', 'DANASEA_TEST_ROOT': str(root), 'TOOLS_DIR': str(ROOT / 'scripts'),
            'PATH': str(binary) + ':' + os.environ['PATH'], 'DEPLOY_POLICY': str(root / 'policy.json'),
            'SSH_ORIGINAL_COMMAND': 'deploy ' + 'a' * 40, 'DANASEA_TEST_CALLS': str(log)}
        for name, value in [('INFRA_CHANGED', infrastructure_changed), ('PREVIOUS_WORKER', previous_worker),
                            ('DEPLOY_FAILS', deploy_fails), ('ACCEPTANCE_FAILS', acceptance_fails),
                            ('BACKUP_FAILS', backup_fails), ('PREFLIGHT_FAILS', preflight_fails), ('MIGRATIONS_CHANGED', migrations_changed), ('AVAILABILITY_FAILS', availability_fails)]:
            environment[name] = '1' if value else '0'
        result = subprocess.run(['bash', str(ROOT / 'scripts/deploy.sh')], env=environment, input=json.dumps(envelope), capture_output=True, text=True, check=False)
        calls = [json.loads(line) for line in log.read_text().splitlines()]
        return result, calls, (root / '.deploy-state').read_text().strip()
