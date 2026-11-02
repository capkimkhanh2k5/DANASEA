import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.rehearse_migration import (
    build_parser,
    main,
    run_rehearsal,
    validate_positive_float,
    validate_positive_int,
)


class MigrationRehearsalTest(unittest.TestCase):
    def test_validate_positive_int(self):
        self.assertEqual(validate_positive_int("100", "rows"), 100)
        self.assertEqual(validate_positive_int(10, "rows"), 10)
        with self.assertRaises(ValueError):
            validate_positive_int("0", "rows")
        with self.assertRaises(ValueError):
            validate_positive_int("-5", "rows")
        with self.assertRaises(ValueError):
            validate_positive_int("abc", "rows")
        with self.assertRaises(ValueError):
            validate_positive_int("12.5", "rows")

    def test_validate_positive_float(self):
        self.assertEqual(validate_positive_float("5.0", "lock"), 5.0)
        self.assertEqual(validate_positive_float("2", "lock"), 2.0)
        self.assertEqual(validate_positive_float(3.5, "lock"), 3.5)
        with self.assertRaises(ValueError):
            validate_positive_float("0", "lock")
        with self.assertRaises(ValueError):
            validate_positive_float("-1.5", "lock")
        with self.assertRaises(ValueError):
            validate_positive_float("foo", "lock")

    @patch("subprocess.run")
    def test_preflight_validation_fails_before_subprocess(self, mock_run):
        # Zero rows
        code = main(["--rows", "0", "--max-lock-seconds", "5", "--max-migration-seconds", "60", "--output", "/tmp/out.json"])
        self.assertEqual(code, 1)
        mock_run.assert_not_called()

        # Negative lock seconds
        code = main(["--rows", "1000", "--max-lock-seconds", "-1", "--max-migration-seconds", "60", "--output", "/tmp/out.json"])
        self.assertEqual(code, 1)
        mock_run.assert_not_called()

        # Negative migration seconds
        code = main(["--rows", "1000", "--max-lock-seconds", "5", "--max-migration-seconds", "0", "--output", "/tmp/out.json"])
        self.assertEqual(code, 1)
        mock_run.assert_not_called()

    @patch("subprocess.run")
    def test_run_rehearsal_passes_exact_jvm_properties(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="BUILD SUCCESS", stderr="")

        with tempfile.TemporaryDirectory() as tmpdir:
            output_file = Path(tmpdir) / "evidence.json"
            # Write valid mock evidence
            output_file.write_text(
                json.dumps(
                    {
                        "passed": True,
                        "numeric_precision": 46,
                        "numeric_scale": 10,
                        "restore_verified": True,
                        "high_precision_verified": True,
                        "actual_lock_seconds": 0.45,
                        "actual_migration_seconds": 2.1,
                    }
                )
            )

            backend_dir = Path(tmpdir) / "backend"
            backend_dir.mkdir()
            (backend_dir / "mvnw").touch()

            evidence = run_rehearsal(
                rows=5000,
                max_lock_seconds=4.0,
                max_migration_seconds=30.0,
                output_path=output_file,
                backend_dir=backend_dir,
            )

            self.assertTrue(evidence["passed"])
            self.assertEqual(evidence["provenance"]["budget_rows"], 5000)
            self.assertEqual(evidence["provenance"]["budget_max_lock_seconds"], 4.0)
            self.assertEqual(evidence["provenance"]["budget_max_migration_seconds"], 30.0)

            # Check subprocess call args
            mock_run.assert_called_once()
            cmd = mock_run.call_args[0][0]
            self.assertIn("-Dtest=PostgreSqlMigrationRehearsalTest", cmd)
            self.assertIn("-Dmigration.rehearsal.rows=5000", cmd)
            self.assertIn("-Dmigration.rehearsal.max-lock-seconds=4.0", cmd)
            self.assertIn("-Dmigration.rehearsal.max-migration-seconds=30.0", cmd)
            self.assertIn(f"-Dmigration.rehearsal.output={output_file.resolve()}", cmd)

    @patch("subprocess.run")
    def test_run_rehearsal_fails_if_lock_budget_exceeded(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="BUILD SUCCESS", stderr="")

        with tempfile.TemporaryDirectory() as tmpdir:
            output_file = Path(tmpdir) / "evidence.json"
            output_file.write_text(
                json.dumps(
                    {
                        "passed": True,
                        "numeric_precision": 46,
                        "numeric_scale": 10,
                        "restore_verified": True,
                        "high_precision_verified": True,
                        "actual_lock_seconds": 6.2,  # exceeds budget 5.0
                        "actual_migration_seconds": 2.1,
                    }
                )
            )

            with self.assertRaises(ValueError) as ctx:
                run_rehearsal(
                    rows=10000,
                    max_lock_seconds=5.0,
                    max_migration_seconds=60.0,
                    output_path=output_file,
                    backend_dir=Path(tmpdir),
                    maven_bin=Path("/bin/true"),
                )
            self.assertIn("exceeded budget", str(ctx.exception))

    @patch("subprocess.run")
    def test_run_rehearsal_fails_if_restore_not_verified(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0, stdout="BUILD SUCCESS", stderr="")

        with tempfile.TemporaryDirectory() as tmpdir:
            output_file = Path(tmpdir) / "evidence.json"
            output_file.write_text(
                json.dumps(
                    {
                        "passed": True,
                        "numeric_precision": 46,
                        "numeric_scale": 10,
                        "restore_verified": False,
                        "high_precision_verified": True,
                        "actual_lock_seconds": 0.5,
                        "actual_migration_seconds": 1.2,
                    }
                )
            )

            with self.assertRaises(ValueError) as ctx:
                run_rehearsal(
                    rows=10000,
                    max_lock_seconds=5.0,
                    max_migration_seconds=60.0,
                    output_path=output_file,
                    backend_dir=Path(tmpdir),
                    maven_bin=Path("/bin/true"),
                )
            self.assertIn("restore verification failed", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
