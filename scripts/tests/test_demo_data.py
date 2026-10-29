"""Lifecycle acceptance without accessing the deployment server."""
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


def load():
    path = Path(__file__).resolve().parents[1] / 'demo_data.py'
    spec = importlib.util.spec_from_file_location('demo_data', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Runtime:
    """Fault-injected external system boundary; lifecycle/storage are real."""
    def __init__(self, fail=None):
        self.events = []
        self.fail = fail or set()

    def step(self, name):
        self.events.append(name)
        if name in self.fail:
            raise ValueError('private credential must never be returned')

    def preflight(self, body): self.step('preflight')
    def enter_maintenance(self, journal): self.step('maintenance')
    def safe_state(self, journal): self.step('safe_state')
    def snapshot(self, directory):
        self.step('backup')
        return {'backup_id': 'verified-test.dump', 'sha256': 'a' * 64}
    def select_backup(self, backup_id):
        self.step('select_backup')
        return {'backup_id': backup_id, 'sha256': 'b' * 64}
    def rehearse(self, point, password): self.step('rehearse')
    def replace(self, point, password, directory):
        self.step('replace')
        return {'transaction': 'COMMITTED', 'totalVerifiedInDb': 270}
    def restore(self, point): self.step('restore')
    def invalidate(self): self.step('invalidate')
    def verify(self, demo=False, password=None): self.step('verify_demo' if demo else 'verify_original')
    def resume(self, journal): self.step('resume')
    def freeze(self, journal): self.step('freeze')


class LifecycleTest(unittest.TestCase):
    def setUp(self):
        self.module = load()
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.body = {'nonce': '1' * 32, 'sha': '2' * 40, 'action': 'replace',
                     'package_sha256': '3' * 64, 'run_id': 1, 'run_attempt': 1}

    def tearDown(self): self.temporary.cleanup()

    def operation(self, runtime, body=None):
        return self.module.execute(body or self.body, 'demo-password-value', runtime, self.root)

    def test_success_has_durable_private_journal_and_no_secret(self):
        runtime = Runtime()
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'REPLACED')
        self.assertEqual(summary['canonical_records'], 270)
        self.assertEqual(runtime.events, ['preflight', 'maintenance', 'safe_state', 'backup',
                                        'rehearse', 'replace', 'invalidate', 'verify_demo', 'resume'])
        journal = self.root / 'operations' / summary['operation_id'] / 'journal.json'
        self.assertEqual(json.loads(journal.read_text())['phase'], 'complete')
        self.assertEqual(journal.stat().st_mode & 0o777, 0o600)
        self.assertNotIn('password', journal.read_text())
        self.assertNotIn('password', json.dumps(summary))

    def test_nonce_replay_fails_before_runtime(self):
        self.operation(Runtime())
        runtime = Runtime()
        with self.assertRaisesRegex(ValueError, 'replay'):
            self.operation(runtime)
        self.assertEqual(runtime.events, [])

    def test_failed_preflight_never_stops_services(self):
        runtime = Runtime({'preflight'})
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'BLOCKED')
        self.assertEqual(runtime.events, ['preflight'])
        self.assertNotIn('credential', json.dumps(summary))

    def test_backup_and_rehearsal_failures_resume_original_without_replacement(self):
        for failure in ('backup', 'rehearse', 'safe_state'):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                runtime = Runtime({failure})
                summary = self.module.execute(self.body, 'secret', runtime, Path(directory))
                self.assertEqual(summary['status'], 'BLOCKED')
                self.assertNotIn('replace', runtime.events)
                self.assertNotIn('restore', runtime.events)
                self.assertEqual(runtime.events[-2:], ['verify_original', 'resume'])

    def test_uncertain_import_is_recovered_even_without_receipt(self):
        runtime = Runtime({'replace'})
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'ROLLED_BACK')
        self.assertIn('restore', runtime.events)
        self.assertEqual(runtime.events[-2:], ['verify_original', 'resume'])

    def test_postcommit_verification_failure_recovers_before_resuming(self):
        runtime = Runtime({'verify_demo'})
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'ROLLED_BACK')
        self.assertLess(runtime.events.index('restore'), runtime.events.index('resume'))

    def test_failed_recovery_keeps_maintenance_and_backup(self):
        runtime = Runtime({'verify_demo', 'restore'})
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'RECOVERY_REQUIRED')
        self.assertEqual(summary['backup_id'], 'verified-test.dump')
        self.assertNotIn('resume', runtime.events)
        self.assertEqual(runtime.events[-1], 'freeze')

    def test_uncertain_public_resume_never_discards_possible_new_writes(self):
        runtime = Runtime({'resume'})
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'RECOVERY_REQUIRED')
        self.assertNotIn('restore', runtime.events)
        self.assertEqual(runtime.events[-1], 'freeze')

    def test_interrupted_operation_blocks_new_replace(self):
        operation = self.root / 'operations' / ('f' * 32)
        operation.mkdir(parents=True)
        (operation / 'journal.json').write_text(json.dumps({'phase': 'commit_possible'}))
        runtime = Runtime()
        with self.assertRaisesRegex(ValueError, 'unfinished'):
            self.operation(runtime)
        self.assertEqual(runtime.events, [])

    def test_explicit_restore_checks_named_point_before_maintenance_and_keeps_current_backup(self):
        self.body.update(action='restore', backup_id='verified-original.dump')
        runtime = Runtime()
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'RESTORED')
        self.assertLess(runtime.events.index('select_backup'), runtime.events.index('maintenance'))
        self.assertLess(runtime.events.index('backup'), runtime.events.index('restore'))
        self.assertNotIn('replace', runtime.events)

    def test_inspect_does_not_enter_maintenance(self):
        self.body['action'] = 'inspect'
        runtime = Runtime()
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'INSPECTED')
        self.assertEqual(runtime.events, ['preflight'])

    def test_inspect_recovers_interrupted_precommit_without_a_backup(self):
        operation = self.root / 'operations' / ('f' * 32)
        operation.mkdir(parents=True)
        (operation / 'journal.json').write_text(json.dumps({'phase': 'guarded', 'database_gate': {'limit': -1, 'login': True}}))
        self.body['action'] = 'inspect'
        runtime = Runtime()
        summary = self.operation(runtime)
        self.assertEqual(summary['status'], 'INSPECTED')
        self.assertEqual(runtime.events, ['preflight', 'maintenance', 'verify_original', 'resume'])
        self.assertEqual(json.loads((operation / 'journal.json').read_text())['phase'], 'recovered')

    def test_inspect_does_not_resume_an_interrupted_possible_commit(self):
        operation = self.root / 'operations' / ('f' * 32)
        operation.mkdir(parents=True)
        (operation / 'journal.json').write_text(json.dumps({'phase': 'commit_possible'}))
        self.body['action'] = 'inspect'
        runtime = Runtime()
        self.operation(runtime)
        self.assertEqual(runtime.events, ['preflight'])
        self.assertEqual(json.loads((operation / 'journal.json').read_text())['phase'], 'commit_possible')

    def test_deployment_lock_contention(self):
        with self.module.deployment_lock(self.root):
            with self.assertRaisesRegex(ValueError, 'progress'):
                with self.module.deployment_lock(self.root):
                    self.fail('acquired an already held deployment lock')


