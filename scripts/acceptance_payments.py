#!/usr/bin/env python3
"""Guarded payment acceptance runner for PayPal and VNPay sandbox environments.

Preflight gates ensure environment safety and sandbox host pinning before any network calls.
Session files maintain owned IDs and execution provenance across lifecycle phases.
Sensitive secrets and bearer tokens are strictly forbidden from evidence and session outputs.
"""
import argparse
import json
import os
import re
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1
SUPPORTED_PROVIDERS = ("paypal", "vnpay")
PAYPAL_SANDBOX_HOST = "api-m.sandbox.paypal.com"
VNPAY_SANDBOX_HOST = "sandbox.vnpayment.vn"
COMMIT_SHA_PATTERN = re.compile(r"^[0-9a-f]{7,40}$", re.IGNORECASE)


class AcceptanceError(Exception):
    """Business or safety contract failure in payment acceptance."""


def atomic_write_json(path: Path, data: dict):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_path = tempfile.mkstemp(prefix=f".{path.name}-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, sort_keys=True)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        os.replace(temp_path, path)
    finally:
        if os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass


def sanitize_evidence(obj):
    """Recursively strip sensitive authorization tokens or secrets from logs/evidence."""
    if isinstance(obj, dict):
        sanitized = {}
        for k, v in obj.items():
            lower_k = k.lower()
            if any(secret_term in lower_k for secret_term in ("token", "secret", "password", "authorization", "key")):
                sanitized[k] = "[REDACTED]"
            else:
                sanitized[k] = sanitize_evidence(v)
        return sanitized
    elif isinstance(obj, list):
        return [sanitize_evidence(item) for item in obj]
    return obj


def validate_deployment_manifest(manifest_path: Path, expected_provider: str) -> dict:
    if not manifest_path.is_file():
        raise AcceptanceError(f"Deployment manifest not found at: {manifest_path}")

    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception as e:
        raise AcceptanceError(f"Deployment manifest is not valid JSON: {e}") from e

    if not isinstance(manifest, dict):
        raise AcceptanceError("Deployment manifest must be a JSON object")

    env = manifest.get("environment")
    if env != "sandbox":
        raise AcceptanceError(f"Manifest environment must be 'sandbox', found: '{env}'")

    revision = manifest.get("revision") or manifest.get("backend_revision") or manifest.get("sha")
    if not revision or not isinstance(revision, str) or not COMMIT_SHA_PATTERN.match(revision):
        raise AcceptanceError(f"Manifest has missing or invalid backend revision: '{revision}'")

    gateways = manifest.get("gateways")
    if not isinstance(gateways, dict):
        raise AcceptanceError("Manifest is missing required 'gateways' configuration dictionary")

    if expected_provider == "paypal":
        paypal_config = gateways.get("paypal")
        if not isinstance(paypal_config, dict):
            raise AcceptanceError("Manifest missing 'gateways.paypal' configuration")
        host = paypal_config.get("api_host")
        if host != PAYPAL_SANDBOX_HOST:
            raise AcceptanceError(
                f"PayPal API host must be '{PAYPAL_SANDBOX_HOST}', found: '{host}'"
            )

    elif expected_provider == "vnpay":
        vnpay_config = gateways.get("vnpay")
        if not isinstance(vnpay_config, dict):
            raise AcceptanceError("Manifest missing 'gateways.vnpay' configuration")
        host = vnpay_config.get("host") or vnpay_config.get("pay_host")
        if host != VNPAY_SANDBOX_HOST:
            raise AcceptanceError(
                f"VNPay host must be '{VNPAY_SANDBOX_HOST}', found: '{host}'"
            )

    return manifest


def read_customer_token(token_file: Path) -> str:
    if not token_file.is_file():
        raise AcceptanceError(f"Customer token file not found at: {token_file}")
    token = token_file.read_text(encoding="utf-8").strip()
    if not token:
        raise AcceptanceError(f"Customer token file is empty: {token_file}")
    if any(c in token for c in ("\r", "\n")):
        raise AcceptanceError("Customer token contains invalid newline characters")
    return token


def validate_session(session_path: Path, manifest: dict, expected_provider: str) -> dict:
    if not session_path.is_file():
        raise AcceptanceError(f"Session file not found at: {session_path}")
    try:
        session = json.loads(session_path.read_text(encoding="utf-8"))
    except Exception as e:
        raise AcceptanceError(f"Session file is not valid JSON: {e}") from e

    if not isinstance(session, dict):
        raise AcceptanceError("Session must be a JSON object")

    if session.get("schema_version") != SCHEMA_VERSION:
        raise AcceptanceError(f"Session schema_version must be {SCHEMA_VERSION}")

    if session.get("environment") != "sandbox":
        raise AcceptanceError(f"Session environment must be 'sandbox', found: {session.get('environment')}")

    if session.get("provider") != expected_provider:
        raise AcceptanceError(
            f"Session provider mismatch: expected '{expected_provider}', got '{session.get('provider')}'"
        )

    manifest_rev = manifest.get("revision") or manifest.get("backend_revision") or manifest.get("sha")
    session_rev = session.get("backend_revision")
    if session_rev != manifest_rev:
        raise AcceptanceError(
            f"Session backend revision '{session_rev}' does not match deployment manifest '{manifest_rev}'"
        )

    for field in ("order_id", "payment_id"):
        if not session.get(field):
            raise AcceptanceError(f"Session missing mandatory owned ID field: '{field}'")

    return session


def validate_url(url_str: str) -> str:
    parsed = urllib.parse.urlsplit(url_str)
    if parsed.scheme not in ("https", "http") or not parsed.hostname:
        raise AcceptanceError(f"Invalid base URL: {url_str}")
    if parsed.username or parsed.password:
        raise AcceptanceError("Base URL cannot contain embedded credentials")
    return url_str.rstrip("/")


def send_http_request(url: str, method: str = "GET", headers: dict = None, body: dict = None) -> tuple[int, dict, float]:
    headers = headers or {}
    encoded_body = None
    if body is not None:
        encoded_body = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=encoded_body, headers=headers, method=method)
    start_time = time.monotonic()
    status = 500
    response_data = {}
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            status = resp.status
            raw_data = resp.read()
            if raw_data:
                response_data = json.loads(raw_data.decode("utf-8"))
    except urllib.error.HTTPError as e:
        status = e.code
        try:
            raw_data = e.read()
            if raw_data:
                try:
                    response_data = json.loads(raw_data.decode("utf-8"))
                except Exception:
                    response_data = {"error": raw_data.decode("utf-8", errors="replace")}
        except Exception:
            response_data = {"error": str(e)}
    except Exception as e:
        duration = time.monotonic() - start_time
        raise AcceptanceError(f"Network request to {url} failed: {e}") from e

    duration = time.monotonic() - start_time
    return status, response_data, duration


