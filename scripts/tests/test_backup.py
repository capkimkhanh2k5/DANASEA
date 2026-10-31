import datetime
import importlib.util
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


class BackupTest(unittest.TestCase):
    def module(self):
        spec = importlib.util.spec_from_file_location('backup', Path(__file__).resolve().parents[1] / 'backup.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def seed_archives(self, root, count, prefix='20000101T000000Z'):
        paths = []
        for number in range(count):
            archive = root / f'verified-{prefix}-{number + 1:08x}.dump'
            archive.write_bytes(b'previous verified archive')
            Path(str(archive) + '.json').write_text(json.dumps({'restore_verified': True}))
            os.utime(archive, (100 + number, 100 + number))
            paths.append(archive)
        return paths

    def dump(self, args, **kwargs):
        if 'pg_dump' in ' '.join(args):
            kwargs['stdout'].write(b'PGDMP' + b'x' * 256)
        return b''

    def test_one_predeploy_backup_keeps_only_seven_by_default_and_preserves_unrelated_files(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = self.seed_archives(root, 8)
            unrelated = root / 'operator-notes.txt'
            unrelated.write_text('keep this')
            with patch.object(module, 'run', side_effect=self.dump), patch.object(module, 'verify_restore'):
                module.backup({'directory': str(root)})
            archives = set(root.glob('verified-*.dump'))
            self.assertEqual(len(archives), 7)
            self.assertEqual(len(archives - set(old)), 1)
            self.assertEqual(set(old) & archives, set(old[2:]))
            for removed in old[:2]:
                self.assertFalse(Path(str(removed) + '.json').exists())
            self.assertEqual(unrelated.read_text(), 'keep this')

    def test_legacy_larger_retention_cannot_exceed_seven_archives(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.seed_archives(root, 8)
            with patch.object(module, 'run', side_effect=self.dump), patch.object(module, 'verify_restore'):
                module.backup({'directory': str(root), 'keep': 28})
            self.assertEqual(len(list(root.glob('verified-*.dump'))), 7)
            self.assertEqual(len(list(root.glob('verified-*.dump.json'))), 7)

    def test_single_archive_retention_keeps_the_new_verified_snapshot(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = self.seed_archives(root, 2)
            with patch.object(module, 'run', side_effect=self.dump), patch.object(module, 'verify_restore'):
                module.backup({'directory': str(root), 'keep': 1})
            archives = list(root.glob('verified-*.dump'))
            self.assertEqual(len(archives), 1)
            self.assertNotIn(archives[0], old)

    def test_latest_snapshot_is_retained_when_multiple_backups_share_a_second(self):
        module = self.module()
        instant = datetime.datetime(2026, 10, 28, tzinfo=datetime.timezone.utc)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = self.seed_archives(root, 7, '20261028T000000Z')
            with patch.object(module, 'run', side_effect=self.dump), patch.object(module, 'verify_restore'), \
                    patch.object(module.datetime, 'datetime') as clock, \
                    patch.object(module.secrets, 'token_hex', return_value='00000000'):
                clock.now.return_value = instant
                module.backup({'directory': str(root), 'keep': 7})
            self.assertTrue((root / 'verified-20261028T000000Z-00000000.dump').exists())
            self.assertFalse(old[0].exists())
            self.assertEqual(len(list(root.glob('verified-*.dump'))), 7)

    def test_failed_verification_does_not_rotate_previous_verified_backups(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = set(self.seed_archives(root, 7))
            with patch.object(module, 'run', side_effect=self.dump), \
                    patch.object(module, 'verify_restore', side_effect=ValueError('restore failed')):
                with self.assertRaises(ValueError):
                    module.backup({'directory': str(root)})
            self.assertEqual(set(root.glob('verified-*.dump')), old)

    def test_first_hardened_backup_rotates_legacy_predeploy_archives_within_the_same_limit(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            legacy = []
            for number in range(7):
                archive = root / f'pre-deadbee-200001010000{number:02}.sql.gz'
                archive.write_bytes(b'legacy snapshot')
                os.utime(archive, (100 + number, 100 + number))
                legacy.append(archive)
            unrelated = root / 'pre-operator-notes.sql.gz'
            unrelated.write_bytes(b'preserve unrelated file')
            os.utime(unrelated, (1, 1))
            with patch.object(module, 'run', side_effect=self.dump), patch.object(module, 'verify_restore'):
                module.backup({'directory': str(root)})
            retained = [path for path in legacy if path.exists()]
            self.assertEqual(len(retained) + len(list(root.glob('verified-*.dump'))), 7)
            self.assertEqual(retained, legacy[1:])
            self.assertEqual(unrelated.read_bytes(), b'preserve unrelated file')

    def test_restore_rejects_live_or_invalid_database_before_any_docker_call(self):
        module = self.module()
        with patch.object(module, 'run') as run:
            for name in ['danasea', 'postgres', 'danasea_restore_unsafe;DROP DATABASE postgres']:
                with self.assertRaises(ValueError):
                    module.restore(Path('/missing'), 'db', name)
            run.assert_not_called()

    def test_failed_restore_always_drops_only_temporary_verification_database(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'backup.dump'
            archive.write_bytes(b'not a PostgreSQL archive')
            calls = []
            def run(args, **kwargs):
                calls.append(args)
                if 'pg_restore' in ' '.join(args):
                    raise subprocess.CalledProcessError(1, args)
                return b''
            with patch.object(module, 'run', side_effect=run), self.assertRaises(subprocess.CalledProcessError):
                module.verify_restore(archive, 'danasea-postgres')
            self.assertIn('dropdb', ' '.join(calls[-1]))
            self.assertIn('danasea_restore_', ' '.join(calls[-1]))

    def test_local_verified_backup_needs_no_remote_or_encryption_tools(self):
        module = self.module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def run(args, **kwargs):
                if 'pg_dump' in ' '.join(args):
                    kwargs['stdout'].write(b'PGDMP' + b'x' * 256)
                return b''
            with patch.object(module, 'run', side_effect=run), patch.object(module, 'verify_restore'):
                module.backup({'directory': str(root), 'keep': 2})
            archives = list(root.glob('verified-*.dump'))
            self.assertEqual(len(archives), 1)
            self.assertTrue(archives[0].read_bytes().startswith(b'PGDMP'))
            self.assertEqual(archives[0].stat().st_mode & 0o777, 0o600)


if __name__ == '__main__':
    unittest.main()
