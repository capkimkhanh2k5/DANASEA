#!/usr/bin/env python3
"""Measure an owned PostgreSQL fixture; require fresh reports and independent restore proof."""

import argparse
import importlib
import json
import math
import subprocess
import sys
import tempfile
import uuid
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))
check_reports = importlib.import_module("scripts.governance.check_test_reports").check
revision = importlib.import_module("scripts.ai.ai_evidence").revision
BACKEND_DIR = REPO_ROOT / "backend"


def get_backend_revision():
    return revision(REPO_ROOT)


def validate_positive_int(value, name):
    try:
        parsed = int(value)
    except (ValueError, TypeError) as error:
        raise ValueError(name + " must be a positive integer") from error
    if parsed <= 0 or str(parsed) != str(value):
        raise ValueError(name + " must be a positive integer")
    return parsed


def validate_positive_float(value, name):
    parsed = float(value)
    if not math.isfinite(parsed) or parsed <= 0:
        raise ValueError(name + " must be finite and positive")
    return parsed


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("rows", "max-lock-seconds", "max-migration-seconds"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--backend-dir", default=BACKEND_DIR, type=Path)
    parser.add_argument("--maven-bin", type=Path)
    return parser


def run_rehearsal(
    rows,
    max_lock_seconds,
    max_migration_seconds,
    output_path,
    backend_dir=BACKEND_DIR,
    maven_bin=None,
):
    rows = validate_positive_int(rows, "rows")
    max_lock_seconds = validate_positive_float(max_lock_seconds, "lock")
    max_migration_seconds = validate_positive_float(max_migration_seconds, "migration")
    backend_dir = backend_dir.resolve()
    output_path = output_path.resolve()
    output_path.unlink(missing_ok=True)
    run_id = str(uuid.uuid4())
    backend_revision = get_backend_revision()
    started = datetime.now(timezone.utc).isoformat()
    with tempfile.TemporaryDirectory(prefix="danasea-migration-evidence-") as temp:
        fresh = Path(temp) / "result.json"
        cmd = [
            str(maven_bin or backend_dir / "mvnw"),
            "-B",
            "clean",
            "test",
            "-Dtest=PostgreSqlMigrationRehearsalTest",
            f"-Dmigration.rehearsal.rows={rows}",
            f"-Dmigration.rehearsal.max-lock-seconds={max_lock_seconds}",
            f"-Dmigration.rehearsal.max-migration-seconds={max_migration_seconds}",
            f"-Dmigration.rehearsal.output={fresh}",
            f"-Dmigration.rehearsal.run-id={run_id}",
        ]
        result = subprocess.run(
            cmd, cwd=backend_dir, capture_output=True, text=True, timeout=900
        )
        if result.returncode:
            raise RuntimeError(
                "Migration test failed: " + (result.stdout + result.stderr)[-2000:]
            )
        if not fresh.is_file():
            raise FileNotFoundError("Fresh migration evidence was not written")
        data = json.loads(fresh.read_text())
        if data.get("run_id") != run_id or data.get("rows") != rows:
            raise ValueError("Migration run/row provenance mismatch")
        cls = "com.danasea.backend.PostgreSqlMigrationRehearsalTest"
        check_reports(
            backend_dir / "target/surefire-reports",
            {cls},
            {
                "exact_cases": True,
                "required_tests": {
                    cls + "#rehearseCoordinateMigrationAndVerifiedRecovery": 1
                },
            },
        )
        for flag in (
            "passed",
            "restore_verified",
            "high_precision_verified",
            "baseline_preserved",
            "corruption_detection_verified",
        ):
            if data.get(flag) is not True:
                raise ValueError("Missing verified migration invariant: " + flag)
        if data.get("numeric_precision") != 46 or data.get("numeric_scale") != 10:
            raise ValueError("V45 precision invariant failed")
        for key, budget in [
            ("actual_lock_seconds", max_lock_seconds),
            ("actual_migration_seconds", max_migration_seconds),
        ]:
            value = data.get(key)
            if (
                type(value) not in (int, float)
                or not math.isfinite(value)
                or not 0 < value <= budget
            ):
                raise ValueError(
                    "Missing/nonfinite measurement or exceeded budget: " + key
                )
        for key in ("reader_queries", "writer_updates", "table_bytes"):
            if type(data.get(key)) is not int or data[key] <= 0:
                raise ValueError("Missing measured contention/data size: " + key)
        for key in ("backup_hash", "restored_data_hash"):
            if not isinstance(data.get(key), str) or len(data[key]) != 64:
                raise ValueError("Missing backup/restored data hash")
        data["release_ready"] = False
        data["provenance"] = {
            "runner": "scripts/ops/rehearse_migration.py",
            "backend_revision": backend_revision,
            "executed_at": started,
            "budget_rows": rows,
            "budget_max_lock_seconds": max_lock_seconds,
            "budget_max_migration_seconds": max_migration_seconds,
            "scope": "owned synthetic database fixture; baseline image startup and staging SLA require separate S2 evidence",
        }
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(data, indent=2) + "\n")
        return data


def main(argv=None):
    args = build_parser().parse_args(argv)
    try:
        result = run_rehearsal(
            validate_positive_int(args.rows, "rows"),
            validate_positive_float(args.max_lock_seconds, "lock"),
            validate_positive_float(args.max_migration_seconds, "migration"),
            args.output,
            args.backend_dir,
            args.maven_bin,
        )
        print(json.dumps(result))
        return 0
    except (ValueError, OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
