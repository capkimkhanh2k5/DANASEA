#!/usr/bin/env python3
"""Run live CLIProxy acceptance tests with provenance and budget tracking.

Exercises ProxyLiveSmokeTest across reasoning models and context compaction.
Ensures zero skips, enforces strict preflight parameter gates, and prevents secret leakage.
"""

from __future__ import annotations

import argparse
import importlib
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.parse
from pathlib import Path

# Add repo root to sys.path
REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

check_test_reports = importlib.import_module("scripts.check_test_reports").check
support = importlib.import_module("scripts.ai_evidence")
validate_ai_evidence = importlib.import_module(
    "scripts.check_ai_acceptance"
).validate_ai_evidence


def get_backend_revision():
    return support.revision(REPO_ROOT)


def validate_key_file(key_path: Path) -> str:
    key_path = key_path.resolve()
    if not key_path.is_file():
        raise ValueError(f"Proxy API key file not found: {key_path}")
    key = key_path.read_text(encoding="utf-8").strip()
    if not key or len(key) < 24:
        raise ValueError("Proxy API key must contain at least 24 characters")
    if any(c in key for c in ("\r", "\n")):
        raise ValueError("Proxy API key contains invalid newline characters")
    return key


def validate_base_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ("https", "http") or not parsed.hostname:
        raise ValueError(f"Invalid proxy base URL: {url}")
    if parsed.username or parsed.password:
        raise ValueError("Proxy base URL cannot contain credentials")
    allowed = set(os.environ.get("AI_PROXY_ALLOWED_HOSTS", "").split(",")) - {""}
    if parsed.hostname not in allowed or parsed.query or parsed.fragment:
        raise ValueError("Proxy endpoint is outside protected environment allowlist")
    if parsed.scheme != "https" and parsed.hostname not in ("127.0.0.1", "localhost"):
        raise ValueError("Remote proxy endpoint requires HTTPS")
    return url.rstrip("/")


def run_proxy_acceptance(base_url, key_file, output_file, backend_dir=None):
    backend_dir = (backend_dir or (REPO_ROOT / "backend")).resolve()
    key_file, output_file = key_file.resolve(), output_file.resolve()
    output_file.unlink(missing_ok=True)
    base_url = validate_base_url(base_url)
    key = validate_key_file(key_file)
    backend_revision = get_backend_revision()
    started = time.monotonic()
    with tempfile.TemporaryDirectory(
        prefix="danasea-proxy-observations-"
    ) as observations:
        command = [
            str(backend_dir / "mvnw"),
            "-B",
            "clean",
            "test",
            "-Dtest=ProxyLiveSmokeTest",
            "-Dai.proxy.smoke=true",
            f"-Dai.proxy.base-url={base_url}",
            f"-Dai.proxy.key-file={key_file}",
            f"-Dai.acceptance.dir={observations}",
        ]
        result = subprocess.run(
            command, cwd=backend_dir, capture_output=True, text=True, timeout=900
        )
        if result.returncode != 0:
            sys.stderr.write(
                (result.stdout + result.stderr)[-4000:].replace(key, "[REDACTED]")
            )
            raise RuntimeError(
                f"Maven ProxyLiveSmokeTest failed with exit code {result.returncode}"
            )
        policy = json.loads(
            (REPO_ROOT / ".ci/proxy-acceptance-policy.json").read_text()
        )
        check_test_reports(
            backend_dir / "target/surefire-reports",
            set(policy["required_classes"]),
            policy,
        )
        cases = support.collect(observations, support.PROXY_CASES)
        record = support.evidence(
            "proxy-live-acceptance", backend_revision, cases, time.monotonic() - started
        )
        validate_ai_evidence(record)
        output_file.parent.mkdir(parents=True, exist_ok=True)
        output_file.write_text(json.dumps(record, indent=2) + "\n")
    return 0


def main():
    parser = argparse.ArgumentParser(description="Run CLIProxy live acceptance suite.")
    parser.add_argument(
        "--base-url", required=True, help="Base URL of CLIProxy service"
    )
    parser.add_argument(
        "--key-file", type=Path, required=True, help="Path to secret proxy API key file"
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Path to write acceptance evidence JSON",
    )
    parser.add_argument(
        "--backend-dir", type=Path, default=None, help="Path to backend directory"
    )
    args = parser.parse_args()

    return run_proxy_acceptance(
        base_url=args.base_url,
        key_file=args.key_file,
        output_file=args.output,
        backend_dir=args.backend_dir,
    )


if __name__ == "__main__":
    sys.exit(main())
