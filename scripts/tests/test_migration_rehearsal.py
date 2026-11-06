import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from scripts.rehearse_migration import (
    main,
    run_rehearsal,
    validate_positive_float,
    validate_positive_int,
)


class MigrationRehearsalTest(unittest.TestCase):
    def test_preflight_rejects_invalid_and_nonfinite_budget_before_execution(self):
        for rows, lock, migration in [
            ("0", "5", "60"),
            ("10", "nan", "60"),
            ("10", "5", "inf"),
            ("-1", "5", "60"),
        ]:
            with (
                self.subTest(values=(rows, lock, migration)),
                patch("subprocess.run") as process,
            ):
                self.assertEqual(
                    main(
                        [
                            "--rows",
                            rows,
                            "--max-lock-seconds",
                            lock,
                            "--max-migration-seconds",
                            migration,
                            "--output",
                            "/tmp/unused-migration.json",
                        ]
                    ),
                    1,
                )
                process.assert_not_called()
        for value in ("0", "-1", "abc", "1.5"):
            with self.assertRaises(ValueError):
                validate_positive_int(value, "rows")
        for value in ("0", "-1", "nan", "inf"):
            with self.assertRaises(ValueError):
                validate_positive_float(value, "lock")

    def exercise(self, changes=None, skip=False):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)

            def maven(command, **kwargs):
                self.assertIn("clean", command)
                props = dict(
                    arg[2:].split("=", 1) for arg in command if arg.startswith("-D")
                )
                data = {
                    "run_id": props["migration.rehearsal.run-id"],
                    "rows": 10000,
                    "passed": True,
                    "numeric_precision": 46,
                    "numeric_scale": 10,
                    "restore_verified": True,
                    "baseline_preserved": True,
                    "high_precision_verified": True,
                    "corruption_detection_verified": True,
                    "actual_lock_seconds": 0.1,
                    "actual_migration_seconds": 0.5,
                    "reader_queries": 1,
                    "writer_updates": 1,
                    "table_bytes": 10000,
                    "backup_hash": "a" * 64,
                    "restored_data_hash": "b" * 64,
                }
                data.update(changes or {})
                Path(props["migration.rehearsal.output"]).write_text(json.dumps(data))
                reports = root / "target/surefire-reports"
                reports.mkdir(parents=True)
                cls = "com.danasea.backend.PostgreSqlMigrationRehearsalTest"
                (reports / "TEST-migration.xml").write_text(
                    '<testsuite><testcase classname="'
                    + cls
                    + '" name="rehearseCoordinateMigrationAndVerifiedRecovery">'
                    + ("<skipped/>" if skip else "")
                    + "</testcase></testsuite>"
                )
                return SimpleNamespace(returncode=0, stdout="", stderr="")

            with (
                patch(
                    "scripts.rehearse_migration.get_backend_revision",
                    return_value="1" * 40,
                ),
                patch("subprocess.run", side_effect=maven),
            ):
                return run_rehearsal(10000, 5, 60, root / "out.json", root)

    def test_real_report_checker_accepts_fresh_matching_evidence(self):
        result = self.exercise()
        self.assertFalse(result["release_ready"])
        self.assertEqual(result["provenance"]["budget_rows"], 10000)

    def test_skip_wrong_nonce_rows_and_unmeasured_contention_fail(self):
        for changes in (
            {"run_id": "old"},
            {"rows": 1},
            {"reader_queries": 0},
            {"actual_lock_seconds": None},
            {"actual_migration_seconds": float("nan")},
            {"restore_verified": False},
            {"corruption_detection_verified": False},
        ):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.exercise(changes)
        with self.assertRaisesRegex(ValueError, "skip"):
            self.exercise(skip=True)

    def test_old_artifact_cannot_pass_without_new_execution(self):
        with (
            tempfile.TemporaryDirectory() as temp,
            patch(
                "scripts.rehearse_migration.get_backend_revision", return_value="1" * 40
            ),
        ):
            root = Path(temp)
            output = root / "old.json"
            output.write_text('{"passed":true}')
            with self.assertRaises(FileNotFoundError):
                run_rehearsal(10000, 5, 60, output, root, Path("/usr/bin/true"))
            self.assertFalse(output.exists())
