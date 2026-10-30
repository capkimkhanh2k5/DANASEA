"""Exercise the Linux installer in an owned container, never the author's host."""
import json
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IMAGE = 'python:3.11-slim@sha256:ae952e0a57138a4317b9b61caaa19a8217c2f356803f8d7e63aad08769950937'
DRIVER = r"""
import json, os, pathlib, subprocess, sys
scenario = sys.argv[1]
root = pathlib.Path('/fixture')
root.mkdir()
app = root / 'app'
app.mkdir()
user = 'root' if scenario == 'root-user' else 'nobody'
if user == 'nobody': os.chown(app, 65534, 65534)
(app / '.deploy-state').write_text('a' * 40)
binary = root / 'bin'
binary.mkdir()
wrapper = pathlib.Path('/usr/local/bin/danasea-deploy')
old = '#!/bin/sh\n# Existing operator-reviewed deploy entrypoint\nexit 0\n'
wrapper.write_text(old)
wrapper.chmod(0o755)
helpers = pathlib.Path('/usr/local/lib/danasea')
helpers.mkdir()
backup = helpers / 'backup.py'
backup.write_text('previous helper')
for name in ('ssh-keygen', 'flock'):
    path = binary / name
    path.write_text('#!/bin/sh\nexit 0\n')
    path.chmod(0o755)
docker = binary / 'docker'
docker.write_text('#!' + sys.executable + '\n' + r'''
import json, os, sys
if os.environ.get('FAIL_ADOPTION') == '1': raise SystemExit(17)
args = sys.argv[1:]
prefix = 'ghcr.io/capkimkhanh2k5/danasea-'
if args[0] == 'inspect':
    name = args[1].removeprefix('danasea-')
    print(json.dumps([{'Config': {'Image': prefix + name + ':' + 'a' * 40},
        'Image': 'fixture-' + name, 'State': {'Health': {'Status': 'healthy'}}}]))
elif args[:2] == ['image', 'inspect']:
    name = args[2].removeprefix('fixture-')
    print(json.dumps([{'RepoDigests': [prefix + name + '@sha256:' + 'c' * 64]}]))
else: raise SystemExit(18)
''')
docker.chmod(0o755)
public = root / 'ci.pub'
public.write_text('ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA fixture\n')
environment = dict(os.environ, PATH=str(binary) + ':' + os.environ['PATH'],
    FAIL_ADOPTION='1' if scenario == 'adoption-failed' else '0')
origin = 'http://example.invalid' if scenario == 'invalid-origin' else 'https://example.invalid'
result = subprocess.run(['bash', '/source/install-production-controls.sh', user, str(app), origin,
    str(public), '123', str(root / 'backups')], env=environment, capture_output=True, text=True)
policy = pathlib.Path('/etc/danasea/backup.json')
saved = app / '.releases' / ('a' * 40 + '.json')
report = {'exit': result.returncode, 'old_wrapper_preserved': wrapper.read_text() == old,
    'old_helper_preserved': backup.read_text() == 'previous helper', 'adopted': saved.exists(),
    'backup_keep': json.loads(policy.read_text())['keep'] if policy.exists() else None}
if scenario == 'success' and result.returncode == 0:
    status = subprocess.run(['runuser', '-u', 'nobody', '--', str(wrapper), 'status'],
        env=environment, capture_output=True, text=True)
    report['status_exit'] = status.returncode
    if status.returncode == 0: report['status_sha'] = json.loads(status.stdout)['sha']
print(json.dumps(report))
"""


class InstallerTest(unittest.TestCase):
    def install(self, scenario):
        result = subprocess.run(['docker', 'run', '--rm', '--network', 'none',
            '-v', str(ROOT / 'scripts') + ':/source:ro', IMAGE, 'python', '-c', DRIVER, scenario],
            capture_output=True, text=True, timeout=180, check=True)
        return json.loads(result.stdout)

    def test_invalid_configuration_preserves_existing_deploy_entrypoint_and_helpers(self):
        result = self.install('invalid-origin')
        self.assertNotEqual(result['exit'], 0)
        self.assertTrue(result['old_wrapper_preserved'])
        self.assertTrue(result['old_helper_preserved'])

    def test_failed_legacy_adoption_preserves_existing_deploy_entrypoint(self):
        result = self.install('adoption-failed')
        self.assertNotEqual(result['exit'], 0)
        self.assertTrue(result['old_wrapper_preserved'])

    def test_successful_install_adopts_legacy_release_and_serves_restricted_status(self):
        result = self.install('success')
        self.assertEqual(result['exit'], 0)
        self.assertFalse(result['old_wrapper_preserved'])
        self.assertTrue(result['adopted'])
        self.assertEqual(result['backup_keep'], 7)
        self.assertEqual(result['status_exit'], 0)
        self.assertEqual(result['status_sha'], 'a' * 40)

    def test_root_cannot_be_the_deployment_account(self):
        result = self.install('root-user')
        self.assertNotEqual(result['exit'], 0)
        self.assertTrue(result['old_wrapper_preserved'])


if __name__ == '__main__':
    unittest.main()
