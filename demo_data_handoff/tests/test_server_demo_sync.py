"""Focused unit tests for server_demo_sync.py contracts and logic."""
from pathlib import Path
import copy
import json
import runpy
import unittest

ROOT = Path(__file__).resolve().parents[1]
SYNC_MODULE = runpy.run_path(str(ROOT / 'installer/server_demo_sync.py'))


class ServerDemoSyncTest(unittest.TestCase):
    def test_load_fixtures_counts(self):
        base_rows, trans_rows, manifest = SYNC_MODULE['load_fixtures']()
        base_count = sum(len(rs) for rs in base_rows.values())
        trans_count = sum(len(rs) for rs in trans_rows.values())
        self.assertEqual(228, base_count, "Expected 228 base records")
        self.assertEqual(42, trans_count, "Expected 42 transaction records")
        self.assertEqual(270, base_count + trans_count, "Expected 270 total demo records")
        self.assertEqual(77, len(manifest), "Expected 77 storage manifest mappings")

    def test_align_schema_defaults(self):
        sample = {
            'services': [{'id': '088d874c-a61e-5b58-a7a9-497f32a56901'}],
            'refunds': [{'id': '00040000-0000-0000-0000-000000000001'}],
            'settlements': [{'id': '00060000-0000-0000-0000-000000000001'}]
        }
        colmap = {
            ('services', 'waiver_required'): {'column_name': 'waiver_required'},
            ('refunds', 'retry_count'): {'column_name': 'retry_count'},
            ('refunds', 'verification_attempts'): {'column_name': 'verification_attempts'},
            ('settlements', 'version'): {'column_name': 'version'}
        }
        SYNC_MODULE['align_schema_defaults'](sample, colmap)
        self.assertFalse(sample['services'][0]['waiver_required'])
        self.assertEqual(1, sample['services'][0]['waiver_version'])
        self.assertEqual(0, sample['refunds'][0]['retry_count'])
        self.assertEqual(0, sample['refunds'][0]['verification_attempts'])
        self.assertEqual(0, sample['settlements'][0]['version'])

    def test_legacy_cloudinary_url_is_classified_as_reconcilable(self):
        # Media URL pointing to legacy cloud dbkxwc5gr must be reconcilable, not a fatal conflict
        db_val = "https://res.cloudinary.com/dbkxwc5gr/image/upload/v1792170671/danasea_demo_data_package/services/sample.jpg"
        exp_val = "https://res.cloudinary.com/xmxguqpi/image/upload/v1793219667/danasea_demo_data_package/services/sample.jpg"
        self.assertIn(SYNC_MODULE['LEGACY_CLOUD'], db_val)
        self.assertIn(SYNC_MODULE['TARGET_CLOUD'], exp_val)

    def test_reverse_cleanup_dependency_order(self):
        order = SYNC_MODULE['REVERSE_CLEANUP_ORDER']
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
        flyway_baseline = {
            'a0000001-0000-0000-0000-000000000001',
            'a0000001-0000-0000-0000-000000000002',
            'a0000001-0000-0000-0000-000000000003',
            'a0000001-0000-0000-0000-000000000004',
            'a0000001-0000-0000-0000-000000000005'
        }
        base_rows, _, _ = SYNC_MODULE['load_fixtures']()
        all_category_ids = {c['id'] for c in base_rows['categories']}
        demo_category_ids = [cid for cid in all_category_ids if cid not in flyway_baseline]
        self.assertEqual(12, len(all_category_ids))
        self.assertEqual(7, len(demo_category_ids), "Only the 7 demo categories should be targeted for cleanup")


    def test_verify_backup_validation(self):
        import tempfile
        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(b"NOT_A_PGDMP_FILE")
            invalid_path = Path(f.name)
        try:
            with self.assertRaisesRegex(ValueError, "magic header PGDMP mismatch"):
                SYNC_MODULE['verify_backup'](invalid_path)
        finally:
            if invalid_path.exists():
                invalid_path.unlink()

        with tempfile.NamedTemporaryFile(delete=False) as f:
            f.write(b"PGDMP\x01\x0f\x00\x00\x00\x00\x00\x00\x00\x00\x00")
            valid_path = Path(f.name)
        try:
            SYNC_MODULE['verify_backup'](valid_path)
        finally:
            if valid_path.exists():
                valid_path.unlink()


if __name__ == '__main__':
    unittest.main()
