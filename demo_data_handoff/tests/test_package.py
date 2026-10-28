"""Offline fixture contract and negative safety checks; no mocks of PostgreSQL."""
from pathlib import Path
import copy
import json
import runpy
import contextlib
import io
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE_DIR = ROOT / 'base' if (ROOT / 'base').is_dir() else ROOT / 'data'
INSTALLER = runpy.run_path(str(ROOT / 'installer/install_demo_data.py'))


class PackageContract(unittest.TestCase):
    def test_current_schema_identity_is_accepted(self):
        for version in ('41', '42', '43'):
            with self.subTest(version=version):
                INSTALLER['validate_identity']({'database': 'danasea_demo_audit', 'schema': 'public',
                                                'failed': 0, 'flyway': version}, 'danasea_demo_audit')

    def test_unknown_schema_or_foreign_database_is_rejected(self):
        baseline = {'database': 'danasea_demo_audit', 'schema': 'public', 'failed': 0, 'flyway': '43'}
        for change in ({'database': 'danasea'}, {'schema': 'private'}, {'failed': 1}, {'flyway': '44'}):
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, 'Database/schema/Flyway mismatch'):
                INSTALLER['validate_identity'](dict(baseline, **change), 'danasea_demo_audit')

    def test_dry_run_rejects_upload_media_side_effect(self):
        from types import SimpleNamespace
        args = SimpleNamespace(dry_run=True, upload_media=True, reuse_storage=False)
        with self.assertRaisesRegex(ValueError, 'Dry-run cannot upload media to Cloudinary'):
            INSTALLER['upload_media']([], args)

    def test_reviewed_package_matches_manifest(self):
        INSTALLER['manifest']()

    def rows(self):
        return {p.stem: [r['sqlRow'] for r in json.loads(p.read_text(encoding='utf8'))['records']]
                for p in BASE_DIR.glob('*.json')
                if isinstance(json.loads(p.read_text(encoding='utf8')), dict)
                and isinstance(json.loads(p.read_text(encoding='utf8')).get('records'), list)
                and json.loads(p.read_text(encoding='utf8'))['records']
                and 'sqlRow' in json.loads(p.read_text(encoding='utf8'))['records'][0]}

    def validate(self, rows):
        self.assertTrue(callable(INSTALLER.get('validate_rows')), 'Extended fixture validator is missing')
        return INSTALLER['validate_rows'](rows)

    def test_safety_documents_in_same_atomic_install(self):
        self.assertIn('service_safety_documents', INSTALLER['TABLES'])

    def test_required_services_have_own_pending_samples(self):
        rows = self.rows()
        self.assertTrue('service_safety_documents' in rows, 'ServiceSafetyDocument dataset missing')
        categories = {c['id']: c for c in rows['categories']}
        needed = {s['id'] for s in rows['services'] if s['weather_sensitive'] or categories[s['category_id']]['requires_safety_cert']}
        docs = rows['service_safety_documents']
        self.assertEqual(24, len(needed))
        self.assertEqual(needed, {d['service_id'] for d in docs})
        self.assertEqual(len(needed), len(docs))
        self.assertTrue(all(d['status'] == 'PENDING' and d['reviewed_by'] is None and d['reviewed_at'] is None for d in docs))
        self.validate(rows)

    def test_missing_required_safety_document_rejected(self):
        rows = self.rows()
        rows['service_safety_documents'].pop()
        with self.assertRaisesRegex(ValueError, 'safety|Safety|counts'):
            self.validate(rows)

    def test_foreign_service_mapping_rejected(self):
        rows = self.rows()
        rows['service_safety_documents'][0]['service_id'] = rows['vendors'][0]['id']
        with self.assertRaises(ValueError):
            self.validate(rows)

    def test_fake_approval_rejected(self):
        rows = self.rows()
        rows['service_safety_documents'][0]['status'] = 'APPROVED'
        with self.assertRaises(ValueError):
            self.validate(rows)

    def test_duplicate_uuid_rejected(self):
        rows = self.rows()
        rows['service_safety_documents'][0]['id'] = rows['vendor_documents'][0]['id']
        with self.assertRaises(ValueError):
            self.validate(rows)

    def test_bank_leading_zero_and_role_mapping_preserved(self):
        rows = self.rows()
        users = {u['email']: u for u in rows['users']}
        banks = {v['user_id']: v for v in rows['vendors']}
        self.assertEqual('00700797976', banks[users['vender_7@danasea.test']['id']]['bank_account_number'])
        self.assertEqual('00700648686', banks[users['vender_8@danasea.test']['id']]['bank_account_number'])
        self.assertEqual('ADMIN', users['admin_demo@danasea.test']['role'])
        self.assertEqual(2, sum(u['role'] == 'CUSTOMER' for u in users.values()))
        self.assertTrue(all(u['password_hash'] is None for u in users.values()))

    def test_source_development_database_not_install_target(self):
        from types import SimpleNamespace
        args = SimpleNamespace(development=True, database='danasea', docker_container='pbl6_httt-postgres-1',
                               allow_source_development=True, apply=False, host='127.0.0.1')
        with self.assertRaisesRegex(ValueError, 'isolated|shared|main|Source'):
            INSTALLER['target_guard'](args)


if __name__ == '__main__':
    unittest.main()
