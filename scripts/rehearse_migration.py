#!/usr/bin/env python3
"""Runner for PostgreSQL migration V45 benchmark rehearsal and recovery verification.

Validates that schema migration V44->V45 preserves coordinates precision,
holds ACCESS EXCLUSIVE lock within maintenance budget under concurrent load,
and proves backup restore onto an isolated PostgreSQL database.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_DIR = REPO_ROOT / "backend"


def get_backend_revision() -> str:
    try:
        git_dir = REPO_ROOT / ".git"
        if git_dir.is_dir():
            head = (git_dir / "HEAD").read_text(encoding="utf-8").strip()
            if head.startswith("ref: "):
                ref_subpath = head[5:].strip()
                ref_path = git_dir / ref_subpath
                if ref_path.is_file():
                    return ref_path.read_text(encoding="utf-8").strip()
                packed_refs = git_dir / "packed-refs"
                if packed_refs.is_file():
                    for line in packed_refs.read_text(encoding="utf-8").splitlines():
                        parts = line.strip().split()
                        if len(parts) == 2 and parts[1] == ref_subpath:
                            return parts[0]
            elif len(head) == 40:
                return head
    except Exception:
        pass
    try:
        res = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
            check=False,
        )
        if res.returncode == 0 and res.stdout.strip():
            return res.stdout.strip()
    except Exception:
        pass
    return "UNKNOWN"


def validate_positive_int(value: str | int, name: str) -> int:
    try:
        val = int(value)
    except (ValueError, TypeError):
        raise ValueError(f"Argument '{name}' must be an integer, got: {value}")
    if val <= 0:
        raise ValueError(f"Argument '{name}' must be strictly positive (> 0), got: {val}")
    return val


def validate_positive_float(value: str | float | int, name: str) -> float:
    try:
        val = float(value)
    except (ValueError, TypeError):
        raise ValueError(f"Argument '{name}' must be a number, got: {value}")
    if val <= 0.0:
        raise ValueError(f"Argument '{name}' must be strictly positive (> 0), got: {val}")
    return val


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Benchmark PostgreSQL migration V45 under concurrent load with recovery proof."
    )
    parser.add_argument(
        "--rows",
        required=True,
        help="Number of service rows to benchmark in target V44 schema (must be positive integer).",
    )
    parser.add_argument(
        "--max-lock-seconds",
        required=True,
        help="Maximum allowed lock wait budget in seconds (must be positive number).",
    )
    parser.add_argument(
        "--max-migration-seconds",
        required=True,
        help="Maximum allowed migration duration budget in seconds (must be positive number).",
    )
    parser.add_argument(
        "--output",
        required=True,
        type=Path,
        help="Path where verification evidence JSON will be recorded.",
    )
    parser.add_argument(
        "--backend-dir",
        type=Path,
        default=BACKEND_DIR,
        help="Path to backend directory containing Maven wrapper.",
    )
    parser.add_argument(
        "--maven-bin",
        type=Path,
        help="Custom path to mvn or ./mvnw.",
    )
    return parser


def run_rehearsal(
    rows: int,
    max_lock_seconds: float,
    max_migration_seconds: float,
    output_path: Path,
    backend_dir: Path = BACKEND_DIR,
    maven_bin: Path | None = None,
) -> dict:
    if rows <= 0:
        raise ValueError(f"Rows must be > 0, got {rows}")
    if max_lock_seconds <= 0:
        raise ValueError(f"Max lock seconds must be > 0, got {max_lock_seconds}")
    if max_migration_seconds <= 0:
        raise ValueError(f"Max migration seconds must be > 0, got {max_migration_seconds}")

    output_path = output_path.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    if maven_bin is None:
        if (backend_dir / "mvnw").exists():
            maven_bin = backend_dir / "mvnw"
        else:
            maven_bin = Path("mvn")

    revision = get_backend_revision()
    started_at = datetime.now(timezone.utc).isoformat()

    cmd = [
        str(maven_bin),
        "-B",
        "test",
        "-Dtest=PostgreSqlMigrationRehearsalTest",
        f"-Dmigration.rehearsal.rows={rows}",
        f"-Dmigration.rehearsal.max-lock-seconds={max_lock_seconds}",
        f"-Dmigration.rehearsal.max-migration-seconds={max_migration_seconds}",
        f"-Dmigration.rehearsal.output={output_path}",
    ]

    print(f"Executing migration rehearsal test with {rows} rows...")
    result = subprocess.run(cmd, cwd=str(backend_dir), capture_output=True, text=True)
    if result.returncode != 0:
        print("Migration rehearsal test failed:", file=sys.stderr)
        print(result.stdout[-2000:], file=sys.stderr)
        print(result.stderr[-2000:], file=sys.stderr)
        raise RuntimeError(f"Maven test run failed with exit code {result.returncode}")

    if not output_path.is_file():
        raise FileNotFoundError(f"Expected evidence file was not written: {output_path}")

    try:
        evidence = json.loads(output_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as err:
        raise ValueError(f"Invalid JSON in rehearsal evidence: {err}")

    # Validate mandatory evidence fields and constraints
    if not evidence.get("passed"):
        raise ValueError("Rehearsal evidence indicates test did not pass")

    if evidence.get("numeric_precision") != 46 or evidence.get("numeric_scale") != 10:
        raise ValueError(
            f"V45 schema precision/scale invariant not met: precision={evidence.get('numeric_precision')}, scale={evidence.get('numeric_scale')}"
        )

    if not evidence.get("restore_verified"):
        raise ValueError("Backup restore verification failed or was not proven")

    if not evidence.get("high_precision_verified"):
        raise ValueError("High precision 10-decimal coordinate insertion was not verified")

    actual_lock = evidence.get("actual_lock_seconds", 0.0)
    if actual_lock > max_lock_seconds:
        raise ValueError(f"Observed lock wait ({actual_lock}s) exceeded budget ({max_lock_seconds}s)")

    actual_migration = evidence.get("actual_migration_seconds", 0.0)
    if actual_migration > max_migration_seconds:
        raise ValueError(
            f"Observed migration duration ({actual_migration}s) exceeded budget ({max_migration_seconds}s)"
        )

    # Enrich evidence with runner metadata
    evidence.update(
        {
            "provenance": {
                "runner": "scripts/rehearse_migration.py",
                "backend_revision": revision,
                "executed_at": started_at,
                "budget_rows": rows,
                "budget_max_lock_seconds": max_lock_seconds,
                "budget_max_migration_seconds": max_migration_seconds,
            }
        }
    )

    output_path.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    return evidence


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        rows = validate_positive_int(args.rows, "rows")
        max_lock = validate_positive_float(args.max_lock_seconds, "max-lock-seconds")
        max_migration = validate_positive_float(args.max_migration_seconds, "max-migration-seconds")
    except ValueError as err:
        print(f"Error: Preflight validation failed: {err}", file=sys.stderr)
        return 1

    try:
        evidence = run_rehearsal(
            rows=rows,
            max_lock_seconds=max_lock,
            max_migration_seconds=max_migration,
            output_path=args.output,
            backend_dir=args.backend_dir,
            maven_bin=args.maven_bin,
        )
        print("Migration rehearsal succeeded.")
        print(json.dumps(evidence, indent=2))
        return 0
    except Exception as err:
        print(f"Error: Migration rehearsal failed: {err}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
