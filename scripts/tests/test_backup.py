import importlib.util
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
