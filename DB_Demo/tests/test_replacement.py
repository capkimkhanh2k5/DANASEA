"""Replacement guards and durable filesystem publication behavior."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('replacement_installer', ROOT / 'install_demo_data.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReplacementContract(unittest.TestCase):
    def test_reviewed_v49_operating_rows_are_replaceable(self):
        reviewed = json.loads((ROOT / 'expected_database_schema.json').read_text())
        for table in ('service_operating_schedules', 'vendor_equipment', 'equipment_maintenance',
                      'vendor_departure_cancellations', 'vendor_fault_compensation_recoveries'):
            self.assertEqual('replace', reviewed['classification'][table])

        references = MODULE.protected_references(reviewed)
        self.assertIn(('vendor_commission_policies', 'vendor_id', 'vendors'), references)
        self.assertNotIn('vendor_fault_compensation_recoveries', {child for child, _, _ in references})

    def test_replace_requires_explicit_transaction_mode(self):
        parser = MODULE.build_parser()
        args = parser.parse_args(['--replace'])
        validate = getattr(MODULE, 'validate_modes', None)
        self.assertTrue(callable(validate), 'Replacement requires explicit mode validation')
        with self.assertRaisesRegex(ValueError, 'dry-run|apply'):
            validate(args)
        for flags in (['--replace', '--cleanup', '--apply'], ['--replace', '--validate-only']):
            with self.subTest(flags=flags), self.assertRaises(ValueError):
                validate(parser.parse_args(flags))

    def test_pending_receipt_is_private_durable_and_never_overwritten(self):
        create = getattr(MODULE, 'write_pending_receipt', None)
        self.assertTrue(callable(create), 'Missing durable receipt journal')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'receipt.json.pending'
            create(path, {'state': 'COMMIT_PENDING'})
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            with self.assertRaises((FileExistsError, ValueError)):
                create(path, {'state': 'other'})
            self.assertIn('COMMIT_PENDING', path.read_text())


if __name__ == '__main__':
    unittest.main()
