"""Restricted stdin dispatch and Linux bootstrap run without contacting production."""
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IMAGE = 'python:3.11-slim@sha256:ae952e0a57138a4317b9b61caaa19a8217c2f356803f8d7e63aad08769950937'
DRIVER = r'''
import json, os, pathlib, subprocess, sys
scenario = sys.argv[1]
root = pathlib.Path('/fixture')
root.mkdir()
app = root / 'app'
app.mkdir()
os.chown(app, 65534, 65534)
etc = pathlib.Path('/etc/danasea')
etc.mkdir()
key = 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
public = root / 'ci.pub'
public.write_text(key + ' fixture\n')
release = etc / 'release-signers'
release.write_text('github-actions namespaces="danasea-release" ' + key + '\n')
original_release = release.read_text()
policy = etc / 'deploy.json'
policy.write_text(json.dumps({'repository': 'capkimkhanh2k5/DANASEA', 'app_dir': str(app),
    'signers': str(release), 'backup_config': '/etc/danasea/backup.json', 'public_origin': 'https://example.invalid'}))
policy.chmod(0o644)
wrapper = pathlib.Path('/usr/local/bin/danasea-deploy')
wrapper.write_text('#!/bin/sh\n# existing\n')
wrapper.chmod(0o755)
old_wrapper = wrapper.read_text()
tools = pathlib.Path('/usr/local/lib/danasea')
tools.mkdir()
(tools / 'backup.py').write_text('previous helper')
private = pathlib.Path('/var/lib/danasea/demo-data')
if scenario == 'bad-policy': os.chown(policy, 65534, 65534)
if scenario == 'different-key': public.write_text(key[:-1] + 'B\n')
if scenario == 'existing-demo-trust': (etc / 'demo-data-signers').write_text('different trust\n')
if scenario == 'unsafe-tools': os.chown(tools, 65534, 65534)
if scenario == 'unsafe-state':
    private.mkdir(parents=True)
    private.chmod(0o777)
if scenario == 'symlink-wrapper':
    wrapper.unlink()
    wrapper.symlink_to(tools / 'backup.py')
binary = root / 'bin'
binary.mkdir()
ssh = binary / 'ssh-keygen'
ssh.write_text('#!/bin/sh\nexit 0\n')
ssh.chmod(0o755)
env = dict(os.environ, PATH=str(binary) + ':' + os.environ['PATH'])
result = subprocess.run(['bash', '/source/install-demo-data-controls.sh', str(public)],
    capture_output=True, text=True, env=env)
report = {'exit': result.returncode, 'stderr': result.stderr,
    'wrapper_preserved': wrapper.read_text() == old_wrapper,
    'release_unchanged': release.read_text() == original_release,
    'helper_preserved': (tools / 'backup.py').read_text() == 'previous helper'}
if result.returncode == 0:
    report['installed'] = all((tools / name).is_file() and (tools / name).stat().st_uid == 0
        for name in ('demo_request.py', 'demo_data.py', 'demo_runtime.py', 'backup.py', 'release.py', 'acceptance.py'))
    report['private_owner'] = private.stat().st_uid
    report['private_mode'] = private.stat().st_mode & 0o777
    access = subprocess.run(['runuser', '-u', 'nobody', '--', sys.executable, '-c',
        'from pathlib import Path; list(Path("/var/lib/danasea/demo-data").iterdir())'], capture_output=True)
    report['private_access'] = access.returncode
    report['demo_trust'] = (etc / 'demo-data-signers').read_text()
    again = subprocess.run(['bash', '/source/install-demo-data-controls.sh', str(public)], capture_output=True, env=env)
    report['repeat_exit'] = again.returncode
print(json.dumps(report))
'''


class DemoDispatchTest(unittest.TestCase):
    def test_demo_dispatch_passes_exact_sha_policy_and_original_stdin(self):
        with tempfile.TemporaryDirectory() as tmp:
            tools = Path(tmp)
            (tools / 'demo_data.py').write_text('import json, sys\nprint(json.dumps([sys.argv[1:], sys.stdin.read()]))\n')
            environment = dict(os.environ, TOOLS_DIR=str(tools), DEPLOY_POLICY='/etc/example.json',
                               SSH_ORIGINAL_COMMAND='demo-data ' + 'a' * 40)
            result = subprocess.run(['bash', str(ROOT / 'scripts/deploy.sh')], input='private envelope\n',
                                    text=True, capture_output=True, env=environment)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout), [['--sha', 'a' * 40, '--policy', '/etc/example.json'], 'private envelope\n'])

    def test_demo_dispatch_rejects_extra_arguments_shell_metacharacters_and_invalid_sha(self):
        for command in ('demo-data ' + 'a' * 40 + ' extra', 'demo-data ' + 'a' * 40 + ';id',
                        'demo-data ../path', 'demo-data ' + 'A' * 40, 'demo-data  ' + 'a' * 40):
            result = subprocess.run(['bash', str(ROOT / 'scripts/deploy.sh')],
                                    env=dict(os.environ, SSH_ORIGINAL_COMMAND=command), capture_output=True)
            self.assertEqual(result.returncode, 2)


class DemoBootstrapTest(unittest.TestCase):
    def install(self, scenario):
        result = subprocess.run(['docker', 'run', '--rm', '--network', 'none',
            '-v', str(ROOT / 'scripts') + ':/source:ro', IMAGE, 'python', '-c', DRIVER, scenario],
            capture_output=True, text=True, timeout=180, check=True)
        return json.loads(result.stdout)

    def test_bootstrap_installs_private_state_and_is_idempotent_without_changing_release_trust(self):
        result = self.install('success')
        self.assertEqual(result['exit'], 0, result['stderr'])
        self.assertTrue(result['installed'])
        self.assertTrue(result['release_unchanged'])
        self.assertIn('namespaces="danasea-demo-data"', result['demo_trust'])
        self.assertEqual(result['private_owner'], 65534)
        self.assertEqual(result['private_mode'], 0o700)
        self.assertEqual(result['private_access'], 0)
        self.assertEqual(result['repeat_exit'], 0)

    def test_bootstrap_rejects_unsafe_policy_key_rotation_trust_overwrite_and_wrapper_symlink(self):
        for scenario in ('bad-policy', 'different-key', 'existing-demo-trust', 'symlink-wrapper', 'unsafe-tools', 'unsafe-state'):
            with self.subTest(scenario=scenario):
                result = self.install(scenario)
                self.assertNotEqual(result['exit'], 0)
                self.assertTrue(result['helper_preserved'])
                self.assertTrue(result['release_unchanged'])
                if scenario != 'symlink-wrapper':
                    self.assertTrue(result['wrapper_preserved'])


if __name__ == '__main__':
    unittest.main()
