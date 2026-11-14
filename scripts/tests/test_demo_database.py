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
from datetime import date, datetime, timedelta, timezone
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
        self.assertEqual(687, result['totalVerifiedInDb'])
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
        schedule_start = date.fromisoformat(schedule['through']) - timedelta(days=schedule['horizonDays'] - 1)
        original_dates[IMPORTER.RESCHEDULE_ORIG_SLOT_ID] = (schedule_start + timedelta(days=6)).isoformat()
        self.assertEqual(original_dates, actual_dates)

    def test_02_failed_import_and_dry_run_restore_old_data(self):
        self.sql("INSERT INTO users(id,created_at,updated_at,email,role) VALUES ('ffffffff-0000-0000-0000-000000000003',now(),now(),'rollback@local.test','CUSTOMER')")
        with self.assertRaises(ValueError):
            self.replace(corrupt=True)
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='rollback@local.test'"))
        self.replace(commit=False)
        self.assertEqual(1, self.sql("SELECT to_json(count(*)) FROM users WHERE email='rollback@local.test'"))

    def test_replacement_preserves_the_original_slot_id_when_its_old_departure_is_reused(self):
        for import_day in (13, 15):
            now = datetime(2026, 11, import_day, 0, tzinfo=timezone(timedelta(hours=7)))
            with self.subTest(import_date=now.date()):
                base, trans, _ = IMPORTER.load_fixtures()
                IMPORTER.prepare_slots(base, trans, {}, today=now.date())
                session = IMPORTER.Session(self.args)
                try:
                    with patch.object(IMPORTER, 'db_now_vn', return_value=now):
                        send = session.send
                        # Keep SQL deadline checks on the same fixed clock as the importer.
                        with patch.object(session, 'send', side_effect=lambda sql: send(
                            sql.replace('CURRENT_TIMESTAMP', f"TIMESTAMPTZ '{now.isoformat()}'")
                        )):
                            result = IMPORTER.execute_replace(session, base, trans, 'local-acceptance-secret')
                    self.assertEqual('REPLACE_SUCCESS', result['status'])
                    slots = session.query("""
                        SELECT json_object_agg(s.date::text, s.id)
                        FROM service_slots s
                        WHERE s.service_id = '88e06f4d-fbb7-5700-a45d-0f6ad1cb0d08'
                          AND s.start_time = '07:00' AND s.date BETWEEN '2026-11-19' AND '2026-11-22'
                    """)
                    original_date = (now.date() + timedelta(days=6)).isoformat()
                    self.assertEqual(IMPORTER.RESCHEDULE_ORIG_SLOT_ID, slots[original_date])
                    self.assertIn('2026-11-20', slots)
                    self.assertNotEqual(IMPORTER.RESCHEDULE_ORIG_SLOT_ID, slots['2026-11-20'])
                    proposal_slot = session.query("""
                        SELECT to_json(proposed_slot_id) FROM sub_order_reschedule_proposals
                        WHERE id = 'fa010000-0000-0000-0000-000000000001'
                    """)
                    self.assertEqual(slots[(now.date() + timedelta(days=7)).isoformat()], proposal_slot)
                finally:
                    session.close()

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
        import bcrypt

        command = [os.sys.executable, '-B', str(ROOT / 'DB_Demo/install_demo_data.py'), '--replace', '--apply',
                   '--confirm-backup', '--docker-container', self.container, '--database', 'demo_acceptance',
                   '--ownership-file', str(self.receipt)]
        env = {**os.environ, 'DEMO_USER_PASSWORD': ''}
        result = subprocess.run(command, env=env, text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('COMMITTED', json.loads(result.stdout)['transaction'])
        base_rows, _, _ = IMPORTER.load_fixtures()
        expected_emails = {row['email'] for row in base_rows['users']}
        hashes = self.sql("SELECT json_object_agg(email, password_hash) FROM users WHERE email LIKE '%@danasea.test'")
        self.assertEqual(expected_emails, set(hashes))
        self.assertTrue(all(bcrypt.checkpw(b'danasea@2026', value.encode()) for value in hashes.values()))
        self.assertNotIn('danasea@2026', result.stdout)
        if os.name != 'nt':
            self.assertEqual(0o600, self.receipt.stat().st_mode & 0o777)
        pending = self.receipt.with_suffix('.json.pending')
        pending.write_text(self.receipt.read_text())
        result = subprocess.run(command, env=env, text=True, capture_output=True, check=False)
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
        runtime.audit_package_schema()
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

        # 1. Zero mismatched discount, commission basis, payment amount, or non-canonical payments
        mismatched_discounts = self.sql("""
            SELECT to_json(count(*))
            FROM master_orders m
            JOIN (
                SELECT master_order_id,
                       SUM(discount_amount) AS total_sub_disc,
                       SUM(final_amount) AS total_sub_final
                FROM sub_orders
                GROUP BY master_order_id
            ) s ON s.master_order_id = m.id
            JOIN payments p ON p.master_order_id = m.id
            WHERE m.discount_amount <> s.total_sub_disc
               OR m.total_amount <> s.total_sub_final
               OR p.amount <> m.total_amount
               OR (p.status = 'SUCCESS' AND m.status IN ('PAID', 'COMPLETED', 'PARTIALLY_COMPLETED') AND NOT EXISTS (
                   SELECT 1 FROM payments pc WHERE pc.master_order_id = m.id AND pc.is_canonical = true
               ))
        """)
        self.assertEqual(0, mismatched_discounts, "Discount, payment amount, or is_canonical mismatched")

        # 1b. Zero refund state machine mismatches (100% vs partial refunds)
        invalid_refund_states = self.sql("""
            SELECT to_json(count(*))
            FROM (
                SELECT s.id
                FROM sub_orders s
                JOIN master_orders m ON m.id = s.master_order_id
                JOIN bookings b ON b.id = m.booking_id
                JOIN (
                    SELECT r.sub_order_id, r.payment_id, SUM(r.amount) AS refunded_total
                    FROM refunds r WHERE r.status = 'PROCESSED'
                    GROUP BY r.sub_order_id, r.payment_id
                ) ref ON ref.sub_order_id = s.id
                JOIN payments p ON p.id = ref.payment_id
                WHERE (ref.refunded_total >= s.final_amount AND (
                           s.status <> 'REFUNDED' OR p.status <> 'REFUNDED'
                        OR m.payment_status <> 'REFUNDED' OR m.status <> 'CANCELLED' OR b.status <> 'CANCELLED'
                       ))
                   OR (ref.refunded_total < s.final_amount AND (
                           s.status <> 'PARTIALLY_REFUNDED' OR p.status <> 'SUCCESS'
                        OR m.payment_status <> 'PAID' OR m.status = 'CANCELLED' OR b.status <> 'CONFIRMED'
                       ))
            ) bad
        """)
        self.assertEqual(0, invalid_refund_states, "Refund status transitions must match RefundProcessingService")

        # 1c. Voucher per-user quotas, used_count sync, and customer alignment
        invalid_vouchers = self.sql("""
            SELECT to_json(
                (SELECT count(*) FROM discount_codes d
                 LEFT JOIN (SELECT discount_code_id, COUNT(*)::int AS cnt FROM discount_redemptions GROUP BY discount_code_id) rc
                   ON rc.discount_code_id = d.id
                 WHERE d.used_count <> COALESCE(rc.cnt, 0) OR (d.max_uses IS NOT NULL AND d.used_count > d.max_uses))
                +
                (SELECT count(*) FROM (
                    SELECT d.id, r.customer_id
                    FROM discount_codes d
                    JOIN discount_redemptions r ON r.discount_code_id = d.id
                    WHERE d.max_uses_per_user IS NOT NULL
                    GROUP BY d.id, r.customer_id, d.max_uses_per_user
                    HAVING COUNT(*) > d.max_uses_per_user
                ) overages)
                +
                (SELECT count(*) FROM discount_redemptions r JOIN master_orders m ON m.id = r.master_order_id
                 WHERE r.customer_id <> m.customer_id)
            )
        """)
        self.assertEqual(0, invalid_vouchers, "Voucher used_count, per-user limits, or customer links violated")

        mismatched_basis = self.sql("""
            SELECT to_json(count(*))
            FROM sub_orders
            WHERE commission_basis_amount <> (subtotal_amount - COALESCE(vendor_discount_amount, 0))
        """)
        self.assertEqual(0, mismatched_basis, "Commission basis amount mismatched with platform/vendor voucher rules")

        # 2. Zero vendor vouchers applied to orders of another vendor
        invalid_vendor_vouchers = self.sql("""
            SELECT to_json(count(*))
            FROM master_orders m
            JOIN discount_codes d ON d.id = m.discount_code_id
            JOIN sub_orders s ON s.master_order_id = m.id
            WHERE d.scope = 'VENDOR' AND d.vendor_id <> s.vendor_id
        """)
        self.assertEqual(0, invalid_vendor_vouchers, "Vendor voucher applied to different vendor")

        # 3. Zero resolved refund disputes missing matching non-failed, admin-requested, timely refunds
        missing_refunds = self.sql("""
            SELECT to_json(count(*))
            FROM disputes d
            JOIN sub_orders s ON s.id = d.sub_order_id
            WHERE d.status IN ('RESOLVED_REFUND', 'RESOLVED_PARTIAL')
              AND NOT EXISTS (
                  SELECT 1 FROM refunds r
                  WHERE r.sub_order_id = d.sub_order_id
                    AND r.status IN ('PENDING', 'PROCESSED')
                    AND r.refund_percentage = d.refund_percentage
                    AND r.amount = ROUND(s.final_amount * d.refund_percentage / 100.0, 2)
                    AND r.requested_by = d.resolved_by
                    AND r.created_at >= d.resolved_at
                    AND (r.processed_at IS NULL OR r.processed_at >= r.created_at)
              )
        """)
        self.assertEqual(0, missing_refunds, "Resolved dispute missing valid/timely refund record")
        imported_evidence = self.sql("""
            SELECT json_object_agg(id::text, evidence_urls::jsonb) FROM public.disputes
        """)
        disputes = json.loads((ROOT / 'DB_Demo/transactions/disputes.json').read_text())['disputes']
        self.assertEqual({row['id']: json.loads(row['evidence_urls']) for row in disputes}, imported_evidence)

        # 4. Zero reschedule proposals targeting foreign service, terminal order, or non-future slot
        invalid_proposals = self.sql("""
            SELECT to_json(count(*))
            FROM sub_order_reschedule_proposals p
            JOIN sub_orders s ON s.id = p.sub_order_id
            JOIN service_slots sl ON sl.id = p.proposed_slot_id
            WHERE sl.service_id <> s.service_id
               OR s.status IN ('COMPLETED', 'CANCELLED', 'REJECTED')
               OR (p.status = 'PENDING' AND (
                   sl.status <> 'OPEN'
                   OR (sl.date + sl.start_time) AT TIME ZONE 'Asia/Ho_Chi_Minh' <= p.created_at
                   OR (sl.date + sl.start_time) AT TIME ZONE 'Asia/Ho_Chi_Minh' <= CURRENT_TIMESTAMP
               ))
        """)
        self.assertEqual(0, invalid_proposals, "Reschedule proposal foreign service, terminal order, or past slot")

        # 5. Zero disruptions with invalid order status or departure time mismatched from original slot
        invalid_disruptions = self.sql("""
            SELECT to_json(count(*))
            FROM sub_order_disruptions d
            JOIN service_slots s ON s.id = d.original_slot_id
            JOIN sub_orders so ON so.id = d.sub_order_id
            WHERE d.original_departure_at <> (s.date + s.start_time) AT TIME ZONE 'Asia/Ho_Chi_Minh'
               OR (d.status = 'AWAITING_DECISION' AND so.status IN ('COMPLETED', 'CANCELLED', 'REJECTED'))
        """)
        self.assertEqual(0, invalid_disruptions, "Disruption invalid order status or original_departure_at mismatched from slot")

        # 5b. Zero vendor fault compensations attached to non-VENDOR_FAULT disruption
        invalid_compensations = self.sql("""
            SELECT to_json(count(*))
            FROM vendor_fault_compensations c
            JOIN sub_order_disruptions d ON d.id = c.disruption_id
            JOIN sub_orders s ON s.id = c.sub_order_id
            WHERE d.reason <> 'VENDOR_FAULT'
               OR c.sub_order_id <> d.sub_order_id
               OR c.original_slot_id <> d.original_slot_id
               OR c.vendor_id <> s.vendor_id
        """)
        self.assertEqual(0, invalid_compensations, "Vendor fault compensation invalid disruption reason or mismatched keys")

        # 6. Reviews present and zero reviews on incomplete orders or mismatched users/services
        review_count = self.sql("SELECT to_json(count(*)) FROM reviews")
        self.assertGreater(review_count, 0, "Reviews must not be empty")
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

        # 7. Diversity of payout statuses, processed_at completeness, multi-item order & payment retry coverage
        payout_statuses = self.sql("SELECT json_agg(DISTINCT status) FROM payout_requests")
        self.assertIn('PAID', payout_statuses)
        self.assertIn('REJECTED', payout_statuses)
        missing_payout_ts = self.sql("""
            SELECT to_json(count(*)) FROM payout_requests
            WHERE (status IN ('APPROVED', 'PAID', 'REJECTED') AND (processed_by IS NULL OR processed_at IS NULL))
               OR (status = 'REQUESTED' AND processed_at IS NOT NULL)
        """)
        self.assertEqual(0, missing_payout_ts, "Processed payout requests must carry processed_at and processed_by")
        multi_item_orders = self.sql("""
            SELECT to_json(count(*)) FROM (
                SELECT master_order_id FROM sub_orders GROUP BY master_order_id HAVING count(*) > 1
            ) m
        """)
        self.assertGreaterEqual(multi_item_orders, 1, "Expected at least 1 multi-item order")
        retry_orders = self.sql("""
            SELECT to_json(count(*)) FROM (
                SELECT master_order_id FROM payments
                GROUP BY master_order_id
                HAVING bool_or(status = 'FAILED') AND bool_or(status = 'SUCCESS')
            ) r
        """)
        self.assertGreaterEqual(retry_orders, 1, "Expected at least 1 order with FAILED + SUCCESS payment retry")

    def test_12_validator_rejects_mutations_and_sync_preserves_external_data(self):
        """Validator phai chan tat ca ca pha hoai nghiep vu, clock drift, va sync khong duoc sua dich vu ngoai goi demo."""
        self.replace()
        base, trans, _manifest = IMPORTER.load_fixtures()
        fixtures_by_table = {}
        for fname, tables in IMPORTER.EXTENDED_FIXTURE_FILES.items():
            fpath = IMPORTER.TRANS_DIR / fname
            if fpath.is_file():
                data = json.loads(fpath.read_text(encoding='utf8'))
                for t in tables:
                    if t in data:
                        fixtures_by_table[t] = data[t]

        # Test all business & temporal mutation rejections inside rolled-back sessions
        conv01 = next(c for c in fixtures_by_table['conversations'] if c['id'] == '55555555-5555-5555-5555-000000000001')
        other_conv_vendor = next(v['id'] for v in base['vendors'] if v['id'] != conv01['vendor_id'])
        prop01 = next(p for p in fixtures_by_table['sub_order_reschedule_proposals'] if p['id'] == 'fa010000-0000-0000-0000-000000000001')
        other_prop_vendor = next(v['id'] for v in base['vendors'] if v['id'] != prop01['vendor_id'])
        red01 = next(r for r in fixtures_by_table['discount_redemptions'] if r['id'] == '12121212-1212-1212-1212-000000000001')
        other_cust = next(u['id'] for u in base['users'] if u['role'] == 'CUSTOMER' and u['id'] != red01['customer_id'])
        mutations = [
            ("delete_reviews", "DELETE FROM public.reviews"),
            ("fail_dispute_refunds", "UPDATE public.refunds SET status = 'FAILED' WHERE idempotency_key LIKE 'dispute-%'"),
            ("awaiting_on_cancelled", "UPDATE public.sub_orders SET status = 'CANCELLED' WHERE id = '00020000-0000-0000-0000-000000000010'"),
            ("weather_compensation", "UPDATE public.sub_order_disruptions SET reason = 'WEATHER' WHERE id = 'da010000-0000-0000-0000-000000000002'"),
            ("conversation_wrong_vendor", f"UPDATE public.conversations SET vendor_id = '{other_conv_vendor}' WHERE master_order_id IS NOT NULL AND id = '55555555-5555-5555-5555-000000000001'"),
            ("redemption_wrong_customer", f"UPDATE public.discount_redemptions SET customer_id = '{other_cust}' WHERE id = '12121212-1212-1212-1212-000000000001'"),
            ("proposal_wrong_vendor", f"UPDATE public.sub_order_reschedule_proposals SET vendor_id = '{other_prop_vendor}' WHERE id = 'fa010000-0000-0000-0000-000000000001'"),
            ("dispute_wrong_order", "UPDATE public.disputes SET order_id = '00010000-0000-0000-0000-000000000002' WHERE id = '77777777-7777-7777-7777-777777777701'"),
            ("expired_proposal", "UPDATE public.sub_order_reschedule_proposals SET expires_at = CURRENT_TIMESTAMP - interval '1 hour' WHERE id = 'fa010000-0000-0000-0000-000000000001'"),
            ("expired_disruption", "UPDATE public.sub_order_disruptions SET original_departure_at = CURRENT_TIMESTAMP + interval '11 hours', decision_deadline = CURRENT_TIMESTAMP - interval '1 hour' WHERE id = 'da010000-0000-0000-0000-000000000001'"),
            ("compensation_bad_math", "UPDATE public.vendor_fault_compensations SET paid_basis = 650000.00 WHERE disruption_id = 'da010000-0000-0000-0000-000000000002'"),
        ]
        for name, stmt in mutations:
            with self.subTest(mutation=name):
                session = IMPORTER.Session(self.args)
                try:
                    session.send(stmt)
                    with self.assertRaises(Exception, msg=f"Validator failed to reject mutation {name}"):
                        IMPORTER.verify_extended_business_integrity(session, fixtures_by_table)
                finally:
                    session.finish(commit=False)
                    session.close()

        # Verify dynamic reschedule rebasing works cleanly on simulated future dates (2026-11-20 & 2026-11-22)
        vn_tz = timezone(timedelta(hours=7))
        for sim_dt in (datetime(2026, 11, 20, 10, 0, tzinfo=vn_tz), datetime(2026, 11, 22, 10, 0, tzinfo=vn_tz)):
            with self.subTest(simulated_date=sim_dt.date().isoformat()):
                b_copy, t_copy, _ = IMPORTER.load_fixtures()
                IMPORTER.prepare_slots(b_copy, t_copy, {}, today=sim_dt.date())
                with patch.object(IMPORTER, 'db_now_vn', return_value=sim_dt):
                    session = IMPORTER.Session(self.args)
                    try:
                        res = IMPORTER.execute_replace(session, b_copy, t_copy, 'local-acceptance-secret')
                        self.assertEqual('REPLACE_SUCCESS', res['status'])
                        prop_slot = session.query("SELECT json_build_object('prop', p.proposed_slot_id, 'sl_date', sl.date::text, 'exp', p.expires_at > now()) FROM public.sub_order_reschedule_proposals p JOIN public.service_slots sl ON sl.id = p.proposed_slot_id")
                        self.assertEqual((sim_dt.date() + timedelta(days=7)).isoformat(), prop_slot['sl_date'])
                        self.assertTrue(prop_slot['exp'])
                    finally:
                        session.finish(commit=False)
                        session.close()

        # Test that sync/seed_extended_simulation_fixtures never modifies ratings of external services
        ext_vendor = '99999999-0000-0000-0000-000000000001'
        ext_service = '99999999-0000-0000-0000-000000000002'
        ext_user = '99999999-0000-0000-0000-000000000003'
        ext_cust = '99999999-0000-0000-0000-000000000004'
        ext_book = '99999999-0000-0000-0000-000000000005'
        ext_bi = '99999999-0000-0000-0000-000000000006'
        ext_mo = '99999999-0000-0000-0000-000000000007'
        ext_so = '99999999-0000-0000-0000-000000000008'
        ext_slot = '99999999-0000-0000-0000-000000000009'
        ext_rev = '99999999-0000-0000-0000-000000000010'
        ext_cat = base['categories'][0]['id']
        session = IMPORTER.Session(self.args)
        try:
            session.send(f"""
                INSERT INTO public.users (id, email, password_hash, full_name, role, is_locked, is_email_verified, session_version, created_at, updated_at)
                VALUES ('{ext_user}', 'ext.v@danasea.local', 'hash', 'Ext V', 'VENDOR', false, true, 1, now(), now()),
                       ('{ext_cust}', 'ext.c@danasea.local', 'hash', 'Ext C', 'CUSTOMER', false, true, 1, now(), now());
                INSERT INTO public.vendors (id, user_id, business_name, verification_status, rating_avg, rating_count, badge_tier, created_at, updated_at)
                VALUES ('{ext_vendor}', '{ext_user}', 'External Vendor', 'APPROVED', 0.00, 0, 'NONE', now(), now());
                INSERT INTO public.services (id, vendor_id, category_id, name, slug, description, price, status, avg_rating, rating_count, created_at, updated_at)
                VALUES ('{ext_service}', '{ext_vendor}', '{ext_cat}', 'Ext Service', 'ext-service-sync', 'Ext', 100000, 'PUBLISHED', 0.00, 0, now(), now());
                INSERT INTO public.service_slots (id, service_id, date, start_time, end_time, capacity, booked_count, status, inventory_type, created_at, updated_at)
                VALUES ('{ext_slot}', '{ext_service}', '2026-10-20', '08:00:00', '10:00:00', 10, 0, 'OPEN', 'PERSON_LIMIT', now(), now());
                INSERT INTO public.bookings (id, customer_id, status, total_amount, hold_expires_at, created_at, updated_at)
                VALUES ('{ext_book}', '{ext_cust}', 'CONFIRMED', 100000, '2026-10-19T08:15:00+00', now(), now());
                INSERT INTO public.booking_items (id, booking_id, service_id, vendor_id, slot_id, booking_date, booking_time, quantity, price, created_at, updated_at)
                VALUES ('{ext_bi}', '{ext_book}', '{ext_service}', '{ext_vendor}', '{ext_slot}', '2026-10-20', '08:00:00', 1, 100000, now(), now());
                INSERT INTO public.master_orders (id, booking_id, customer_id, status, payment_status, total_amount, discount_amount, created_at, updated_at)
                VALUES ('{ext_mo}', '{ext_book}', '{ext_cust}', 'COMPLETED', 'PAID', 100000, 0, now(), now());
                INSERT INTO public.sub_orders (id, master_order_id, booking_item_id, vendor_id, service_id, slot_id, quantity, unit_price, subtotal_amount, discount_amount, final_amount, commission_basis_amount, commission_rate, commission_amount, vendor_payout_amount, status, created_at, updated_at)
                VALUES ('{ext_so}', '{ext_mo}', '{ext_bi}', '{ext_vendor}', '{ext_service}', '{ext_slot}', 1, 100000, 100000, 0, 100000, 100000, 0.15, 15000, 85000, 'COMPLETED', now(), now());
                INSERT INTO public.reviews (id, service_id, vendor_id, customer_id, sub_order_id, rating, is_visible, created_at, updated_at)
                VALUES ('{ext_rev}', '{ext_service}', '{ext_vendor}', '{ext_cust}', '{ext_so}', 5, true, now(), now());
            """)
            IMPORTER.seed_extended_simulation_fixtures(session, base)
            ext_rating = session.query(f"SELECT to_json(avg_rating) FROM public.services WHERE id = '{ext_service}'")
            self.assertEqual(0.0, float(ext_rating), "Sync must never mutate rating of external services outside demo fixture")
        finally:
            session.finish(commit=False)
            session.close()

    def test_validator_accepts_a_full_refund_for_only_one_item_in_a_paid_order(self):
        self.replace()
        session = IMPORTER.Session(self.args)
        try:
            session.send("""
                WITH refunded_item AS (
                    UPDATE public.sub_orders SET status = 'REFUNDED'
                    WHERE id = '00020000-0000-0000-0000-000000000051'
                    RETURNING id, master_order_id, final_amount
                )
                INSERT INTO public.refunds (
                    id, sub_order_id, payment_id, amount, refund_percentage,
                    provider, reason, status, created_at, updated_at, processed_at
                )
                SELECT 'ffffffff-0000-0000-0000-000000000051', s.id, p.id,
                       s.final_amount, 100, p.provider, 'ADMIN_OVERRIDE', 'PROCESSED',
                       now(), now(), now()
                FROM refunded_item s
                JOIN public.payments p ON p.master_order_id = s.master_order_id AND p.is_canonical
            """)
            self.assertEqual(1, session.query("""
                SELECT to_json(count(*)) FROM public.refunds
                WHERE id = 'ffffffff-0000-0000-0000-000000000051'
            """))
            IMPORTER.verify_extended_business_integrity(session)
        finally:
            session.finish(commit=False)
            session.close()

    def test_13_replace_apply_then_cleanup_round_trip(self):
        """replace --apply -> cleanup --dry-run -> cleanup --apply must succeed cleanly including extended & schedule tables."""
        with tempfile.TemporaryDirectory(prefix='danasea-cli-receipt-') as tmp:
            receipt_path = Path(tmp) / 'cli_receipt.json'
            base_cmd = [
                'python3', str(ROOT / 'DB_Demo/install_demo_data.py'),
                '--docker-container', self.container,
                '--database', 'demo_acceptance',
                '--ownership-file', str(receipt_path),
            ]
            rep = subprocess.run(
                base_cmd + ['--replace', '--apply', '--confirm-backup', '--demo-password-stdin'],
                input='local-acceptance-secret\n', text=True, capture_output=True, check=False
            )
            self.assertEqual(0, rep.returncode, rep.stdout + rep.stderr)
            self.assertEqual('REPLACE_SUCCESS', json.loads(rep.stdout)['status'])
            self.assertTrue(receipt_path.is_file())
            receipt_data = json.loads(receipt_path.read_text(encoding='utf8'))
            self.assertIn('extendedOwned', receipt_data)
            self.assertIn('scheduleOwned', receipt_data)
            self.assertIn('flash_sales', receipt_data['extendedOwned'])

            dry_clean = subprocess.run(
                base_cmd + ['--cleanup', '--dry-run'],
                text=True, capture_output=True, check=False
            )
            self.assertEqual(0, dry_clean.returncode, dry_clean.stdout + dry_clean.stderr)
            self.assertEqual('CLEANUP_SUCCESS', json.loads(dry_clean.stdout)['status'])

            apply_clean = subprocess.run(
                base_cmd + ['--cleanup', '--apply', '--confirm-backup'],
                text=True, capture_output=True, check=False
            )
            self.assertEqual(0, apply_clean.returncode, apply_clean.stdout + apply_clean.stderr)
            self.assertEqual('CLEANUP_SUCCESS', json.loads(apply_clean.stdout)['status'])
            self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM bookings"))
            self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM flash_sales"))
            self.assertEqual(0, self.sql("SELECT to_json(count(*)) FROM reviews"))
            self.assertGreater(self.sql("SELECT to_json(count(*)) FROM system_configs"), 0)

            # Re-seed so test suite leaves a healthy database regardless of test execution order
            self.replace()


if __name__ == '__main__':
    unittest.main()
