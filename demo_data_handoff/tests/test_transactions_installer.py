"""Unit tests for install_demo_transactions.py offline safety contracts."""
from pathlib import Path
import json
import runpy
import unittest
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
TRANS_DIR = ROOT / 'transactions' if (ROOT / 'transactions').is_dir() else ROOT / 'data'
INSTALLER = runpy.run_path(str(ROOT / 'installer/install_demo_transactions.py'))


class TransactionsInstallerContract(unittest.TestCase):
    def test_identity_check(self):
        for version in ('24', '37', '41', '42', '43'):
            with self.subTest(version=version):
                INSTALLER['validate_identity']({'database': 'danasea_demo_local', 'schema': 'public',
                                                'failed': 0, 'flyway': version}, 'danasea_demo_local')

    def test_identity_rejection(self):
        baseline = {'database': 'danasea_demo_local', 'schema': 'public', 'failed': 0, 'flyway': '43'}
        for mut in ({'database': 'danasea'}, {'schema': 'custom'}, {'failed': 1}, {'flyway': '45'}):
            with self.subTest(mut=mut), self.assertRaises(ValueError):
                INSTALLER['validate_identity'](dict(baseline, **mut), 'danasea_demo_local')

    def test_target_guard_protects_production_and_shared(self):
        # Missing --development
        args = SimpleNamespace(development=False, database='danasea_demo_test', docker_container=None, host='127.0.0.1')
        with self.assertRaisesRegex(ValueError, 'Explicit --development is required'):
            INSTALLER['target_guard'](args)

        # Database name doesn't start with danasea_demo_
        args = SimpleNamespace(development=True, database='production', docker_container=None, host='127.0.0.1')
        with self.assertRaisesRegex(ValueError, 'Only isolated DEVELOPMENT targets allowed'):
            INSTALLER['target_guard'](args)

        # Shared container forbidden
        args = SimpleNamespace(development=True, database='danasea_demo_test', docker_container='pbl6_httt-postgres-1', host='127.0.0.1')
        with self.assertRaisesRegex(ValueError, 'Only isolated DEVELOPMENT targets allowed'):
            INSTALLER['target_guard'](args)

    def test_canonical_transactions_have_zero_syntax_issues(self):
        data = json.loads((TRANS_DIR / 'demo_transactions.json').read_text(encoding='utf8'))
        issues = INSTALLER['get_syntax_audit'](data)
        self.assertEqual(0, len(issues), 'Canonical handoff transactions must have zero non-hex UUIDs')

    def test_uuid_normalization_handles_legacy_prefixes(self):
        # Simulate legacy non-hex prefixes
        legacy_sample = {'payments': [{'id': 'p0010000-0000-0000-0000-000000000001', 'master_order_id': '00010000-0000-0000-0000-000000000001'}],
                         'bookings': [], 'booking_items': [], 'master_orders': [], 'sub_orders': [],
                         'refunds': [], 'invoices': [], 'settlements': [], 'settlement_line_items': [], 'payout_requests': []}
        issues = INSTALLER['get_syntax_audit'](legacy_sample)
        self.assertEqual(1, len(issues))
        normalized = INSTALLER['apply_uuid_normalization'](legacy_sample)
        self.assertEqual(0, len(INSTALLER['get_syntax_audit'](normalized)))
        self.assertTrue(normalized['payments'][0]['id'].startswith('0003'))

    def test_align_defaults_populates_missing_columns(self):
        sample_rows = {
            'refunds': [{'id': '00040000-0000-0000-0000-000000000001', 'amount': '100.00'}],
            'settlements': [{'id': '00060000-0000-0000-0000-000000000001', 'gross_amount': '1000.00'}]
        }
        colmap = {
            ('refunds', 'retry_count'): {'column_name': 'retry_count'},
            ('refunds', 'verification_attempts'): {'column_name': 'verification_attempts'},
            ('settlements', 'version'): {'column_name': 'version'}
        }
        INSTALLER['align_defaults'](sample_rows, colmap)
        self.assertEqual(0, sample_rows['refunds'][0]['retry_count'])
        self.assertEqual(0, sample_rows['refunds'][0]['verification_attempts'])
        self.assertEqual(0, sample_rows['settlements'][0]['version'])

    def test_align_defaults_preserves_existing_values(self):
        sample_rows = {
            'refunds': [{'id': '00040000-0000-0000-0000-000000000001', 'retry_count': 5, 'verification_attempts': 2}],
            'settlements': [{'id': '00060000-0000-0000-0000-000000000001', 'version': 3}]
        }
        colmap = {
            ('refunds', 'retry_count'): {'column_name': 'retry_count'},
            ('refunds', 'verification_attempts'): {'column_name': 'verification_attempts'},
            ('settlements', 'version'): {'column_name': 'version'}
        }
        INSTALLER['align_defaults'](sample_rows, colmap)
        self.assertEqual(5, sample_rows['refunds'][0]['retry_count'])
        self.assertEqual(2, sample_rows['refunds'][0]['verification_attempts'])
        self.assertEqual(3, sample_rows['settlements'][0]['version'])

    def test_align_defaults_noops_when_columns_absent(self):
        sample_rows = {
            'refunds': [{'id': '00040000-0000-0000-0000-000000000001', 'amount': '100.00'}],
            'settlements': [{'id': '00060000-0000-0000-0000-000000000001', 'gross_amount': '1000.00'}]
        }
        colmap = {}
        INSTALLER['align_defaults'](sample_rows, colmap)
        self.assertNotIn('retry_count', sample_rows['refunds'][0])
        self.assertNotIn('verification_attempts', sample_rows['refunds'][0])
        self.assertNotIn('version', sample_rows['settlements'][0])


if __name__ == '__main__':
    unittest.main()