class RuntimeGuardsTest(unittest.TestCase):
    def setUp(self):
        sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
        import demo_runtime
        self.module = demo_runtime

    def test_deployed_sha_drift_blocks_before_external_commands(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory)
            (app / '.deploy-state').write_text('a' * 40)
            runtime = self.module.Runtime({'app_dir': directory}, app)
            runtime.command = lambda *a, **k: self.fail('Must reject source drift before Docker or Git')
            with self.assertRaisesRegex(ValueError, 'Deployed SHA drift'):
                runtime.preflight({'sha': 'b' * 40})

    def test_actual_database_environment_must_match_compose_backup_target(self):
        expected = {'POSTGRES_DB': 'danasea', 'POSTGRES_USER': 'postgres'}
        self.module.validate_environment(expected, ['POSTGRES_DB=danasea', 'POSTGRES_USER=postgres'])
        with self.assertRaisesRegex(ValueError, 'environment drift'):
            self.module.validate_environment(expected, ['POSTGRES_DB=another_db', 'POSTGRES_USER=postgres'])

    def test_checkout_sha_drift_blocks_even_when_deploy_state_matches(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory)
            (app / '.deploy-state').write_text('a' * 40)
            runtime = self.module.Runtime({'app_dir': directory}, app)
            runtime.command = lambda *a, **k: ('b' * 40).encode()
            with self.assertRaisesRegex(ValueError, 'Checkout SHA drift'):
                runtime.preflight({'sha': 'a' * 40})

    def test_signed_manifest_must_match_git_object_before_package_or_runtime(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory)
            (app / '.deploy-state').write_text('a' * 40)
            runtime = self.module.Runtime({'app_dir': directory}, app)
            def command(args, **kwargs):
                if args[:2] == ['git', 'rev-parse']:
                    return ('a' * 40).encode()
                if args[:2] == ['git', 'diff']:
                    return b''
                if args[:2] == ['git', 'show']:
                    return b'another manifest'
                self.fail('Must reject Git manifest mismatch before external runtime')
            runtime.command = command
            with self.assertRaisesRegex(ValueError, 'Committed package manifest mismatch'):
                runtime.preflight({'sha': 'a' * 40, 'package_sha256': 'b' * 64})

    def test_unknown_redis_keys_block_the_entire_invalidation(self):
        self.assertTrue(self.module.known_redis_key('usersById::123'))
        self.assertTrue(self.module.known_redis_key('inventory:slot:123:holds'))
        self.assertTrue(self.module.known_redis_key('127.0.0.1'))
        self.assertFalse(self.module.known_redis_key('another-company:session'))
        self.assertFalse(self.module.known_redis_key('usersById-other::123'))
        for prefix in ('weather:forecast:', 'marine:forecast:', 'ai:weather:forecast:',
                       'ai:weather:safety:', 'ai:ratelimit:ip:', 'ai:ratelimit:user:', 'ai:ratelimit:anon:'):
            self.assertTrue(self.module.known_redis_key(prefix + 'test'))

    def test_active_inventory_hold_blocks_before_database_gate(self):
        runtime = self.module.Runtime({'app_dir': '/tmp'}, Path('/tmp'))
        runtime.audit_queues = lambda: None
        runtime.redis_keys = lambda: ['inventory:slot:123:holds']
        runtime.gate = lambda journal: self.fail('Database gate must not run for active holds')
        with self.assertRaisesRegex(ValueError, 'inventory hold'):
            runtime.safe_state({})

    def test_second_database_writer_blocks_before_role_or_connection_changes(self):
        runtime = self.module.Runtime({'app_dir': '/tmp'}, Path('/tmp'))
        runtime.runtime_role = 'danasea_app'
        runtime.database = 'danasea'
        queries = []
        def sql(query):
            queries.append(query)
            if 'pg_stat_activity' in query:
                return '1'
            return '{"limit": -1, "login": true}'
        runtime.sql = sql
        with tempfile.TemporaryDirectory() as directory:
            journal = load().Journal(Path(directory) / 'journal.json')
            with self.assertRaisesRegex(ValueError, '[Dd]atabase writer'):
                runtime.gate(journal)
        self.assertFalse(any('ALTER ' in query for query in queries))

    def test_manifest_rejects_paths_outside_package_and_modified_payload(self):
        import hashlib
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            payload = root / 'fixture.json'
            payload.write_text('fixture')
            manifest = root / 'MANIFEST.sha256'
            digest = hashlib.sha256(payload.read_bytes()).hexdigest()
            manifest.write_text(digest + '  fixture.json\n')
            expected = hashlib.sha256(manifest.read_bytes()).hexdigest()
            self.module.verify_package(root, expected)
            payload.write_text('modified')
            with self.assertRaises(ValueError):
                self.module.verify_package(root, expected)
            manifest.write_text(digest + '  ../fixture.json\n')
            with self.assertRaises(ValueError):
                self.module.verify_package(root, hashlib.sha256(manifest.read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
