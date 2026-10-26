import hashlib
import importlib.util
import json
import os
import secrets
import shlex
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('production', ROOT / 'scripts/production.py')


class ProductionPreparationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = importlib.util.module_from_spec(SPEC)
        SPEC.loader.exec_module(cls.module)

    def test_prepare_preserves_credentials_oauth_and_existing_proxy_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = secrets.token_hex(32)
            (root / '.env').write_text('POSTGRES_DB=app\nPOSTGRES_USER=owner\nPOSTGRES_PASSWORD=' + original + '\nAPP_JWT_SECRET=' + original + '\n')
            auth = root / 'cli-proxy/auths/account.json'
            auth.parent.mkdir(parents=True)
            auth.write_text('oauth must survive')
            self.module.prepare(root)
            values = self.module.settings(root)
            self.assertEqual(values['APP_JWT_SECRET'], original)
            self.assertEqual(values['POSTGRES_PASSWORD'], original)
            self.assertEqual(len({values[k] for k in self.module.SECRETS}), len(self.module.SECRETS))
            config_path = root / 'private/cli-proxy/config.yaml'
            config = json.loads(config_path.read_text())
            self.assertEqual(config['access']['api-keys'], [values['AI_CLIPROXY_API_KEY']])
            self.assertEqual(config['oauth']['auth-dir'], '/auths')
            self.assertNotIn('api-keys', config)
            config['models'] = {'catalog': '/custom/catalog.json'}
            config_path.write_text(json.dumps(config))
            self.module.prepare(root)
            self.assertEqual(json.loads(config_path.read_text())['models'], config['models'])
            self.assertEqual(auth.read_text(), 'oauth must survive')
            self.assertEqual((root / '.env').stat().st_mode & 0o777, 0o600)
            self.assertEqual(config_path.stat().st_mode & 0o777, 0o600)

    def test_canonical_compose_env_parsing_and_no_shell_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / '.env').write_text("POSTGRES_PASSWORD='literal$with#symbols'\nAPP_JWT_SECRET='$(touch SHOULD_NOT_EXIST)'\n")
            values = self.module.settings(root)
            self.assertEqual(values['POSTGRES_PASSWORD'], 'literal$with#symbols')
            self.assertEqual(values['APP_JWT_SECRET'], '$(touch SHOULD_NOT_EXIST)')
            self.assertFalse((root / 'SHOULD_NOT_EXIST').exists())

    def test_malformed_private_config_never_exposes_its_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / '.env').write_text('POSTGRES_DB=app\nPOSTGRES_USER=owner\n')
            self.module.prepare(root)
            (root / 'frontend').mkdir()
            (root / 'frontend/nginx.prod.conf').write_text('fixture')
            config = root / 'private/cli-proxy/config.yaml'
            sensitive = secrets.token_hex(32)
            config.write_text('management: [\n  secret-key: ' + sensitive + '\n')
            with self.assertRaises(ValueError) as failure:
                self.module.check(root, self.module.settings(root))
            self.assertNotIn(sensitive, str(failure.exception))

    def test_private_management_key_cannot_be_missing_weak_or_reuse_client_key(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / '.env').write_text('POSTGRES_DB=app\nPOSTGRES_USER=owner\n')
            self.module.prepare(root)
            (root / 'frontend').mkdir()
            (root / 'frontend/nginx.prod.conf').write_text('fixture')
            config = root / 'private/cli-proxy/config.yaml'
            proxy = json.loads(config.read_text())
            values = self.module.settings(root)
            for key in ['', 'weak-management-key', values['AI_CLIPROXY_API_KEY']]:
                with self.subTest(key_type='missing' if not key else 'invalid'):
                    proxy['management']['secret-key'] = key
                    config.write_text(json.dumps(proxy))
                    with self.assertRaisesRegex(ValueError, 'management key'):
                        self.module.check(root, values)

    def test_network_overlap_only_permits_the_configured_edge_network(self):
        validate = self.module.validate_networks
        validate([{'Name': 'danasea_edge', 'IPAM': {'Config': [{'Subnet': '172.30.10.0/28'}]}}], [])
        for networks, routes in [
            ([{'Name': 'other', 'IPAM': {'Config': [{'Subnet': '172.30.0.0/16'}]}}], []),
            ([], [{'dst': '172.30.10.0/24', 'dev': 'vpn0'}]),
        ]:
            with self.assertRaises(ValueError):
                validate(networks, routes)

    def test_host_and_none_networks_without_ipam_subnets_are_ignored(self):
        self.module.validate_networks(
            [
                {'Name': 'host', 'IPAM': {'Config': None}},
                {'Name': 'none', 'IPAM': None},
            ],
            [],
        )

    def test_frontend_config_rejects_private_or_world_writable_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / 'nginx.prod.conf'
            config.write_text('public Nginx configuration')
            for mode in [0o600, 0o666]:
                config.chmod(mode)
                with self.assertRaises(ValueError):
                    self.module.validate_frontend_config(config)
            config.chmod(0o644)
            self.module.validate_frontend_config(config)

    def test_quyet_model_rejects_missing_tampered_unreadable_and_escaping_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            root.chmod(0o755)
            weights = root / 'model.safetensors'
            weights.write_bytes(b'fixture weights')
            weights.chmod(0o644)
            manifest = root / 'MANIFEST.sha256'
            manifest.write_text(hashlib.sha256(weights.read_bytes()).hexdigest() + '  model.safetensors\n')
            manifest.chmod(0o644)
            with patch.object(self.module, 'QUYET_MANIFEST_SHA256', hashlib.sha256(manifest.read_bytes()).hexdigest()):
                self.module.validate_quyet_model(str(root))
                weights.chmod(0o600)
                with self.assertRaisesRegex(ValueError, 'readable'):
                    self.module.validate_quyet_model(str(root))
                weights.chmod(0o644)
                weights.write_bytes(b'tampered')
                with self.assertRaisesRegex(ValueError, 'checksum'):
                    self.module.validate_quyet_model(str(root))
                weights.unlink()
                with self.assertRaisesRegex(ValueError, 'file path'):
                    self.module.validate_quyet_model(str(root))
            manifest.write_text('0' * 64 + '  ../outside\n')
            with (
                patch.object(self.module, 'QUYET_MANIFEST_SHA256', hashlib.sha256(manifest.read_bytes()).hexdigest()),
                self.assertRaisesRegex(ValueError, 'file path'),
            ):
                self.module.validate_quyet_model(str(root))
            with self.assertRaisesRegex(ValueError, 'pinned'):
                self.module.validate_quyet_model(str(root))
        with self.assertRaisesRegex(ValueError, 'absolute'):
            self.module.validate_quyet_model('')

    def test_database_role_cannot_be_the_migration_owner(self):
        with self.assertRaises(ValueError):
            self.module.runtime_role_sql({'POSTGRES_APP_USER': 'owner', 'POSTGRES_USER': 'owner', 'POSTGRES_DB': 'app', 'POSTGRES_APP_PASSWORD': secrets.token_hex(32)})
        sql = self.module.runtime_role_sql({'POSTGRES_APP_USER': 'app', 'POSTGRES_USER': 'owner', 'POSTGRES_DB': 'app', 'POSTGRES_APP_PASSWORD': secrets.token_hex(32)})
        self.assertIn('NOSUPERUSER', sql)
        self.assertIn('flyway_schema_history', sql)
        self.assertIn('REVOKE INSERT, UPDATE, DELETE', sql)