def run_preflight(args) -> int:
    manifest = validate_deployment_manifest(Path(args.deployment_manifest), args.provider)
    read_customer_token(Path(args.token_file))
    base_url = validate_url(args.base_url)

    if Path(args.session).is_file():
        validate_session(Path(args.session), manifest, args.provider)

    evidence = {
        "schema_version": SCHEMA_VERSION,
        "phase": "preflight",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "provider": args.provider,
        "environment": "sandbox",
        "backend_revision": manifest.get("revision") or manifest.get("backend_revision") or manifest.get("sha"),
        "base_url": base_url,
        "preflight_status": "PASSED",
        "checks": [
            "token_file_valid",
            "deployment_manifest_valid",
            "sandbox_environment_verified",
            f"{args.provider}_sandbox_host_pinned",
        ],
    }

    evidence_dir = Path(args.evidence_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    evidence_file = evidence_dir / f"preflight-{args.provider}.json"
    atomic_write_json(evidence_file, evidence)
    print(f"[PREFLIGHT PASSED] Evidence written to {evidence_file}")
    return 0


def run_create_intent(args) -> int:
    manifest = validate_deployment_manifest(Path(args.deployment_manifest), args.provider)
    token = read_customer_token(Path(args.token_file))
    base_url = validate_url(args.base_url)

    if not getattr(args, "order_id", None):
        raise AcceptanceError("--order-id is required for create-intent")

    order_id = str(args.order_id)
    idempotency_key = str(uuid.uuid4())
    run_id = str(uuid.uuid4())

    endpoint = f"{base_url}/api/payments/{order_id}/create-intent"
    payload = {"provider": args.provider.upper()}
    headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": idempotency_key,
    }

    status, resp, duration = send_http_request(endpoint, method="POST", headers=headers, body=payload)
    if status != 200:
        raise AcceptanceError(f"create-intent failed with HTTP {status}: {resp}")

    payment_id = resp.get("paymentId")
    if not payment_id:
        raise AcceptanceError("Backend did not return paymentId in create-intent response")

    approval_url = resp.get("reference") or resp.get("paymentUrl") or ""

    backend_revision = manifest.get("revision") or manifest.get("backend_revision") or manifest.get("sha")
    session = {
        "schema_version": SCHEMA_VERSION,
        "environment": "sandbox",
        "backend_revision": backend_revision,
        "provider": args.provider,
        "run_id": run_id,
        "order_id": order_id,
        "payment_id": str(payment_id),
        "approval_url": approval_url,
        "refund_id": None,
        "operation_id": None,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }

    session_path = Path(args.session)
    atomic_write_json(session_path, session)

    evidence = {
        "schema_version": SCHEMA_VERSION,
        "phase": "create-intent",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "run_id": run_id,
        "request_outcome": {
            "http_status": status,
            "duration_seconds": round(duration, 3),
            "response_summary": sanitize_evidence(resp),
        },
        "provider_verification": {
            "approval_url_present": bool(approval_url),
        },
        "persisted_state_verification": {
            "order_id": order_id,
            "payment_id": str(payment_id),
        },
    }

    evidence_dir = Path(args.evidence_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    evidence_file = evidence_dir / f"create-intent-{args.provider}-{run_id}.json"
    atomic_write_json(evidence_file, evidence)

    print(f"[INTENT CREATED] Payment ID: {payment_id}")
    print(f"[ACTION REQUIRED] Buyer must approve sandbox transaction at: {approval_url}")
    print(f"Session stored at: {session_path}")
    return 0


def run_capture(args) -> int:
    manifest = validate_deployment_manifest(Path(args.deployment_manifest), args.provider)
    token = read_customer_token(Path(args.token_file))
    base_url = validate_url(args.base_url)
    session = validate_session(Path(args.session), manifest, args.provider)

    payment_id = session["payment_id"]
    idempotency_key = str(uuid.uuid4())

    if args.provider == "paypal":
        paypal_order_id = session.get("approval_url", "").split("token=")[-1] or session.get("payment_id")
        if getattr(args, "paypal_order_id", None):
            paypal_order_id = args.paypal_order_id

        endpoint = f"{base_url}/api/payments/paypal/capture"
        payload = {
            "paymentId": payment_id,
            "paypalOrderId": paypal_order_id,
        }
        headers = {
            "Authorization": f"Bearer {token}",
            "Idempotency-Key": idempotency_key,
        }
        status, resp, duration = send_http_request(endpoint, method="POST", headers=headers, body=payload)
    else:
        # VNPay IPN or capture verification
        endpoint = f"{base_url}/api/payments/detail/{payment_id}"
        headers = {"Authorization": f"Bearer {token}"}
        status, resp, duration = send_http_request(endpoint, method="GET", headers=headers)

    if status != 200:
        raise AcceptanceError(f"Capture/Verification failed with HTTP {status}: {resp}")

    evidence = {
        "schema_version": SCHEMA_VERSION,
        "phase": "capture",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "run_id": session.get("run_id"),
        "request_outcome": {
            "http_status": status,
            "duration_seconds": round(duration, 3),
            "response_summary": sanitize_evidence(resp),
        },
        "provider_verification": {
            "terminal_status_reached": resp.get("status") in ("SUCCESS", "COMPLETED"),
        },
        "persisted_state_verification": {
            "payment_id": payment_id,
            "recorded_amount": str(resp.get("amount")),
            "recorded_status": resp.get("status"),
        },
    }

    evidence_dir = Path(args.evidence_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    evidence_file = evidence_dir / f"capture-{args.provider}-{session.get('run_id')}.json"
    atomic_write_json(evidence_file, evidence)
    print(f"[CAPTURE/VERIFY RECORDED] Status: {resp.get('status')}")
    return 0


def run_verify(args) -> int:
    manifest = validate_deployment_manifest(Path(args.deployment_manifest), args.provider)
    token = read_customer_token(Path(args.token_file))
    base_url = validate_url(args.base_url)
    session = validate_session(Path(args.session), manifest, args.provider)

    payment_id = session["payment_id"]
    endpoint = f"{base_url}/api/payments/detail/{payment_id}"
    headers = {"Authorization": f"Bearer {token}"}

    status, resp, duration = send_http_request(endpoint, method="GET", headers=headers)
    if status != 200:
        raise AcceptanceError(f"Verify failed with HTTP {status}: {resp}")

    evidence = {
        "schema_version": SCHEMA_VERSION,
        "phase": "verify",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "run_id": session.get("run_id"),
        "request_outcome": {
            "http_status": status,
            "duration_seconds": round(duration, 3),
            "response_summary": sanitize_evidence(resp),
        },
        "provider_verification": {
            "status": resp.get("status"),
            "provider": resp.get("provider"),
        },
        "persisted_state_verification": {
            "payment_id": payment_id,
            "order_id": resp.get("orderId"),
            "amount": str(resp.get("amount")),
            "currency": resp.get("currency"),
        },
    }

    evidence_dir = Path(args.evidence_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    evidence_file = evidence_dir / f"verify-{args.provider}-{session.get('run_id')}.json"
    atomic_write_json(evidence_file, evidence)
    print(f"[VERIFY COMPLETED] Payment {payment_id} Status: {resp.get('status')}")
    return 0


def run_refund(args) -> int:
    manifest = validate_deployment_manifest(Path(args.deployment_manifest), args.provider)
    token = read_customer_token(Path(args.token_file))
    base_url = validate_url(args.base_url)
    session = validate_session(Path(args.session), manifest, args.provider)

    order_id = session["order_id"]
    idempotency_key = str(uuid.uuid4())
    endpoint = f"{base_url}/api/orders/{order_id}/refund-request"
    headers = {
        "Authorization": f"Bearer {token}",
        "Idempotency-Key": idempotency_key,
    }
    payload = {"reason": "CUSTOMER_REQUEST"}

    status, resp, duration = send_http_request(endpoint, method="POST", headers=headers, body=payload)
    if status not in (200, 202):
        raise AcceptanceError(f"Refund request failed with HTTP {status}: {resp}")

    refund_id = None
    if isinstance(resp, list) and len(resp) > 0:
        refund_id = str(resp[0].get("refundId"))
        session["refund_id"] = refund_id
        atomic_write_json(Path(args.session), session)

    evidence = {
        "schema_version": SCHEMA_VERSION,
        "phase": "refund",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "run_id": session.get("run_id"),
        "request_outcome": {
            "http_status": status,
            "duration_seconds": round(duration, 3),
            "response_summary": sanitize_evidence(resp),
        },
        "provider_verification": {
            "refund_initiated": bool(refund_id),
        },
        "persisted_state_verification": {
            "order_id": order_id,
            "refund_id": refund_id,
        },
    }

    evidence_dir = Path(args.evidence_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    evidence_file = evidence_dir / f"refund-{args.provider}-{session.get('run_id')}.json"
    atomic_write_json(evidence_file, evidence)
    print(f"[REFUND INITIATED] Refund ID: {refund_id}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Payment acceptance runner with sandbox preflight and safety guards.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    common_parser = argparse.ArgumentParser(add_help=False)
    common_parser.add_argument("--provider", choices=SUPPORTED_PROVIDERS, required=True, help="Target payment provider")
    common_parser.add_argument("--base-url", required=True, help="Base backend URL")
    common_parser.add_argument("--token-file", type=Path, required=True, help="Path to customer auth token file")
    common_parser.add_argument("--session", type=Path, required=True, help="Path to session JSON file")
    common_parser.add_argument("--evidence-dir", type=Path, required=True, help="Directory to store evidence JSON")
    common_parser.add_argument(
        "--deployment-manifest", type=Path, required=True, help="Path to deployment manifest JSON"
    )

    subparsers.add_parser("preflight", parents=[common_parser], help="Run sandbox safety preflight")

    intent_parser = subparsers.add_parser("create-intent", parents=[common_parser], help="Create payment intent")
    intent_parser.add_argument("--order-id", required=True, help="Order ID to pay")

    capture_parser = subparsers.add_parser("capture", parents=[common_parser], help="Capture approved payment")
    capture_parser.add_argument("--paypal-order-id", required=False, help="Explicit PayPal Order ID if available")

    subparsers.add_parser("verify", parents=[common_parser], help="Verify backend and provider payment state")

    subparsers.add_parser("refund", parents=[common_parser], help="Initiate refund request for session")

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    command_handlers = {
        "preflight": run_preflight,
        "create-intent": run_create_intent,
        "capture": run_capture,
        "verify": run_verify,
        "refund": run_refund,
    }

    handler = command_handlers.get(args.command)
    if not handler:
        sys.stderr.write(f"Unknown command: {args.command}\n")
        return 1

    try:
        return handler(args)
    except AcceptanceError as e:
        sys.stderr.write(f"[ACCEPTANCE FAILURE] {e}\n")
        return 2
    except Exception as e:
        sys.stderr.write(f"[UNEXPECTED ERROR] {e}\n")
        return 3


if __name__ == "__main__":
    sys.exit(main())
