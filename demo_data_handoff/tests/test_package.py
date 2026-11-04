"""Offline fixture contract and integrity checks for DANASEA demo data package."""
from pathlib import Path
import copy
import json
import runpy
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE_DIR = ROOT / 'base' if (ROOT / 'base').is_dir() else ROOT / 'data'
INSTALLER = runpy.run_path(str(ROOT / 'install_demo_data.py'))


class PackageContract(unittest.TestCase):
    def test_current_schema_identity_is_accepted(self):
        for version in ('43', '44'):
            with self.subTest(version=version):
                INSTALLER['validate_identity']({'database': 'danasea_demo_audit', 'schema': 'public',
                                                'failed': 0, 'flyway': version}, 'danasea_demo_audit')

    def test_unknown_schema_or_foreign_database_is_rejected(self):
        baseline = {'database': 'danasea_demo_audit', 'schema': 'public', 'failed': 0, 'flyway': '43'}
        for change in ({'database': 'danasea_other'}, {'schema': 'private'}, {'failed': 1}, {'flyway': '42'}):
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, 'Database/schema/Flyway mismatch'):
                INSTALLER['validate_identity'](dict(baseline, **change), 'danasea_demo_audit')

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
        self.assertIn('service_safety_documents', INSTALLER['BASE_TABLES'])

    def test_required_services_have_own_safety_documents(self):
        rows = self.rows()
        self.assertTrue('service_safety_documents' in rows, 'ServiceSafetyDocument dataset missing')
        categories = {c['id']: c for c in rows['categories']}
        needed = {s['id'] for s in rows['services'] if s['weather_sensitive'] or categories[s['category_id']]['requires_safety_cert']}
        docs = rows['service_safety_documents']
        self.assertEqual(24, len(needed))
        self.assertEqual(needed, {d['service_id'] for d in docs})
        self.assertEqual(len(needed), len(docs))
        self.assertTrue(all(d['status'] in ('PENDING', 'APPROVED') and (d['status'] != 'APPROVED' or (d['reviewed_by'] and d['reviewed_at'])) for d in docs))
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
        rows['service_safety_documents'][0]['reviewed_by'] = None
        with self.assertRaises(ValueError):
            self.validate(rows)

    def test_unauthorized_vendor_verifier_rejected(self):
        rows = self.rows()
        users = {u['role']: u['id'] for u in rows['users']}
        # Non-admin roles must be rejected
        for invalid_id in (users['CUSTOMER'], users['VENDOR'], '00000000-0000-0000-0000-000000000000'):
            r = self.rows()
            r['vendors'][0]['verified_by'] = invalid_id
            with self.subTest(invalid_id=invalid_id), self.assertRaises(ValueError):
                self.validate(r)

    def test_unauthorized_document_reviewer_rejected(self):
        rows = self.rows()
        users = {u['role']: u['id'] for u in rows['users']}
        for invalid_id in (users['CUSTOMER'], users['VENDOR'], '00000000-0000-0000-0000-000000000000'):
            r1 = self.rows()
            r1['vendor_documents'][0]['reviewed_by'] = invalid_id
            with self.subTest(table='vendor_documents', invalid_id=invalid_id), self.assertRaises(ValueError):
                self.validate(r1)

            r2 = self.rows()
            r2['service_safety_documents'][0]['reviewed_by'] = invalid_id
            with self.subTest(table='service_safety_documents', invalid_id=invalid_id), self.assertRaises(ValueError):
                self.validate(r2)

    def test_invalid_approval_timestamps_rejected(self):
        # 1. Vendor created_at > verified_at
        r = self.rows()
        r['vendors'][0]['created_at'] = '2026-10-25T10:00:00+00:00'
        with self.assertRaises(ValueError):
            self.validate(r)

        # 2. Vendor verified_at > updated_at
        r = self.rows()
        r['vendors'][0]['updated_at'] = '2026-10-18T10:00:00+00:00'
        with self.assertRaises(ValueError):
            self.validate(r)

        # 3. Safety document created_at > reviewed_at
        r = self.rows()
        r['service_safety_documents'][0]['created_at'] = '2026-10-25T10:00:00+00:00'
        with self.assertRaises(ValueError):
            self.validate(r)

        # 4. Safety document reviewed_at > updated_at
        r = self.rows()
        r['service_safety_documents'][0]['updated_at'] = '2026-10-18T10:00:00+00:00'
        with self.assertRaises(ValueError):
            self.validate(r)

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



    def test_vendor_tax_codes_authentic_or_none(self):
        rows = self.rows()
        for v in rows['vendors']:
            vid = v['id']
            tc = v.get('tax_code')
            if vid == 'baea9605-392b-5003-93f1-60f2c0582f65':
                self.assertEqual('0402177261', tc, 'Kajin Vina must have authentic corporate tax code')
            elif vid == '128672c5-94e7-5560-896c-158794913b8c':
                self.assertEqual('0401802233', tc, 'D BLUE must have authentic corporate tax code')
            else:
                self.assertIsNotNone(tc, f"Vendor {vid} ({v.get('business_name')}) must have valid demo tax_code")
            if tc is not None:
                self.assertFalse(tc.startswith('TEST-'), 'TEST- tax code strings are prohibited')

    def test_users_have_valid_phones(self):
        rows = self.rows()
        for u in rows['users']:
            phone = u.get('phone')
            self.assertIsNotNone(phone, f"User {u['email']} must have a valid phone number")
            self.assertTrue(phone.startswith(('03', '05', '07', '08', '09')), f"User {u['email']} phone must be standard VN prefix 03/05/07/08/09...")
            self.assertEqual(10, len(phone), f"User {u['email']} phone must be exactly 10 digits")

    def test_service_slots_have_active_future_dates_and_realistic_capacities(self):
        rows = self.rows()
        start_times = set()
        historic_ids = {'5e068a90-a8d3-5194-b2f6-d37dca987121', 'ae67fe13-018f-5fae-b607-ceb243fb9f5d', 'a759a4df-553c-551d-a93f-648ac467444c',
                        '38f2aed5-9335-5d28-92fe-dc863243c79e', '86bd99b8-2040-56db-aa9b-ab3da84d94e0', 'a593f000-eaf6-5927-aee7-d4746e892837',
                        '1ae4ed72-991c-54c4-a040-1fc1f2dcf06a', 'ace7c208-edec-5b15-8702-3d8dbef22db0', 'e90aaad4-0b3b-5eb3-96d2-f3e041eb49e5'}
        for s in rows['service_slots']:
            if s['id'] in historic_ids:
                self.assertEqual('2026-10-25', s['date'], 'Historic slots must have fixed 2026-10-25 date')
            else:
                self.assertGreaterEqual(s['date'], '2026-11-20', 'Unreferenced slots must have active future date')
            self.assertGreaterEqual(s['capacity'], 6, 'Slot capacity must be realistic (>= 6)')
            start_times.add(s['start_time'])
        self.assertGreaterEqual(len(start_times), 5, 'Operating hours must be diversified across activities')


if __name__ == '__main__':
    unittest.main()
