"""Authenticated, bounded demo requests use a separate SSH signing namespace."""
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import demo_request

SHA = 'a' * 40
REPO = 'capkimkhanh2k5/DANASEA'


def request(**changes):
    body = dict(version=1, repository=REPO, sha=SHA, package_sha256='b' * 64,
                action='replace', run_id=123, run_attempt=1, issued_at=int(time.time()),
                nonce='c' * 32, confirmation='REPLACE_DEMO_DATA')
    body.update(changes)
    return body


class DemoRequestTest(unittest.TestCase):
    def test_validate_binding_types_expiry_and_confirmation(self):
        body = request()
        self.assertEqual(demo_request.validate(body, SHA, REPO), body)
        for changes in ({'version': True}, {'run_id': True}, {'run_attempt': 0},
                        {'issued_at': int(time.time()) - 601}, {'issued_at': int(time.time()) + 61},
                        {'confirmation': 'RESTORE_DEMO_DATA'}, {'nonce': '../invalid'},
                        {'package_sha256': 'z' * 64}, {'password': 'hidden'}, {'sha': SHA + ';id'},
                        {'repository': '../../etc/passwd'}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                demo_request.validate(request(**changes))
        for binding in ({'sha': 'd' * 40}, {'repository': 'someone/else'}):
            with self.subTest(binding=binding), self.assertRaises(ValueError):
                demo_request.validate(body, **binding)

    def test_restore_only_accepts_backup_basename_and_exact_confirmation(self):
        self.assertEqual(demo_request.validate(request(action='restore', confirmation='RESTORE_DEMO_DATA',
                                                      backup_id='danasea-20261029T100000Z.dump'))['action'], 'restore')
        for name in ('../backup.dump', '/tmp/backup.dump', '-options', '.', 'backup;id', 'backup\n.dump', ''):
            with self.subTest(name=name), self.assertRaises(ValueError):
                demo_request.validate(request(action='restore', confirmation='RESTORE_DEMO_DATA', backup_id=name))
        with self.assertRaises(ValueError):
            demo_request.validate(request(backup_id='unexpected.dump'))
        demo_request.validate(request(action='inspect', confirmation=''))

    def signed(self, directory, body=None, namespace='danasea-demo-data'):
        key = directory / 'key'
        if not key.exists():
            subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(key)], check=True)
        data = directory / 'request.json'
        data.write_text(json.dumps(body or request()))
        data.with_suffix('.json.sig').unlink(missing_ok=True)
        subprocess.run(['ssh-keygen', '-Y', 'sign', '-f', str(key), '-n', namespace, str(data)],
                       check=True, capture_output=True)
        signers = directory / 'signers'
        signers.write_text('github-actions namespaces="danasea-demo-data" ' + key.with_suffix('.pub').read_text())
        envelope = json.dumps({'request': base64.b64encode(data.read_bytes()).decode(),
                               'signature': base64.b64encode(data.with_suffix('.json.sig').read_bytes()).decode(),
                               'password': 'private-demo-password'})
        return envelope, signers

    def test_real_ssh_signature_returns_private_password_separately(self):
        with tempfile.TemporaryDirectory() as tmp:
            envelope, signers = self.signed(Path(tmp))
            body, password = demo_request.verify(envelope, signers, SHA, REPO)
            self.assertEqual(password, 'private-demo-password')
            self.assertNotIn('password', body)
            self.assertEqual(body['sha'], SHA)
            for sha, repo in (('d' * 40, REPO), (SHA, 'other/repo')):
                with self.assertRaises(ValueError):
                    demo_request.verify(envelope, signers, sha, repo)
            payload = json.loads(envelope)
            payload['request'] = base64.b64encode(json.dumps(request(nonce='e' * 32)).encode()).decode()
            with self.assertRaises(ValueError):
                demo_request.verify(json.dumps(payload), signers, SHA, REPO)

    def test_release_namespace_signature_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            envelope, signers = self.signed(Path(tmp), namespace='danasea-release')
            with self.assertRaises(ValueError):
                demo_request.verify(envelope, signers, SHA, REPO)

    def test_real_signed_expired_request_and_wrong_identity_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            envelope, signers = self.signed(Path(tmp), request(issued_at=int(time.time()) - 601))
            with self.assertRaises(ValueError):
                demo_request.verify(envelope, signers, SHA, REPO)
            envelope, signers = self.signed(Path(tmp))
            signers.write_text(signers.read_text().replace('github-actions', 'other-principal'))
            with self.assertRaises(ValueError):
                demo_request.verify(envelope, signers, SHA, REPO)

    def test_envelope_rejects_oversize_invalid_base64_duplicate_keys_and_unknown_fields(self):
        with tempfile.TemporaryDirectory() as tmp:
            envelope, signers = self.signed(Path(tmp))
            for invalid in ('x' * 65537, '{}', '[]', '{"request":"x","request":"y"}',
                            json.dumps(dict(json.loads(envelope), password=['secret'])),
                            json.dumps(dict(json.loads(envelope), password='x' * 73)),
                            json.dumps(dict(json.loads(envelope), request='%%%')),
                            json.dumps(dict(json.loads(envelope), arbitrary='shell'))):
                with self.subTest(invalid=invalid[:70]), self.assertRaises(ValueError):
                    demo_request.verify(invalid, signers, SHA, REPO)

    def test_cli_create_and_envelope_keep_password_out_of_signed_body_and_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            output = directory / 'body.json'
            cli = Path(demo_request.__file__)
            args = [sys.executable, str(cli), 'create', '--action', 'replace', '--sha', SHA,
                    '--repository', REPO, '--package-sha256', 'b' * 64, '--run-id', '123',
                    '--run-attempt', '2', '--confirmation', 'REPLACE_DEMO_DATA', '--output', str(output)]
            result = subprocess.run(args, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(output.stat().st_mode & 0o777, 0o600)
            self.assertNotIn('password', output.read_text())
            result = subprocess.run(args + ['--backup-id', ''], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn('backup_id', json.loads(output.read_text()))
            envelope, _ = self.signed(directory)
            result = subprocess.run([sys.executable, str(cli), 'envelope', '--request', str(output),
                                     '--signature', str(directory / 'request.json.sig'),
                                     '--password-env', 'DEMO_USER_PASSWORD'],
                                    env=dict(os.environ, DEMO_USER_PASSWORD='private-demo-password'),
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout)['password'], 'private-demo-password')
            result = subprocess.run([sys.executable, str(cli), 'envelope', '--request', str(output),
                                     '--signature', str(directory / 'request.json.sig'),
                                     '--password-env', 'DEMO_USER_PASSWORD'],
                                    env=dict(os.environ, DEMO_USER_PASSWORD='short'),
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn('short', result.stderr)
            result = subprocess.run(args[:-2] + ['--output', '/no/such/path/private-demo-password'],
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn('private-demo-password', result.stderr)


if __name__ == '__main__':
    unittest.main()
