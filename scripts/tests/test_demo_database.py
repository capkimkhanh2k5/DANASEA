"""Real PostgreSQL 17 acceptance; RUN_DEMO_DATABASE_TESTS=1 enables Docker resources."""
import contextlib
import importlib.util
import io
import json
import os
import subprocess
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('demo_importer', ROOT / 'demo_data_handoff/install_demo_data.py')
IMPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPORTER)


@unittest.skipUnless(os.environ.get('RUN_DEMO_DATABASE_TESTS') == '1', 'Set RUN_DEMO_DATABASE_TESTS=1 for disposable PostgreSQL acceptance')
class DemoDatabaseAcceptance(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = 'danasea-demo-acceptance-' + uuid.uuid4().hex[:12]
        cls.folder = tempfile.TemporaryDirectory(prefix='danasea-demo-receipt-')
        cls.receipt = Path(cls.folder.name) / 'ownership.json'
        subprocess.run(['docker', 'run', '-d', '--name', cls.container, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust',
                        '-e', 'POSTGRES_USER=postgres', '-e', 'POSTGRES_DB=demo_acceptance', 'postgres:17-alpine'], check=True, capture_output=True)
        cls.addClassCleanup(lambda: subprocess.run(['docker', 'rm', '-f', cls.container], capture_output=True))
        cls.addClassCleanup(cls.folder.cleanup)
        for _ in range(60):
            ready = subprocess.run(['docker', 'exec', cls.container, 'pg_isready', '-U', 'postgres'], capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(.5)
        result = subprocess.run(['docker', 'run', '--rm', '--network', 'container:' + cls.container,
                                 '-v', str(ROOT / 'backend/src/main/resources/db/migration') + ':/flyway/sql:ro',
                                 'redgate/flyway:13.10.0', '-url=jdbc:postgresql://127.0.0.1:5432/demo_acceptance',
                                 '-user=postgres', 'migrate'], capture_output=True, text=True, timeout=180)
        if result.returncode:
            raise RuntimeError('Disposable Flyway migrations failed: ' + result.stdout + result.stderr)
        cls.args = IMPORTER.build_parser().parse_args(['--docker-container', cls.container, '--database', 'demo_acceptance'])

    def sql(self, statement):
        result = subprocess.run(['docker', 'exec', '-i', self.container, 'psql', '-XAtq', '-v', 'ON_ERROR_STOP=1',
                                 '-U', 'postgres', '-d', 'demo_acceptance'], input=statement, text=True, capture_output=True, check=True)
        return json.loads(result.stdout) if result.stdout.strip() else None

    def replace(self, commit=True, corrupt=False):
        operation = getattr(IMPORTER, 'execute_replace', None)
        self.assertTrue(callable(operation), 'Missing atomic application-data replacement')
        base, trans, _ = IMPORTER.load_fixtures()
        IMPORTER.prepare_slots(base, trans, {})
        if corrupt:
            base['services'][0]['status'] = 'INVALID_STATUS'
        session = IMPORTER.Session(self.args)
        try:
            result = operation(session, base, trans, 'local-acceptance-secret')
            session.finish(commit=commit)
            return result
        finally:
            session.close()

    def test_01_replacement_preserves_configuration_closure_and_removes_sentinel(self):
        self.sql("INSERT INTO users(id,created_at,updated_at,email,role) VALUES ('ffffffff-0000-0000-0000-000000000001',now(),now(),'old@local.test','CUSTOMER'),('ffffffff-0000-0000-0000-000000000002',now(),now(),'config@local.test','ADMIN'); UPDATE system_configs SET updated_by='ffffffff-0000-0000-0000-000000000002';")
        before = self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM system_configs t")
        rules = self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM category_safety_rules t")
        result = self.replace()
        self.assertEqual(270, result['totalVerifiedInDb'])
        self.assertEqual('REPLACE_SUCCESS', result['status'])
        self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM users WHERE email='old@local.test'"))
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='config@local.test'"))
        self.assertEqual(before, self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM system_configs t"))
        self.assertEqual(rules, self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM category_safety_rules t"))
        self.assertEqual(24, self.sql("SELECT to_json(count(*)) FROM service_safety_documents WHERE status='PENDING' AND reviewed_by IS NULL"))
        self.replace()
        self.assertEqual(13, self.sql("SELECT to_json(count(*)) FROM users"))

    def test_02_failed_import_and_dry_run_restore_old_data(self):
        self.sql("INSERT INTO users(id,created_at,updated_at,email,role) VALUES ('ffffffff-0000-0000-0000-000000000003',now(),now(),'rollback@local.test','CUSTOMER')")
        with self.assertRaises(ValueError):
            self.replace(corrupt=True)
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='rollback@local.test'"))
        self.replace(commit=False)
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='rollback@local.test'"))

    def test_03_unknown_table_schema_and_foreign_key_block_before_deletion(self):
        self.sql("INSERT INTO users(id,created_at,updated_at,email,role) VALUES ('ffffffff-0000-0000-0000-000000000009',now(),now(),'unknown-guard@local.test','CUSTOMER')")
        for create, drop in [('CREATE TABLE public.unreviewed(id int)', 'DROP TABLE public.unreviewed'),
                             ('CREATE SCHEMA unexpected', 'DROP SCHEMA unexpected'),
                             ('ALTER TABLE system_configs ADD COLUMN new_principal uuid', 'ALTER TABLE system_configs DROP COLUMN new_principal'),
                             ('ALTER TABLE users ADD CONSTRAINT unreviewed_fk FOREIGN KEY (id) REFERENCES users(id)', 'ALTER TABLE users DROP CONSTRAINT unreviewed_fk')]:
            with self.subTest(create=create):
                self.sql(create)
                try:
                    with self.assertRaisesRegex(ValueError, 'inventory|schema|foreign|reviewed'):
                        self.replace()
                    self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='unknown-guard@local.test'"))
                finally:
                    self.sql(drop)

    def test_04_protected_fixture_conflict_blocks_without_overwriting(self):
        self.sql("UPDATE categories SET name='Protected custom category' WHERE id='a0000001-0000-0000-0000-000000000001'")
        try:
            with self.assertRaisesRegex(ValueError, '[Pp]rotected|conflict'):
                self.replace()
            self.assertEqual('Protected custom category', self.sql("SELECT to_json(name) FROM categories WHERE id='a0000001-0000-0000-0000-000000000001'"))
        finally:
            base, _, _ = IMPORTER.load_fixtures()
            category = next(c for c in base['categories'] if c['id'] == 'a0000001-0000-0000-0000-000000000001')
            self.sql('UPDATE categories SET name=' + IMPORTER.lit(category['name']) + " WHERE id='a0000001-0000-0000-0000-000000000001'")

    def test_05_cli_commits_private_receipt_and_rejects_pending_replay(self):
        command = [os.sys.executable, '-B', str(ROOT / 'demo_data_handoff/install_demo_data.py'), '--replace', '--apply',
                   '--confirm-backup', '--docker-container', self.container, '--database', 'demo_acceptance',
                   '--ownership-file', str(self.receipt), '--demo-password-stdin']
        result = subprocess.run(command, input='local-acceptance-secret\n', text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('COMMITTED', json.loads(result.stdout)['transaction'])
        self.assertEqual(0o600, self.receipt.stat().st_mode & 0o777)
        pending = self.receipt.with_suffix('.json.pending')
        pending.write_text(self.receipt.read_text())
        result = subprocess.run(command, input='local-acceptance-secret\n', text=True, capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('pending', result.stderr.lower())
        self.assertTrue(pending.exists())
        pending.unlink()


    def test_06_category_ancestors_and_immutable_financial_configuration_survive(self):
        self.sql("""
        INSERT INTO categories(id,name,slug,parent_id,is_active,requires_safety_cert,created_at,updated_at) VALUES
        ('eeeeeeee-0000-0000-0000-000000000001','Grandparent','protected-grandparent',NULL,true,false,now(),now()),
        ('eeeeeeee-0000-0000-0000-000000000002','Parent','protected-parent','eeeeeeee-0000-0000-0000-000000000001',true,false,now(),now()),
        ('eeeeeeee-0000-0000-0000-000000000003','Child','protected-child','eeeeeeee-0000-0000-0000-000000000002',true,true,now(),now());
        INSERT INTO category_safety_rules(category_id,category_slug,category_name,max_wave_height_m,max_wind_speed_kmh)
        VALUES (NULL,'protected-child','Child',1,20);
        INSERT INTO users(id,email,role,created_at,updated_at) VALUES
        ('eeeeeeee-0000-0000-0000-000000000004','policy-owner@local.test','VENDOR',now(),now()),
        ('eeeeeeee-0000-0000-0000-000000000005','policy-admin@local.test','ADMIN',now(),now()),
        ('eeeeeeee-0000-0000-0000-000000000006','policy-verifier@local.test','ADMIN',now(),now());
        INSERT INTO vendors(id,user_id,verified_by,created_at,updated_at)
        VALUES ('eeeeeeee-0000-0000-0000-000000000007','eeeeeeee-0000-0000-0000-000000000004','eeeeeeee-0000-0000-0000-000000000006',now(),now());
        INSERT INTO vendor_commission_policies(id,vendor_id,version,commission_rate,maximum_subsidy_ratio,estimated_unit_cost,minimum_unit_margin,provenance,created_by)
        VALUES ('eeeeeeee-0000-0000-0000-000000000008','eeeeeeee-0000-0000-0000-000000000007',1,0.1,0.1,100,10,'acceptance','eeeeeeee-0000-0000-0000-000000000005');
        """)
        before = self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM vendor_commission_policies t")
        self.replace()
        self.assertEqual(before, self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM vendor_commission_policies t"))
        self.assertEqual(3, self.sql("SELECT to_json(count(*)) FROM categories WHERE slug LIKE 'protected-%'"))
        self.assertEqual(3, self.sql("SELECT to_json(count(*)) FROM users WHERE email LIKE 'policy-%@local.test'"))

    def test_07_commit_publication_failure_retains_recoverable_pending_receipt(self):
        receipt = Path(self.folder.name) / 'interrupted.json'
        pending = receipt.with_suffix('.json.pending')
        self.sql("INSERT INTO users(id,created_at,updated_at,email,role) VALUES ('ffffffff-0000-0000-0000-000000000010',now(),now(),'publication@local.test','CUSTOMER')")
        original = os.replace

        def interrupted(source, destination):
            if destination == receipt:
                raise OSError('Simulated interrupted receipt publication')
            return original(source, destination)

        argv = ['install_demo_data.py', '--replace', '--apply', '--confirm-backup', '--docker-container', self.container,
                '--database', 'demo_acceptance', '--ownership-file', str(receipt)]
        with patch('sys.argv', argv), patch.dict(os.environ, {'DEMO_USER_PASSWORD': 'local-acceptance-secret'}), patch.object(IMPORTER.os, 'replace', side_effect=interrupted):
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(OSError):
                IMPORTER.main()
        self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM users WHERE email='publication@local.test'"))
        self.assertFalse(receipt.exists())
        journal = json.loads(pending.read_text())
        self.assertEqual('COMMIT_PENDING', journal['state'])
        self.assertEqual('demo_acceptance', journal['target']['database'])
        self.assertEqual(0o600, pending.stat().st_mode & 0o777)
        self.assertIn('operationId', journal)

    def test_08_session_closes_all_database_process_streams(self):
        session = IMPORTER.Session(self.args)
        session.query('SELECT to_json(1)')
        session.finish(commit=False)
        session.close()
        self.assertTrue(session.p.stdout.closed)
        self.assertTrue(session.p.stderr.closed)


    def test_09_protected_email_collision_is_rejected_even_without_database_unique_constraint(self):
        self.sql("INSERT INTO users(id,email,role,created_at,updated_at) VALUES ('ffffffff-0000-0000-0000-000000000002','admin_demo@danasea.test','ADMIN',now(),now()) ON CONFLICT(id) DO UPDATE SET email=EXCLUDED.email; UPDATE system_configs SET updated_by='ffffffff-0000-0000-0000-000000000002'")
        try:
            with self.assertRaisesRegex(ValueError, '[Pp]rotected.*conflict'):
                self.replace()
        finally:
            self.sql("UPDATE users SET email='config@local.test' WHERE id='ffffffff-0000-0000-0000-000000000002'")


    def test_10_runtime_restore_rehearsal_and_failure_rollback_preserve_runtime_grants(self):
        # Only RabbitMQ inspection is omitted: this is the real PostgreSQL recovery boundary.
        import sys
        sys.path.insert(0, str(ROOT / 'scripts'))
        self.addCleanup(lambda: sys.path.remove(str(ROOT / 'scripts')))
        from demo_data import Journal
        from demo_runtime import Runtime

        state = Path(self.folder.name) / 'recovery'
        state.mkdir(mode=0o700)
        backups = state / 'source-backups'
        runtime = Runtime({'app_dir': str(ROOT)}, state)
        runtime.container = self.container
        runtime.admin = 'postgres'
        runtime.database = 'demo_acceptance'
        runtime.runtime_role = 'demo_recovery_app'
        runtime.body = {'sha': 'a' * 40}
        runtime.backup_config = {'directory': str(backups), 'container': self.container}
        runtime.audit_queues = lambda: None
        runtime.journal = Journal(state / 'journal.json', phase='guarded')
        runtime.journal.save()
        self.sql("CREATE ROLE demo_recovery_app LOGIN; GRANT CONNECT ON DATABASE demo_acceptance TO demo_recovery_app; GRANT USAGE ON SCHEMA public TO demo_recovery_app; GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO demo_recovery_app; REVOKE INSERT,UPDATE,DELETE ON flyway_schema_history FROM demo_recovery_app;")
        self.sql("INSERT INTO users(id,created_at,updated_at,email,role) VALUES ('ffffffff-0000-0000-0000-000000000011',now(),now(),'original-recovery@local.test','CUSTOMER')")
        runtime.baseline = runtime.fingerprints()
        runtime.config_fingerprint = runtime.baseline['configuration_sha256']
        runtime.gate(runtime.journal)
        self.addCleanup(lambda: runtime.ungate(runtime.journal))
        point = runtime.snapshot(state)
        self.assertTrue(point['restore_verified'])
        self.assertEqual(point, runtime.select_backup(point['backup_id']))
        runtime.rehearse(point, 'local-acceptance-secret')
        self.replace()
        self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM users WHERE email='original-recovery@local.test'"))
        real_command = runtime.command

        def fail_restored_sql(arguments, **kwargs):
            if '--single-transaction' in arguments:
                stream = kwargs['stdin']
                stream.seek(0, 2)
                stream.write(b'\nSELECT 1/0;\n')
                stream.flush()
                stream.seek(0)
            return real_command(arguments, **kwargs)

        with patch.object(runtime, 'command', side_effect=fail_restored_sql):
            with self.assertRaises(subprocess.CalledProcessError) as failure:
                runtime.restore(point)
        self.assertIn(b'22012', failure.exception.stderr)
        self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM users WHERE email='original-recovery@local.test'"))
        self.assertEqual(12, self.sql("SELECT to_json(count(*)) FROM users WHERE email LIKE '%@danasea.test'"))
        try:
            runtime.restore(point)
        except subprocess.CalledProcessError as error:
            self.fail('Real recovery command failed: ' + error.stderr.decode())
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='original-recovery@local.test'"))
        self.assertEqual(runtime.baseline, runtime.fingerprints())
        runtime.ungate(runtime.journal)
        command = ['docker', 'exec', '-i', self.container, 'psql', '-XAtq', '-v', 'ON_ERROR_STOP=1',
                   '-U', 'demo_recovery_app', '-d', 'demo_acceptance']
        allowed = subprocess.run(command, input='SELECT count(*) FROM users;', text=True, capture_output=True)
        self.assertEqual(0, allowed.returncode, allowed.stderr)
        forbidden = subprocess.run(command, input='DELETE FROM flyway_schema_history WHERE false;', text=True, capture_output=True)
        self.assertNotEqual(0, forbidden.returncode)
        self.assertIn('permission denied', forbidden.stderr)


if __name__ == '__main__':
    unittest.main()
