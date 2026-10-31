import base64
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'release.py'
SHA = 'a' * 40
REPO = 'capkimkhanh2k5/DANASEA'


def manifest():
    images = {name: f'ghcr.io/capkimkhanh2k5/danasea-{name}@sha256:' + 'b' * 64 for name in ('backend', 'frontend', 'quyet')}
    return {'version': 1, 'sha': SHA, 'repository': REPO, 'run_id': 123, 'action': 'deploy',
            'images': images, 'baseline_sha': '', 'rehearsal': {'passed': True, 'images': images,
                'baseline_sha': '', 'restore_verified': True, 'existing_data_preserved': True,
                'booking_order_verified': True, 'worker_inference_verified': True}}


class ReleaseTest(unittest.TestCase):
    def run_release(self, *args, **kwargs):
        return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)],
                              capture_output=True, text=True, **kwargs, check=False)

    def test_valid_release_outputs_only_digest_pinned_images(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'release.json'
            path.write_text(json.dumps(manifest()))
            result = self.run_release('validate', path, '--sha', SHA, '--repository', REPO, '--action', 'deploy')
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout)['sha'], SHA)

    def test_wrong_sha_action_registry_mutable_image_or_missing_worker_is_rejected(self):
        for field, value in [('sha', 'c' * 40), ('action', 'rollback'), ('run_id', 0),
                             ('images', {'backend': 'evil.example/backend:latest'})]:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as directory:
                body = manifest()
                body[field] = value
                path = Path(directory) / 'release.json'
                path.write_text(json.dumps(body))
                result = self.run_release('validate', path, '--sha', SHA, '--repository', REPO, '--action', 'deploy')
                self.assertEqual(result.returncode, 1, result.stderr)

    def test_real_signature_verified_and_tampered_manifest_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key = root / 'signer'
            subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(key)], check=True)
            allowed = root / 'allowed'
            allowed.write_text('github-actions ' + key.with_suffix('.pub').read_text())
            path = root / 'release.json'
            path.write_text(json.dumps(manifest()))
            subprocess.run(['ssh-keygen', '-Y', 'sign', '-f', str(key), '-n', 'danasea-release', str(path)],
                           check=True, capture_output=True)
            envelope = {'manifest': base64.b64encode(path.read_bytes()).decode(),
                        'signature': base64.b64encode(Path(str(path) + '.sig').read_bytes()).decode()}
            args = ('verify', '--sha', SHA, '--repository', REPO, '--action', 'deploy', '--signers', allowed, '--output', root / 'verified.json')
            result = self.run_release(*args, input=json.dumps(envelope))
            self.assertEqual(result.returncode, 0, result.stderr)
            envelope['manifest'] = base64.b64encode(json.dumps({**manifest(), 'run_id': 456}).encode()).decode()
            self.assertEqual(self.run_release(*args, input=json.dumps(envelope)).returncode, 1)

    def test_history_rejects_downgrade_even_when_old_commit_is_on_main(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()
            git('init', '-q')
            git('config', 'user.email', 'test@example.invalid')
            git('config', 'user.name', 'Test')
            git('commit', '--allow-empty', '-qm', 'first')
            old = git('rev-parse', 'HEAD')
            git('commit', '--allow-empty', '-qm', 'second')
            new = git('rev-parse', 'HEAD')
            git('update-ref', 'refs/remotes/origin/main', new)
            result = self.run_release('history', '--root', root, '--sha', new, '--previous', old)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(self.run_release('history', '--root', root, '--sha', old, '--previous', new).returncode, 1)

    def test_publish_state_refuses_same_sha_with_replaced_image(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'candidate.json'
            path.write_text(json.dumps(manifest()))
            self.assertEqual(self.run_release('publish', path, '--root', root).returncode, 0)
            body = manifest()
            body['images']['backend'] = body['images']['backend'].replace('b' * 64, 'c' * 64)
            path.write_text(json.dumps(body))
            self.assertEqual(self.run_release('publish', path, '--root', root).returncode, 1)
            self.assertEqual((root / '.deploy-state').read_text().strip(), SHA)

    def test_changed_migrations_disable_rollback(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()
            git('init', '-q')
            git('config', 'user.email', 'test@example.invalid')
            git('config', 'user.name', 'Test')
            git('commit', '--allow-empty', '-qm', 'first')
            old = git('rev-parse', 'HEAD')
            sql = root / 'backend/src/main/resources/db/migration/V1__test.sql'
            sql.parent.mkdir(parents=True)
            sql.write_text('DROP TABLE important;')
            git('add', '.')
            git('commit', '-qm', 'migration')
            new = git('rev-parse', 'HEAD')
            self.assertEqual(self.run_release('compatible', '--root', root, '--sha', old, '--previous', old).returncode, 0)
            self.assertEqual(self.run_release('compatible', '--root', root, '--sha', new, '--previous', old).returncode, 1)

    def test_promotion_requires_successful_rehearsal_for_the_exact_images_and_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'candidate.json'
            path.write_text(json.dumps(manifest()))
            evidence = root / 'rehearsal.json'
            evidence.write_text(json.dumps(manifest()['rehearsal']))
            args = ('promote', path, '--rehearsal', evidence, '--baseline-sha', '', '--action', 'deploy', '--output', root / 'promoted.json')
            self.assertEqual(self.run_release(*args).returncode, 0)
            body = manifest()['rehearsal']
            body['baseline_sha'] = 'f' * 40
            evidence.write_text(json.dumps(body))
            self.assertEqual(self.run_release(*args).returncode, 1)


if __name__ == '__main__':
    unittest.main()
