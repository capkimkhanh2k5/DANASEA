#!/usr/bin/env python3
"""Run real Java adapter to Quyet Small worker acceptance suite.

Ensures real inference worker is started, health-checked, and exercised via
QuyetDecisionAdapterTest with zero skips and complete provenance.
Sensitive test keys and credentials are never included in evidence or logs.
"""

from __future__ import annotations

import argparse
import importlib
import json
import os
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

# Add repo root to sys.path so scripts modules can be imported
REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

check_test_reports = importlib.import_module("scripts.governance.check_test_reports").check
validate_quyet_model = importlib.import_module(
    "scripts.ops.production"
).validate_quyet_model
support = importlib.import_module("scripts.ai.ai_evidence")
validate_ai_evidence = importlib.import_module(
    "scripts.ai.check_ai_acceptance"
).validate_ai_evidence

EXPECTED_REVISION = "233167bba5df61b5375a522bf8a042d8d2189379"
DEFAULT_PORT = 18091


def get_backend_revision() -> str:
    return support.revision(REPO_ROOT)


def is_port_in_use(port: int, host: str = "127.0.0.1") -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        try:
            s.connect((host, port))
            return True
        except (ConnectionRefusedError, TimeoutError, OSError):
            return False


def ensure_key_file(key_path: Path) -> str:
    key_path = key_path.resolve()
    key_path.parent.mkdir(parents=True, exist_ok=True)
    if not key_path.is_file():
        token = secrets.token_urlsafe(32)
        descriptor = os.open(str(key_path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as f:
            f.write(token)
    else:
        key_path.chmod(0o600)

    key = key_path.read_text(encoding="utf-8").strip()
    if len(key) < 24:
        raise ValueError("API key must contain at least 24 characters")
    if any(c in key for c in ("\r", "\n")):
        raise ValueError("API key contains invalid newline characters")
    return key


def wait_for_health(port: int, proc: subprocess.Popen, timeout: float = 60.0) -> dict:
    url = f"http://127.0.0.1:{port}/health"
    start_time = time.monotonic()
    while time.monotonic() - start_time < timeout:
        if proc.poll() is not None:
            raise RuntimeError(f"Worker exited prematurely with code {proc.returncode}")
        try:
            with urllib.request.urlopen(url, timeout=2.0) as resp:
                if resp.status == 200:
                    data = json.loads(resp.read().decode("utf-8"))
                    if (
                        data.get("status") == "UP"
                        and data.get("revision") == EXPECTED_REVISION
                    ):
                        return data
                    raise ValueError(
                        f"Worker health contract failed: unexpected status/revision {data}"
                    )
        except (urllib.error.URLError, ConnectionRefusedError, TimeoutError):
            pass
        time.sleep(0.5)

    raise TimeoutError(
        f"Worker failed to become healthy within {timeout} seconds on port {port}"
    )


def run_acceptance(
    model_dir, key_file, port, output_file, backend_dir=None, worker_timeout=60.0
):
    backend_dir = (backend_dir or (REPO_ROOT / "backend")).resolve()
    model_dir, key_file, output_file = (
        model_dir.resolve(),
        key_file.resolve(),
        output_file.resolve(),
    )
    output_file.unlink(missing_ok=True)
    validate_quyet_model(str(model_dir))
    if key_file.is_relative_to(model_dir):
        raise ValueError("API key must be outside the model cache")
    key = ensure_key_file(key_file)
    if is_port_in_use(port):
        raise RuntimeError(
            f"Port {port} is already in use; refusing an external worker"
        )
    backend_revision = get_backend_revision()
    started = time.monotonic()
    env = os.environ.copy()
    env.update(
        AI_QUYET_API_KEY=key,
        QUYET_MODEL_DIR=str(model_dir),
        QUYET_CPU_THREADS="2",
        HF_HUB_OFFLINE="1",
        TRANSFORMERS_OFFLINE="1",
        TOKENIZERS_PARALLELISM="false",
    )
    with (
        tempfile.TemporaryDirectory(
            prefix="danasea-worker-observations-"
        ) as observations,
        tempfile.TemporaryFile() as log,
    ):
        proc = subprocess.Popen(
            [
                sys.executable,
                "-m",
                "uvicorn",
                "app.quyet_service:app",
                "--host",
                "127.0.0.1",
                "--port",
                str(port),
                "--workers",
                "1",
                "--no-access-log",
            ],
            cwd=REPO_ROOT / "ai-decision",
            env=env,
            stdout=log,
            stderr=log,
        )
        try:
            health = wait_for_health(port, proc, timeout=worker_timeout)
            if (
                health.get("model") != "chinhnc/Quyet-1.0-Small"
                or proc.poll() is not None
            ):
                raise RuntimeError("Owned worker model/identity check failed")
            command = [
                str(backend_dir / "mvnw"),
                "-B",
                "clean",
                "test",
                "-Dtest=QuyetDecisionAdapterTest",
                "-Dai.local.smoke=true",
                f"-Dai.local.base-url=http://127.0.0.1:{port}",
                f"-Dai.local.key-file={key_file}",
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
                    f"Maven test run failed with exit code {result.returncode}"
                )
            policy = json.loads(
                (REPO_ROOT / ".ci/worker-acceptance-policy.json").read_text()
            )
            check_test_reports(
                backend_dir / "target/surefire-reports",
                set(policy["required_classes"]),
                policy,
            )
            if proc.poll() is not None:
                raise RuntimeError("Owned worker exited during acceptance")
            cases = support.collect(observations, support.WORKER_TASKS)
            record = support.evidence(
                "java-worker-acceptance",
                backend_revision,
                cases,
                time.monotonic() - started,
            )
            validate_ai_evidence(record)
            output_file.parent.mkdir(parents=True, exist_ok=True)
            output_file.write_text(json.dumps(record, indent=2) + "\n")
            return 0
        finally:
            if proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser(
        description="Run Java adapter to Quyet model acceptance."
    )
    parser.add_argument(
        "--model-dir",
        type=Path,
        required=True,
        help="Directory containing pinned Quyet model",
    )
    parser.add_argument(
        "--key-file", type=Path, required=True, help="Path to secret test API key file"
    )
    parser.add_argument(
        "--port", type=int, default=DEFAULT_PORT, help="Port to bind worker"
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

    return run_acceptance(
        model_dir=args.model_dir,
        key_file=args.key_file,
        port=args.port,
        output_file=args.output,
        backend_dir=args.backend_dir,
    )


if __name__ == "__main__":
    sys.exit(main())