class DeploymentGuardTest(unittest.TestCase):
    def simulate_failed_deploy(self, infrastructure_changed, previous_worker=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binary = root / 'bin'
            binary.mkdir()
            (root / 'frontend').mkdir()
            (root / '.deploy-state').write_text('b' * 40)
            (root / '.env').write_text('fixture')
            (root / '.env').chmod(0o600)
            log = root / 'calls.jsonl'
            mock = '''import json, os, pathlib, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
root = pathlib.Path(os.environ['APP_DIR'])
log = pathlib.Path(os.environ['DANASEA_TEST_CALLS'])
def record(value):
    with log.open('a') as output:
        output.write(json.dumps(value) + '\\n')
record([name, *args])
if name == 'git' and args[0] == 'checkout':
    path = root / 'frontend/nginx.prod.conf'
    path.write_text('fixture')
    path.chmod(0o600)
if name == 'git' and args[0] == 'show':
    print('fixture compose')
if name == 'python3':
    if args[0] == 'scripts/production.py':
        record(['preflight-modes', (root / 'frontend/nginx.prod.conf').stat().st_mode & 0o777,
                (root / '.env').stat().st_mode & 0o777])
    else:
        os.execv(sys.executable, [sys.executable, *args])
if name == 'docker':
    if 'config' in args:
        previous = any('rollback-source.yml' in arg for arg in args)
        redis = 'redis:7.0-alpine' if previous and os.environ['DANASEA_TEST_INFRA_CHANGED'] == '1' else 'redis:7.4-alpine'
        payload = {'services': {'postgres': {'image': 'postgres:17-alpine'},
              'redis': {'image': redis}, 'rabbitmq': {'image': 'rabbitmq:4.3-alpine'},
              'quyet': {'image': 'quyet:old' if previous else 'quyet:new', 'read_only': True}},
              'networks': {}, 'volumes': {}}
        if previous and os.environ['DANASEA_TEST_PREVIOUS_WORKER'] == '0':
            payload['services'].pop('quyet')
        print(json.dumps(payload))
    if 'up' in args:
        calls = [json.loads(line) for line in log.read_text().splitlines()]
        count = sum(row[0] == 'docker' and 'up' in row for row in calls)
        sys.exit(1 if count == 1 else 0)
    if 'logs' in args:
        print('Using generated security password: fake-diagnostic-password')
    if 'inspect' in args:
        print('[]')
'''
            for name in ['git', 'flock', 'python3', 'docker']:
                path = binary / name
                path.write_text('#!' + sys.executable + '\n' + mock)
                path.chmod(0o700)
            environment = {
                **os.environ, 'APP_DIR': str(root),
                'PATH': str(binary) + ':' + os.environ['PATH'],
                'SSH_ORIGINAL_COMMAND': 'deploy ' + 'a' * 40,
                'DANASEA_TEST_CALLS': str(log),
                'DANASEA_TEST_INFRA_CHANGED': '1' if infrastructure_changed else '0',
                'DANASEA_TEST_PREVIOUS_WORKER': '1' if previous_worker else '0',
            }
            result = subprocess.run(['bash', str(ROOT / 'scripts/deploy.sh')],
                                    env=environment, capture_output=True, text=True)
            calls = [json.loads(line) for line in log.read_text().splitlines()]
            return result, calls

    def test_nginx_config_is_readable_without_relaxing_secret_permissions(self):
        _, calls = self.simulate_failed_deploy(False)
        self.assertIn(['preflight-modes', 0o644, 0o600], calls)

    def test_infrastructure_change_blocks_automatic_rollback(self):
        result, calls = self.simulate_failed_deploy(True)
        updates = [call for call in calls if call[0] == 'docker' and 'up' in call]
        self.assertEqual(result.returncode, 1)
        self.assertEqual(len(updates), 1)
        self.assertIn('Rollback blocked', result.stdout)
        self.assertFalse(any(call[0] == 'git' and 'checkout' in call and 'b' * 40 in call for call in calls))

    def test_compatible_rollback_only_restarts_applications_and_redacts_diagnostics(self):
        result, calls = self.simulate_failed_deploy(False)
        updates = [call for call in calls if call[0] == 'docker' and 'up' in call]
        self.assertEqual(result.returncode, 1)
        self.assertEqual(len(updates), 2)
        self.assertIn('--no-deps', updates[1])
        self.assertEqual(updates[1][-3:], ['quyet', 'backend', 'frontend'])
        self.assertNotIn('fake-diagnostic-password', result.stdout)

    def test_first_quyet_release_blocks_rollback_to_backend_without_worker(self):
        result, calls = self.simulate_failed_deploy(False, previous_worker=False)
        updates = [call for call in calls if call[0] == 'docker' and 'up' in call]
        self.assertEqual(result.returncode, 1)
        self.assertEqual(len(updates), 1)
        self.assertIn('Rollback blocked', result.stdout)

    def test_private_proxy_addresses_match_compose_and_backend(self):
        with tempfile.TemporaryDirectory() as directory:
            envfile = Path(directory) / '.env'
            text = (ROOT / '.env.example').read_text()
            for key in ['POSTGRES_APP_PASSWORD', 'APP_CHECKIN_QR_SECRET', 'APP_PAYMENT_WEBHOOK_SECRET', 'SPRING_REDIS_PASSWORD', 'SPRING_RABBITMQ_PASSWORD', 'AI_CLIPROXY_API_KEY', 'AI_QUYET_API_KEY']:
                text = text.replace(key + '=\n', key + '=' + secrets.token_hex(32) + '\n')
            text = text.replace('QUYET_MODEL_DIR=\n', 'QUYET_MODEL_DIR=/tmp/quyet-test-model\n')
            # The example targets a backend on the host; this fixture targets Docker production.
            text = text.replace('AI_QUYET_ENABLED=false\n', 'AI_QUYET_ENABLED=true\n')
            text = text.replace('AI_QUYET_BASE_URL=http://127.0.0.1:8091\n',
                                'AI_QUYET_BASE_URL=http://quyet:8091\n')
            envfile.write_text(text)
            environment = {key: value for key, value in os.environ.items()
                           if key not in ['AI_QUYET_ENABLED', 'AI_QUYET_BASE_URL']}
            command = ['docker', 'compose', '--env-file', str(envfile), '-f', str(ROOT / 'docker-compose.prod.yml'), 'config', '--format', 'json']
            result = subprocess.run(command, env=environment, capture_output=True, text=True, check=True)
            config = json.loads(result.stdout)
            # Explicit .env values must remain configurable, including disabling backend calls.
            envfile.write_text(text.replace('AI_QUYET_ENABLED=true\n', 'AI_QUYET_ENABLED=false\n')
                              .replace('AI_QUYET_BASE_URL=http://quyet:8091\n',
                                       'AI_QUYET_BASE_URL=http://external-worker:8091\n'))
            override = subprocess.run(command, env=environment, capture_output=True, text=True, check=True)
            overridden = json.loads(override.stdout)['services']['backend']['environment']
            self.assertEqual(overridden['AI_QUYET_ENABLED'], 'false')
            self.assertEqual(overridden['AI_QUYET_BASE_URL'], 'http://external-worker:8091')
        worker = config['services']['quyet']
        backend = config['services']['backend']
        self.assertEqual(backend['environment']['AI_QUYET_ENABLED'], 'true')
        self.assertEqual(backend['environment']['AI_QUYET_BASE_URL'], 'http://quyet:8091')
        self.assertEqual(backend['environment']['AI_QUYET_API_KEY'], worker['environment']['AI_QUYET_API_KEY'])
        self.assertEqual(backend['depends_on']['quyet']['condition'], 'service_healthy')
        self.assertNotIn('ports', worker)
        self.assertTrue(worker['volumes'][0]['read_only'])
        source = (ROOT / 'docker-compose.prod.yml').read_text()
        worker_source = source.split('\n  quyet:\n', 1)[1].split('\n  cli-proxy-api:\n', 1)[0]
        self.assertIn('        bind:\n          create_host_path: false\n', worker_source)
        self.assertIs(worker['volumes'][0].get('bind', {}).get('create_host_path', False), False)
        network = config['networks']['app']['ipam']['config'][0]
        self.assertEqual(network, {'subnet': '172.30.10.0/28', 'gateway': '172.30.10.1'})
        frontend = config['services']['frontend']['networks']['app']['ipv4_address']
        nginx = (ROOT / 'frontend/nginx.prod.conf').read_text()
        self.assertIn('set_real_ip_from ' + network['gateway'] + ';', nginx)
        self.assertIn("internal-proxies: '" + frontend.replace('.', '\\.') + "'", (ROOT / 'backend/src/main/resources/application-prod.yml').read_text())
        for service in config['services'].values():
            for setting in ['mem_limit', 'memswap_limit', 'cpus', 'pids_limit']:
                self.assertNotIn(setting, service)
        self.assertNotIn('X-Forwarded-Proto https', (ROOT / 'frontend/nginx.conf').read_text())
        self.assertIn('PRODUCTION_DEPLOY_ENABLED', (ROOT / '.github/workflows/cd.yml').read_text())

    def test_preflight_failure_never_starts_or_pulls_containers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binary = root / 'bin'
            binary.mkdir()
            log = root / 'calls'
            for name in ['git', 'flock', 'python3', 'docker']:
                path = binary / name
                path.write_text('#!/bin/sh\nprintf "%s\\n" "' + name + ' $*" >> ' + shlex.quote(str(log)) + '\n' + ('exit 17\n' if name == 'python3' else 'exit 0\n'))
                path.chmod(0o700)
            environment = {**os.environ, 'APP_DIR': str(root), 'PATH': str(binary) + ':' + os.environ['PATH'], 'SSH_ORIGINAL_COMMAND': 'deploy ' + 'a' * 40}
            result = subprocess.run(['bash', str(ROOT / 'scripts/deploy.sh')], env=environment, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            calls = log.read_text()
            self.assertIn('python3 scripts/production.py check', calls)
            self.assertNotIn('docker ', calls)

    def test_forced_command_rejects_extra_arguments_before_git(self):
        result = subprocess.run(['bash', str(ROOT / 'scripts/deploy.sh')], env={**os.environ, 'SSH_ORIGINAL_COMMAND': 'deploy ' + 'a' * 40 + ' extra'}, capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn('Usage:', result.stderr)


if __name__ == '__main__':
    unittest.main()
