"""Real PostgreSQL 17 acceptance; RUN_DEMO_DATABASE_TESTS=1 enables Docker resources."""
import contextlib
import importlib.util
import io
import json
import os
import stat
import subprocess
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('demo_importer', ROOT / 'DB_Demo/install_demo_data.py')
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
        cls.addClassCleanup(lambda: subprocess.run(['docker', 'rm', '-f', cls.container], capture_output=True, check=False))
        cls.addClassCleanup(cls.folder.cleanup)
        for _ in range(60):
            ready = subprocess.run(['docker', 'exec', cls.container, 'pg_isready', '-U', 'postgres'], capture_output=True, check=False)
            if ready.returncode == 0:
                break
            time.sleep(.5)
        cls.sql_folder = tempfile.TemporaryDirectory(prefix='danasea-demo-flyway-')
        cls.addClassCleanup(cls.sql_folder.cleanup)
        mig_dir = ROOT / 'backend/src/main/resources/db/migration'
        for sql_file in mig_dir.glob('*.sql'):
            (Path(cls.sql_folder.name) / sql_file.name).write_bytes(sql_file.read_bytes().replace(b'\r\n', b'\n'))
        result = subprocess.run(['docker', 'run', '--rm', '--network', 'container:' + cls.container,
                                 '-v', cls.sql_folder.name + ':/flyway/sql:ro',
                                 'redgate/flyway:13.10.0', '-url=jdbc:postgresql://127.0.0.1:5432/demo_acceptance',
                                 '-user=postgres', 'migrate'], capture_output=True, text=True, timeout=180, check=False)
        if result.returncode:
            raise RuntimeError('Disposable Flyway migrations failed: ' + result.stdout + result.stderr)
        cls.args = IMPORTER.build_parser().parse_args(['--docker-container', cls.container, '--database', 'demo_acceptance'])

    def sql(self, statement):
        result = subprocess.run(['docker', 'exec', '-i', self.container, 'psql', '-XAtq', '-v', 'ON_ERROR_STOP=1',
                                 '-U', 'postgres', '-d', 'demo_acceptance'], input=statement, text=True, encoding='utf-8', capture_output=True, check=True)
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
        self.assertEqual(683, result['totalVerifiedInDb'])
        self.assertEqual('REPLACE_SUCCESS', result['status'])
        self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM users WHERE email='old@local.test'"))
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='config@local.test'"))
        self.assertEqual(before, self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM system_configs t"))
        self.assertEqual(rules, self.sql("SELECT json_agg(to_jsonb(t) ORDER BY id) FROM category_safety_rules t"))
        self.assertEqual(24, self.sql("SELECT to_json(count(*)) FROM service_safety_documents WHERE status='APPROVED' AND reviewed_by IS NOT NULL"))
        self.replace()
        self.assertEqual(36, self.sql("SELECT to_json(count(*)) FROM users"))

    def test_01b_replacement_materializes_bookable_demo_horizon(self):
        result = self.replace()
        schedule = result['demoOperatingSchedule']
        self.assertEqual(24, schedule['services'])
        self.assertEqual(90, schedule['horizonDays'])
        self.assertEqual(74, schedule['equipment'])
        self.assertGreater(schedule['departures'], 4000)
        self.assertEqual(24, self.sql("""
            SELECT to_json(count(DISTINCT s.service_id))
            FROM service_operating_schedules g JOIN service_slots s ON s.service_id = g.service_id
            WHERE s.date = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + 89
              AND s.status = 'OPEN'
        """))
        self.assertEqual(0, self.sql("""
            SELECT to_json(count(*)) FROM service_operating_schedules o JOIN services s ON s.id = o.service_id
            WHERE s.status <> 'PUBLISHED' OR NOT o.active OR o.ends_on IS NOT NULL
        """))
        self.assertEqual(0, self.sql("""
            SELECT to_json(count(*))
            FROM service_options o JOIN service_operating_schedules p ON p.service_id = o.service_id
            WHERE o.option_type = 'PRIVATE' AND o.status = 'ACTIVE' AND NOT EXISTS (
                SELECT 1 FROM service_operating_schedule_slots g JOIN service_slots s ON s.id = g.slot_id
                JOIN service_slot_units u ON u.slot_id = s.id
                WHERE s.service_id = o.service_id AND s.status = 'OPEN'
                  AND s.inventory_type = 'SHARED_CAPACITY_UNITS'
                  AND u.equipment_id IS NOT NULL AND u.capacity >= o.max_pax_per_package
            )
        """))
        base, transactions, _ = IMPORTER.load_fixtures()
        historical = {item['slot_id'] for item in transactions['booking_items']}
        original_dates = {row['id']: row['date'] for row in base['service_slots'] if row['id'] in historical}
        actual_dates = self.sql("""
            SELECT json_object_agg(s.id, s.date) FROM service_slots s
            WHERE s.id IN (SELECT slot_id FROM booking_items)
        """)
        self.assertEqual(original_dates, actual_dates)

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
        command = [os.sys.executable, '-B', str(ROOT / 'DB_Demo/install_demo_data.py'), '--replace', '--apply',
                   '--confirm-backup', '--docker-container', self.container, '--database', 'demo_acceptance',
                   '--ownership-file', str(self.receipt), '--demo-password-stdin']
        result = subprocess.run(command, input='local-acceptance-secret\n', text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('COMMITTED', json.loads(result.stdout)['transaction'])
        if os.name != 'nt':
            self.assertEqual(0o600, self.receipt.stat().st_mode & 0o777)
        pending = self.receipt.with_suffix('.json.pending')
        pending.write_text(self.receipt.read_text())
        result = subprocess.run(command, input='local-acceptance-secret\n', text=True, capture_output=True, check=False)
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
        with (
            patch('sys.argv', argv),
            patch.dict(os.environ, {'DEMO_USER_PASSWORD': 'local-acceptance-secret'}),
            patch.object(IMPORTER.os, 'replace', side_effect=interrupted),
            contextlib.redirect_stdout(io.StringIO()),
            self.assertRaises(OSError),
        ):
            IMPORTER.main()
        self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM users WHERE email='publication@local.test'"))
        self.assertFalse(receipt.exists())
        journal = json.loads(pending.read_text())
        self.assertEqual('COMMIT_PENDING', journal['state'])
        self.assertEqual('demo_acceptance', journal['target']['database'])
        if os.name != 'nt':
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

        if 'fcntl' not in sys.modules and importlib.util.find_spec('fcntl') is None:
            sys.modules['fcntl'] = unittest.mock.MagicMock()
        for p in [str(ROOT / 'scripts'), str(ROOT / 'scripts/demo'), str(ROOT / 'scripts/ops')]:
            if p not in sys.path:
                sys.path.insert(0, p)
                self.addCleanup(lambda p=p: sys.path.remove(p) if p in sys.path else None)

        import backup
        from demo_data import Journal
        from demo_runtime import Runtime

        orig_backup = backup.backup

        def win_backup(config):
            orig_stat = Path.stat
            orig_open = os.open

            def safe_stat(path_obj, *a, **kw):
                st = orig_stat(path_obj, *a, **kw)
                if 'source-backups' in str(path_obj):
                    class FakeStat:
                        def __getattr__(self, name):
                            return getattr(st, name)

                        @property
                        def st_mode(self):
                            return stat.S_IFDIR | 0o700

                    return FakeStat()
                return st

            def safe_open(file, flags, *a, **kw):
                if isinstance(file, (str, bytes, os.PathLike)) and os.path.isdir(file):
                    return 0
                return orig_open(file, flags, *a, **kw)

            orig_fsync = os.fsync

            def safe_fsync(fd):
                if fd == 0:
                    return
                return orig_fsync(fd)

            orig_close = os.close

            def safe_close(fd):
                if fd == 0:
                    return
                return orig_close(fd)

            with (
                patch.object(Path, 'stat', safe_stat),
                patch.object(os, 'fchmod', lambda *a, **kw: None, create=True),
                patch.object(os, 'open', safe_open),
                patch.object(os, 'fsync', safe_fsync),
                patch.object(os, 'close', safe_close),
            ):
                return orig_backup(config)

        if os.name == 'nt':
            def safe_atomic_json(path, value):
                p = Path(path)
                tmp = p.with_suffix('.tmp')
                tmp.write_text(json.dumps(value, sort_keys=True) + '\n')
                os.replace(tmp, p)
            patcher1 = patch('demo_data.atomic_json', safe_atomic_json)
            patcher2 = patch('demo_runtime.atomic_json', safe_atomic_json)
            patcher3 = patch.object(backup, 'backup', win_backup)
            patcher1.start()
            patcher2.start()
            patcher3.start()
            self.addCleanup(patcher1.stop)
            self.addCleanup(patcher2.stop)
            self.addCleanup(patcher3.stop)

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

        with (
            patch.object(runtime, 'command', side_effect=fail_restored_sql),
            self.assertRaises(subprocess.CalledProcessError) as failure,
        ):
            runtime.restore(point)
        self.assertIn(b'22012', failure.exception.stderr)
        self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM users WHERE email='original-recovery@local.test'"))
        self.assertEqual(35, self.sql("SELECT to_json(count(*)) FROM users WHERE email LIKE '%@danasea.test'"))
        try:
            runtime.restore(point)
        except subprocess.CalledProcessError as error:
            self.fail('Real recovery command failed: ' + error.stderr.decode())
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='original-recovery@local.test'"))
        self.assertEqual(runtime.baseline, runtime.fingerprints())
        runtime.ungate(runtime.journal)
        command = ['docker', 'exec', '-i', self.container, 'psql', '-XAtq', '-v', 'ON_ERROR_STOP=1',
                   '-U', 'demo_recovery_app', '-d', 'demo_acceptance']
        allowed = subprocess.run(command, input='SELECT count(*) FROM users;', text=True, capture_output=True, check=False)
        self.assertEqual(0, allowed.returncode, allowed.stderr)
        forbidden = subprocess.run(command, input='DELETE FROM flyway_schema_history WHERE false;', text=True, capture_output=True, check=False)
        self.assertNotEqual(0, forbidden.returncode)
        self.assertIn('permission denied', forbidden.stderr)

    def test_11_extended_business_integrity_zero_violations(self):
        """Buoc toan bo cac vi pham quan he nghiep vu mo rong ve 0 tren PostgreSQL 17."""
        self.replace()

        # 1. Zero mismatched discount or payment amounts
        mismatched_discounts = self.sql("""
            SELECT to_json(count(*))
            FROM master_orders m
            JOIN (
                SELECT master_order_id, SUM(discount_amount) AS total_sub_disc
                FROM sub_orders
                GROUP BY master_order_id
            ) s ON s.master_order_id = m.id
            JOIN payments p ON p.master_order_id = m.id
            WHERE m.discount_amount <> s.total_sub_disc
               OR p.amount <> (m.total_amount - m.discount_amount)
        """)
        self.assertEqual(0, mismatched_discounts, "Discount or payment amounts mismatched")

        # 2. Zero vendor vouchers applied to orders of another vendor
        invalid_vendor_vouchers = self.sql("""
            SELECT to_json(count(*))
            FROM master_orders m
            JOIN discount_codes d ON d.id = m.discount_code_id
            JOIN sub_orders s ON s.master_order_id = m.id
            WHERE d.scope = 'VENDOR' AND d.vendor_id <> s.vendor_id
        """)
        self.assertEqual(0, invalid_vendor_vouchers, "Vendor voucher applied to different vendor")

        # 3. Zero resolved refund disputes missing matching processed refunds
        missing_refunds = self.sql("""
            SELECT to_json(count(*))
            FROM disputes d
            JOIN sub_orders s ON s.id = d.sub_order_id
            WHERE d.status IN ('RESOLVED_REFUND', 'RESOLVED_PARTIAL')
              AND NOT EXISTS (
                  SELECT 1 FROM refunds r
                  WHERE r.sub_order_id = d.sub_order_id
                    AND r.refund_percentage = d.refund_percentage
                    AND r.amount = ROUND(s.final_amount * d.refund_percentage / 100.0, 2)
              )
        """)
        self.assertEqual(0, missing_refunds, "Resolved dispute missing matching refund record")

        # 4. Zero reschedule proposals targeting foreign service or completed orders
        invalid_proposals = self.sql("""
            SELECT to_json(count(*))
            FROM sub_order_reschedule_proposals p
            JOIN sub_orders s ON s.id = p.sub_order_id
            JOIN service_slots sl ON sl.id = p.proposed_slot_id
            WHERE sl.service_id <> s.service_id
               OR s.status = 'COMPLETED'
        """)
        self.assertEqual(0, invalid_proposals, "Reschedule proposal foreign service or completed order")

        # 5. Zero disruptions with departure time mismatched from original slot
        invalid_disruptions = self.sql("""
            SELECT to_json(count(*))
            FROM sub_order_disruptions d
            JOIN service_slots s ON s.id = d.original_slot_id
            WHERE d.original_departure_at <> (s.date + s.start_time) AT TIME ZONE 'Asia/Ho_Chi_Minh'
        """)
        self.assertEqual(0, invalid_disruptions, "Disruption original_departure_at mismatched from slot")

        # 6. Zero reviews on incomplete orders or mismatched users/services
        invalid_reviews = self.sql("""
            SELECT to_json(count(*))
            FROM reviews r
            JOIN sub_orders s ON s.id = r.sub_order_id
            JOIN master_orders m ON m.id = s.master_order_id
            WHERE s.status <> 'COMPLETED'
               OR r.customer_id <> m.customer_id
               OR r.service_id <> s.service_id
               OR r.vendor_id <> s.vendor_id
        """)
        self.assertEqual(0, invalid_reviews, "Reviews attached to incomplete orders or wrong entity")

        # 7. Diversity of payout statuses: must include PAID and REJECTED
        payout_statuses = self.sql("""
            SELECT json_agg(DISTINCT status) FROM payout_requests
        """)
        self.assertIn('PAID', payout_statuses)
        self.assertIn('REJECTED', payout_statuses)


if __name__ == '__main__':
    unittest.main()
