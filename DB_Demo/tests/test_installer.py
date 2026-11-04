"""Unit tests for install_demo_data.py contracts, safety guards, and reconciliation logic."""
from pathlib import Path
import copy
import json
import runpy
import tempfile
import unittest
import uuid
from datetime import date
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
TRANS_DIR = ROOT / 'transactions' if (ROOT / 'transactions').is_dir() else ROOT / 'data'
INSTALLER = runpy.run_path(str(ROOT / 'install_demo_data.py'))


class UnifiedInstallerTest(unittest.TestCase):
    def test_credentials_are_required_without_a_source_default(self):
        resolve = INSTALLER.get('resolve_demo_password')
        self.assertTrue(callable(resolve), 'Missing explicit runtime credential provisioning')
        args = INSTALLER['build_parser']().parse_args([])
        with patch.dict('os.environ', {}, clear=True), patch('sys.stdin.isatty', return_value=False), self.assertRaises(ValueError):
            resolve(args)
        with patch.dict('os.environ', {'DEMO_USER_PASSWORD': 'short'}, clear=True), self.assertRaises(ValueError):
            resolve(args)

    def test_unreferenced_slots_are_future_but_history_stays_fixed(self):
        prepare = INSTALLER.get('prepare_slots')
        self.assertTrue(callable(prepare), 'Expired fixture slots need runtime dates')
        base, trans, _ = INSTALLER['load_fixtures']()
        prepare(base, trans, {}, date(2030, 1, 1))
        historic = {i['slot_id'] for i in trans['booking_items']}
        self.assertEqual(3, len(historic))
        self.assertTrue(all(s['date'] == ('2026-10-25' if s['id'] in historic else '2030-01-08') for s in base['service_slots']))

    def test_fresh_import_slots_start_on_or_after_target_date(self):
        prepare = INSTALLER.get('prepare_slots')
        base, trans, _ = INSTALLER['load_fixtures']()
        prepare(base, trans, {}, date(2026, 10, 29))
        historic = {i['slot_id'] for i in trans['booking_items']}
        self.assertTrue(all(s['date'] == '2026-10-25' for s in base['service_slots'] if s['id'] in historic))
        self.assertTrue(all(s['date'] >= '2026-11-20' for s in base['service_slots'] if s['id'] not in historic))

    def test_cleanup_requires_import_ownership(self):
        base, trans, _ = INSTALLER['load_fixtures']()
        with self.assertRaisesRegex(ValueError, 'ownership|receipt'):
            INSTALLER['execute_cleanup'](None, base, trans)

    def test_preflight_rejects_corrupted_booking_and_settlement(self):
        for table, field, value in [('booking_items', 'price', '999.00'), ('refunds', 'payment_id', '00030000-0000-0000-0000-000000000001'), ('settlements', 'net_payable_amount', '9999999.00'), ('master_orders', 'payment_status', 'UNPAID'), ('sub_orders', 'status', 'PENDING'), ('payout_requests', 'vendor_id', '5a53c8e5-3a76-5383-b104-17b22d4af50f')]:
            base, trans, _ = INSTALLER['load_fixtures']()
            trans[table][0][field] = value
            with self.subTest(table=table), self.assertRaises(ValueError):
                INSTALLER['validate_transactions'](base, trans)

    def test_ownership_target_mismatch_is_rejected(self):
        base, trans, _ = INSTALLER['load_fixtures']()
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'receipt.json'
            path.write_text(json.dumps({'version': 1, 'target': {'database': 'real_db', 'endpoint': 'real_host'}}))
            args = INSTALLER['build_parser']().parse_args(['--ownership-file', str(path)])
            with self.assertRaisesRegex(ValueError, 'target'):
                INSTALLER['load_ownership'](args, base, trans)

    def test_load_fixtures_counts(self):
        base_rows, trans_rows, manifest = INSTALLER['load_fixtures']()
        base_count = sum(len(rs) for rs in base_rows.values())
        trans_count = sum(len(rs) for rs in trans_rows.values())
        self.assertEqual(228, base_count, "Expected 228 base records")
        self.assertEqual(42, trans_count, "Expected 42 transaction records")
        self.assertEqual(270, base_count + trans_count, "Expected 270 total demo records")
        self.assertEqual(77, len(manifest), "Expected 77 storage manifest mappings")

    def test_align_schema_defaults_populates_missing_columns(self):
        sample = {
            'services': [{'id': '088d874c-a61e-5b58-a7a9-497f32a56901'}],
            'refunds': [{'id': '00040000-0000-0000-0000-000000000001', 'amount': '100.00'}],
            'settlements': [{'id': '00060000-0000-0000-0000-000000000001', 'gross_amount': '1000.00'}]
        }
        colmap = {
            ('services', 'waiver_required'): {'column_name': 'waiver_required'},
            ('refunds', 'retry_count'): {'column_name': 'retry_count'},
            ('refunds', 'verification_attempts'): {'column_name': 'verification_attempts'},
            ('settlements', 'version'): {'column_name': 'version'}
        }
        INSTALLER['align_schema_defaults'](sample, colmap)
        self.assertFalse(sample['services'][0]['waiver_required'])
        self.assertEqual(1, sample['services'][0]['waiver_version'])
        self.assertEqual(0, sample['refunds'][0]['retry_count'])
        self.assertEqual(0, sample['refunds'][0]['verification_attempts'])
        self.assertEqual(0, sample['settlements'][0]['version'])

    def test_align_schema_defaults_preserves_existing_values(self):
        sample = {
            'refunds': [{'id': '00040000-0000-0000-0000-000000000001', 'retry_count': 5, 'verification_attempts': 2}],
            'settlements': [{'id': '00060000-0000-0000-0000-000000000001', 'version': 3}]
        }
        colmap = {
            ('refunds', 'retry_count'): {'column_name': 'retry_count'},
            ('refunds', 'verification_attempts'): {'column_name': 'verification_attempts'},
            ('settlements', 'version'): {'column_name': 'version'}
        }
        INSTALLER['align_schema_defaults'](sample, colmap)
        self.assertEqual(5, sample['refunds'][0]['retry_count'])
        self.assertEqual(2, sample['refunds'][0]['verification_attempts'])
        self.assertEqual(3, sample['settlements'][0]['version'])

    def test_legacy_cloudinary_url_is_classified_as_reconcilable(self):
        db_val = "https://res.cloudinary.com/dbkxwc5gr/image/upload/v1792170671/danasea_demo_data_package/services/sample.jpg"
        exp_val = "https://res.cloudinary.com/xmxguqpi/image/upload/v1793219667/danasea_demo_data_package/services/sample.jpg"
        self.assertIn(INSTALLER['LEGACY_CLOUD'], db_val)
        self.assertIn(INSTALLER['TARGET_CLOUD'], exp_val)

    def test_reverse_cleanup_dependency_order(self):
        order = INSTALLER['REVERSE_CLEANUP_ORDER']
        self.assertEqual(19, len(order), "Cleanup must cover all 19 entity tables")
        # Children must come before parents
        self.assertLess(order.index('payout_requests'), order.index('settlements'))
        self.assertLess(order.index('settlement_line_items'), order.index('settlements'))
        self.assertLess(order.index('settlement_line_items'), order.index('sub_orders'))
        self.assertLess(order.index('refunds'), order.index('sub_orders'))
        self.assertLess(order.index('payments'), order.index('master_orders'))
        self.assertLess(order.index('sub_orders'), order.index('master_orders'))
        self.assertLess(order.index('sub_orders'), order.index('booking_items'))
        self.assertLess(order.index('booking_items'), order.index('bookings'))
        self.assertLess(order.index('service_safety_documents'), order.index('services'))
        self.assertLess(order.index('service_images'), order.index('services'))
        self.assertLess(order.index('vendor_documents'), order.index('vendors'))
        self.assertLess(order.index('service_slots'), order.index('services'))
        self.assertLess(order.index('service_options'), order.index('services'))
        self.assertLess(order.index('services'), order.index('vendors'))
        self.assertLess(order.index('vendors'), order.index('users'))
        self.assertLess(order.index('services'), order.index('categories'))

    def test_cleanup_preserves_flyway_baseline_categories(self):
        base_rows, _, _ = INSTALLER['load_fixtures']()
        all_category_ids = {c['id'] for c in base_rows['categories']}
        demo_category_ids = [cid for cid in all_category_ids if cid not in INSTALLER['FLYWAY_BASELINE_CATEGORIES']]
        self.assertEqual(12, len(all_category_ids))
        self.assertEqual(7, len(demo_category_ids), "Only the 7 demo categories should be targeted for cleanup")

    def test_verify_backup_validation(self):
        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(b"NOT_A_PGDMP_FILE")
            invalid_path = Path(f.name)
        try:
            with self.assertRaisesRegex(ValueError, "magic header PGDMP mismatch"):
                INSTALLER['verify_backup'](invalid_path)
        finally:
            if invalid_path.exists():
                invalid_path.unlink()

        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(b"PGDMP\x01\x0f\x00\x00\x00\x00\x00\x00\x00\x00\x00")
            valid_path = Path(f.name)
        try:
            with self.assertRaisesRegex(ValueError, 'archive|verify|pg_restore'):
                INSTALLER['verify_backup'](valid_path)
        finally:
            if valid_path.exists():
                valid_path.unlink()

    def test_validate_identity(self):
        for version in ('43', '44'):
            with self.subTest(version=version):
                INSTALLER['validate_identity']({'database': 'danasea', 'schema': 'public',
                                                'failed': 0, 'flyway': version}, 'danasea')

        baseline = {'database': 'danasea', 'schema': 'public', 'failed': 0, 'flyway': '43'}
        for change in ({'database': 'other'}, {'schema': 'private'}, {'failed': 1}, {'flyway': '42'}):
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, 'Database/schema/Flyway mismatch'):
                INSTALLER['validate_identity'](dict(baseline, **change), 'danasea')

    def test_canonical_transactions_have_zero_syntax_issues(self):
        data = json.loads((TRANS_DIR / 'demo_transactions.json').read_text(encoding='utf8'))
        for t in INSTALLER['TRANSACTION_TABLES']:
            for row in data[t]:
                self.assertIn('id', row)
                # Ensure each ID parses as valid UUID
                parsed = uuid.UUID(row['id'])
                self.assertEqual(row['id'].lower(), str(parsed).lower())

    def test_cli_parser_defaults(self):
        parser = INSTALLER['build_parser']()
        args = parser.parse_args([])
        self.assertFalse(args.apply)
        self.assertFalse(args.dry_run)
        self.assertFalse(args.cleanup)
        self.assertEqual('127.0.0.1', args.host)
        self.assertEqual(5432, args.port)
        self.assertEqual('danasea', args.database)
        self.assertEqual('postgres', args.user)


if __name__ == '__main__':
    unittest.main()
