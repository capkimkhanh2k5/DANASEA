#!/usr/bin/env python3
"""Operate only on signed sandbox fixtures; preserve private sessions between phases."""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path

SCHEMA_VERSION = 2
SUPPORTED_PROVIDERS = ("paypal", "vnpay")
PAYPAL_SANDBOX_HOST = "api-m.sandbox.paypal.com"
VNPAY_SANDBOX_HOST = "sandbox.vnpayment.vn"


class AcceptanceError(ValueError):
    """Required sandbox identity or financial invariant is not established."""


def require(condition, message):
    if not condition:
        raise AcceptanceError(message)


def atomic_write_json(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix="." + path.name, dir=path.parent)
    try:
        with os.fdopen(fd, "w") as output:
            json.dump(data, output, indent=2)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def sanitize_evidence(data):
    if isinstance(data, dict):
        return {
            key: "[REDACTED]"
            if any(
                word in key.lower()
                for word in (
                    "token",
                    "secret",
                    "password",
                    "authorization",
                    "key",
                    "url",
                )
            )
            else sanitize_evidence(value)
            for key, value in data.items()
        }
    if isinstance(data, list):
        return [sanitize_evidence(value) for value in data]
    return data


def validate_url(url):
    parsed = urllib.parse.urlsplit(url)
    require(
        parsed.scheme in ("http", "https")
        and parsed.hostname
        and not parsed.username
        and not parsed.password
        and not parsed.query
        and not parsed.fragment,
        "Invalid backend base URL",
    )
    require(
        parsed.scheme == "https" or parsed.hostname in ("127.0.0.1", "localhost"),
        "Remote backend requires HTTPS",
    )
    return url.rstrip("/")


def amount(value):
    try:
        result = Decimal(str(value))
    except (InvalidOperation, TypeError) as error:
        raise AcceptanceError("Invalid monetary amount") from error
    require(
        result.is_finite() and result > 0, "Monetary amount must be finite and positive"
    )
    return result


def validate_deployment_manifest(path, provider):
    try:
        data = json.loads(Path(path).read_text())
    except (OSError, ValueError) as error:
        raise AcceptanceError("Deployment manifest not found or invalid") from error
    require(
        data.get("environment") == "sandbox", "Manifest environment must be 'sandbox'"
    )
    require(
        isinstance(data.get("revision"), str)
        and re.fullmatch("[0-9a-f]{40}", data["revision"]),
        "Missing or invalid backend revision",
    )
    gateways = data.get("gateways", {})
    if provider == "paypal":
        require(
            gateways.get(provider, {}).get("api_host") == PAYPAL_SANDBOX_HOST,
            "PayPal host must be " + PAYPAL_SANDBOX_HOST,
        )
    else:
        require(
            gateways.get(provider, {}).get("host") == VNPAY_SANDBOX_HOST,
            "VNPay host must be " + VNPAY_SANDBOX_HOST,
        )
    return data


def verify_signed(path, signers, namespace):
    require(
        signers and Path(signers).is_file(), "Trusted sandbox signers file required"
    )
    signature = Path(str(path) + ".sig")
    require(signature.is_file(), "Signed deployment/receipt attestation required")
    result = subprocess.run(
        [
            "ssh-keygen",
            "-Y",
            "verify",
            "-f",
            str(signers),
            "-I",
            "sandbox-operator",
            "-n",
            namespace,
            "-s",
            str(signature),
        ],
        input=Path(path).read_bytes(),
        capture_output=True,
    )
    require(result.returncode == 0, "Sandbox attestation signature verification failed")


def read_customer_token(path):
    try:
        token = Path(path).read_text().strip()
    except OSError as error:
        raise AcceptanceError("Customer token file not found") from error
    require(
        token and "\n" not in token and "\r" not in token,
        "Customer token is empty or invalid",
    )
    return token


def context(args):
    manifest = validate_deployment_manifest(args.deployment_manifest, args.provider)
    verify_signed(
        args.deployment_manifest,
        getattr(args, "signers", None),
        "danasea-payment-sandbox",
    )
    base = validate_url(args.base_url)
    require(
        manifest.get("base_url") == base,
        "Sandbox backend URL does not match signed deployment",
    )
    require(
        manifest["revision"] == getattr(args, "expected_revision", None),
        "Deployment revision mismatch",
    )
    try:
        expires = datetime.fromisoformat(manifest["expires_at"])
        attested = datetime.fromisoformat(manifest["attested_at"])
        now = datetime.now(timezone.utc)
        require(
            attested.tzinfo
            and expires.tzinfo
            and attested <= now < expires
            and (expires - attested).total_seconds() <= 3600,
            "Sandbox deployment attestation expired/invalid",
        )
    except (KeyError, ValueError, TypeError) as error:
        raise AcceptanceError(
            "Sandbox deployment attestation expired/invalid"
        ) from error
    fixture = manifest.get("fixture", {})
    for key in ("order_id", "customer_id"):
        require(bool(fixture.get(key)), "Signed owned fixture missing " + key)
    amount(fixture.get("amount"))
    amount(fixture.get("provider_amount"))
    expected_refunds = fixture.get("refunds")
    require(
        isinstance(expected_refunds, list) and expected_refunds,
        "Signed refund quote required",
    )
    require(
        len({row.get("sub_order_id") for row in expected_refunds})
        == len(expected_refunds),
        "Duplicate refund quote",
    )
    for row in expected_refunds:
        require(
            row.get("sub_order_id")
            and row.get("currency") == fixture.get("provider_currency"),
            "Invalid refund quote identity/currency",
        )
        amount(row.get("amount"))
        amount(row.get("provider_amount"))
    require(
        sum(amount(row["amount"]) for row in expected_refunds)
        <= amount(fixture["amount"]),
        "Refund quote exceeds original payment",
    )

    require(
        fixture.get("provider_currency")
        == ("USD" if args.provider == "paypal" else "VND"),
        "Provider currency mismatch",
    )
    return manifest, read_customer_token(args.token_file), base


def validate_session(path, manifest, provider):
    try:
        session = json.loads(Path(path).read_text())
    except (OSError, ValueError) as error:
        raise AcceptanceError("Session file not found or invalid") from error
    require(
        session.get("schema_version") == SCHEMA_VERSION
        and session.get("environment") == "sandbox",
        "Invalid session version/environment",
    )
    require(session.get("provider") == provider, "Session provider mismatch")
    require(
        session.get("backend_revision") == manifest["revision"],
        "Session revision mismatch",
    )
    require(
        session.get("base_url") == manifest["base_url"],
        "Session belongs to another backend",
    )
    require(
        session.get("order_id") == manifest["fixture"]["order_id"]
        and session.get("customer_id") == manifest["fixture"]["customer_id"],
        "Session fixture mismatch",
    )
    require(session.get("fixture") == manifest["fixture"], "Session quote mismatch")
    return session


def send_http_request(url, method="GET", headers=None, body=None):
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(
            self, request, fp, code, message, response_headers, target
        ):
            raise AcceptanceError("Redirects are forbidden for sandbox credentials")

    request_headers = dict(headers or {})
    payload = None
    if body is not None:
        payload = json.dumps(body).encode()
        request_headers["Content-Type"] = "application/json"
    started = time.monotonic()
    request = urllib.request.Request(
        url, data=payload, headers=request_headers, method=method
    )
    try:
        with urllib.request.build_opener(NoRedirect).open(
            request, timeout=30
        ) as response:
            return (
                response.status,
                json.loads(response.read(), parse_float=Decimal),
                time.monotonic() - started,
            )
    except Exception as error:
        raise AcceptanceError(
            "Sandbox HTTP request failed: " + urllib.parse.urlsplit(url).path
        ) from error


def request(base, token, path, body=None, key=None):
    headers = {"Authorization": "Bearer " + token}
    if key:
        headers["Idempotency-Key"] = key
    status, data, _ = send_http_request(
        base + path, "POST" if body is not None else "GET", headers, body
    )
    require(
        status in (200, 201, 202), "Sandbox request failed with HTTP " + str(status)
    )
    return data


def require_payment(data, session, terminal=True, allowed_statuses=("SUCCESS",)):
    require(
        isinstance(data, dict) and data.get("id") == session["payment_id"],
        "Payment ID mismatch",
    )
    require(
        data.get("masterOrderId") == session["order_id"]
        and data.get("provider") == session["provider"].upper(),
        "Payment order/provider mismatch",
    )
    require(
        amount(data.get("amount")) == amount(session["fixture"]["amount"]),
        "Payment amount mismatch",
    )
    if session.get("provider_order_id"):
        require(
            data.get("providerOrderId") == session["provider_order_id"],
            "Provider order ID changed",
        )
    if session.get("provider_transaction_id"):
        require(
            data.get("providerTransactionId") == session["provider_transaction_id"],
            "Provider transaction ID changed after verified capture",
        )
    if terminal:
        require(
            data.get("status") in allowed_statuses
            and data.get("providerTransactionId"),
            "Payment is not verified SUCCESS; acceptance blocked",
        )


def save_evidence(args, session, phase, status, data):
    # Deliberate allowlist: no raw API response, buyer approval URL or authorization in public evidence.
    record = {
        "schema_version": SCHEMA_VERSION,
        "phase": phase,
        "status": status,
        "run_id": session.get("run_id"),
        "backend_revision": session["backend_revision"],
        "provider": args.provider,
        "environment": "sandbox",
        "observations": data,
    }
    atomic_write_json(
        Path(args.evidence_dir) / (phase + "-" + args.provider + ".json"), record
    )


def run_preflight(args):
    manifest, _, _ = context(args)
    if Path(args.session).exists():
        validate_session(args.session, manifest, args.provider)
    save_evidence(
        args,
        {"backend_revision": manifest["revision"]},
        "preflight",
        "PASSED",
        {"trusted_deployment_attestation_verified": True},
    )
    return 0


def run_create_intent(args):
    manifest, token, base = context(args)
    fixture = manifest["fixture"]
    require(
        getattr(args, "order_id", None) == fixture["order_id"],
        "Order is not the signed owned sandbox fixture",
    )
    order = request(base, token, "/api/orders/" + fixture["order_id"])
    require(
        order.get("id") == fixture["order_id"]
        and order.get("customerId") == fixture["customer_id"],
        "Owned order readback mismatch",
    )
    require(
        amount(order.get("totalAmount")) == amount(fixture["amount"]),
        "Order amount mismatch",
    )
    if Path(args.session).exists():
        session = validate_session(args.session, manifest, args.provider)
    else:
        session = {
            "schema_version": SCHEMA_VERSION,
            "environment": "sandbox",
            "provider": args.provider,
            "backend_revision": manifest["revision"],
            "base_url": base,
            "fixture": fixture,
            "customer_id": fixture["customer_id"],
            "order_id": fixture["order_id"],
            "run_id": str(uuid.uuid4()),
            "idempotency_keys": {
                phase: str(uuid.uuid4())
                for phase in ("create-intent", "capture", "refund")
            },
        }
        atomic_write_json(args.session, session)
    data = request(
        base,
        token,
        "/api/payments/" + session["order_id"] + "/create-intent",
        {"provider": args.provider.upper()},
        session["idempotency_keys"]["create-intent"],
    )
    require(
        data.get("orderId") == session["order_id"]
        and data.get("provider") == args.provider.upper()
        and data.get("paymentId"),
        "Intent identity mismatch",
    )
    require(
        amount(data.get("amount")) == amount(fixture["amount"]),
        "Intent amount mismatch",
    )
    approval = data.get("paymentReference")
    url = urllib.parse.urlsplit(approval or "")
    require(
        url.scheme == "https"
        and url.hostname
        == (
            "www.sandbox.paypal.com"
            if args.provider == "paypal"
            else VNPAY_SANDBOX_HOST
        ),
        "Missing or non-sandbox approval URL",
    )
    if session.get("payment_id"):
        require(
            session["payment_id"] == data["paymentId"],
            "Replay created a different payment",
        )
    session.update(payment_id=data["paymentId"], approval_url=approval)
    detail = request(base, token, "/api/payments/detail/" + session["payment_id"])
    require_payment(detail, session, False)
    require(detail.get("providerOrderId"), "Missing persisted provider order ID")
    session["provider_order_id"] = detail["providerOrderId"]
    atomic_write_json(args.session, session)
    save_evidence(
        args,
        session,
        "create-intent",
        "AWAITING_BUYER_APPROVAL",
        {"payment_id": session["payment_id"]},
    )
    print(
        "Buyer approval URL is in the private session. Complete sandbox approval before capture."
    )
    return 0


def run_capture(args):
    manifest, token, base = context(args)
    session = validate_session(args.session, manifest, args.provider)
    require(
        session.get("payment_id") and session.get("provider_order_id"),
        "Session has no owned intent",
    )
    if args.provider == "paypal":
        data = request(
            base,
            token,
            "/api/payments/paypal/capture",
            {
                "paymentId": session["payment_id"],
                "paypalOrderId": session["provider_order_id"],
            },
            session["idempotency_keys"]["capture"],
        )
        require_payment(data, session)
    data = request(base, token, "/api/payments/detail/" + session["payment_id"])
    require_payment(data, session)
    session["capture_verified"] = True
    session["provider_transaction_id"] = data["providerTransactionId"]
    atomic_write_json(args.session, session)
    save_evidence(
        args,
        session,
        "capture",
        "BACKEND_VERIFIED",
        {"payment_id": session["payment_id"], "status": data["status"]},
    )
    return 0


def run_refund(args):
    manifest, token, base = context(args)
    session = validate_session(args.session, manifest, args.provider)
    require(session.get("capture_verified"), "Capture must be verified before refund")
    require_payment(
        request(base, token, "/api/payments/detail/" + session["payment_id"]),
        session,
        allowed_statuses=("SUCCESS", "REFUNDED"),
    )
    data = request(
        base,
        token,
        "/api/orders/" + session["order_id"] + "/refund-request",
        {"reason": "CUSTOMER_REQUEST"},
        session["idempotency_keys"]["refund"],
    )
    require(isinstance(data, list) and data, "Refund response is empty")
    ids = [entry.get("id") for entry in data]
    require(all(ids) and len(ids) == len(set(ids)), "Missing or duplicate refund IDs")
    if session.get("refund_ids"):
        require(session["refund_ids"] == ids, "Replay created different refunds")
    quotes = {row["sub_order_id"]: row for row in session["fixture"]["refunds"]}
    require(len(data) == len(quotes), "Refund quote count mismatch")
    for row in data:
        quote = quotes.get(row.get("subOrderId"))
        require(
            quote and amount(row.get("amount")) == amount(quote["amount"]),
            "Refund request amount/sub-order mismatch",
        )
    session["expected_refunds"] = {
        row["id"]: {
            "sub_order_id": row["subOrderId"],
            "amount": str(amount(row["amount"])),
        }
        for row in data
    }
    session["refund_ids"] = ids
    atomic_write_json(args.session, session)
    save_evidence(
        args,
        session,
        "refund",
        "INITIATED",
        {"refund_ids": ids, "provider_completion_verified": False},
    )
    return 0


def run_verify(args):
    manifest, token, base = context(args)
    session = validate_session(args.session, manifest, args.provider)
    payment = request(base, token, "/api/payments/detail/" + session["payment_id"])
    require_payment(payment, session, allowed_statuses=("SUCCESS", "REFUNDED"))
    receipt_path = getattr(args, "provider_receipt", None)
    require(
        receipt_path,
        "Independent signed provider receipt required; live acceptance blocked",
    )
    verify_signed(receipt_path, args.signers, "danasea-payment-provider-receipt")
    receipt = json.loads(Path(receipt_path).read_text())
    for field, expected in [
        ("environment", "sandbox"),
        ("backend_revision", session["backend_revision"]),
        ("run_id", session["run_id"]),
        ("provider", args.provider),
        ("order_id", session["order_id"]),
        ("payment_id", session["payment_id"]),
        ("provider_order_id", session["provider_order_id"]),
        ("provider_transaction_id", session["provider_transaction_id"]),
        ("currency", session["fixture"]["provider_currency"]),
        ("status", "COMPLETED"),
    ]:
        require(receipt.get(field) == expected, "Provider receipt mismatch: " + field)
    require(
        amount(receipt.get("amount")) == amount(session["fixture"]["provider_amount"]),
        "Provider receipt amount mismatch",
    )
    require(session.get("refund_ids"), "Full acceptance requires refund completion")
    refunds = request(base, token, "/api/orders/" + session["order_id"] + "/refunds")
    require(isinstance(refunds, list), "Invalid refund readback")
    related = [
        refund for refund in refunds if refund.get("paymentId") == session["payment_id"]
    ]
    require(
        len(related) == len(session["refund_ids"])
        and {row.get("id") for row in related} == set(session["refund_ids"]),
        "Unexpected additional financial refund",
    )

    owned = [refund for refund in refunds if refund.get("id") in session["refund_ids"]]
    require(
        len(owned) == len(session["refund_ids"])
        and len({r["id"] for r in owned}) == len(owned),
        "Missing or duplicate persisted refunds",
    )
    expected = session.get("expected_refunds", {})
    provider_refunds = receipt.get("refunds")
    require(
        isinstance(provider_refunds, list) and len(provider_refunds) == len(owned),
        "Missing per-refund provider receipt",
    )
    by_provider_id = {row.get("provider_refund_id"): row for row in provider_refunds}
    require(len(by_provider_id) == len(owned), "Duplicate provider refund receipts")
    operations = set()
    for refund in owned:
        quote = expected.get(refund["id"])
        require(
            quote
            and refund.get("subOrderId") == quote["sub_order_id"]
            and amount(refund.get("amount")) == amount(quote["amount"]),
            "Persisted refund amount/sub-order mismatch",
        )
        provider_row = by_provider_id.get(refund.get("providerRefundId"))
        expected_quote = next(
            row
            for row in session["fixture"]["refunds"]
            if row["sub_order_id"] == quote["sub_order_id"]
        )
        require(
            provider_row
            and provider_row.get("refund_id") == refund["id"]
            and provider_row.get("status") == "COMPLETED"
            and provider_row.get("currency") == expected_quote["currency"]
            and amount(provider_row.get("amount"))
            == amount(expected_quote["provider_amount"]),
            "Provider refund monetary snapshot mismatch",
        )
        operation = provider_row.get("backend_operation_id")
        require(
            isinstance(operation, str) and operation,
            "Missing attested backend operation",
        )
        wire_operation = (
            operation.replace("-", "") if args.provider == "vnpay" else operation
        )
        require(
            wire_operation == provider_row.get("provider_operation_id")
            and wire_operation not in operations,
            "Duplicate or mismatched attested refund operation",
        )
        operations.add(wire_operation)
        require(
            refund.get("paymentId") == session["payment_id"]
            and refund.get("provider") == args.provider.upper()
            and refund.get("status") == "PROCESSED"
            and refund.get("providerRefundId"),
            "Refund not verified PROCESSED",
        )
    refunded_amount = sum(amount(row["amount"]) for row in owned)
    require(
        refunded_amount <= amount(session["fixture"]["amount"]),
        "Refund exceeds payment",
    )
    expected_status = (
        "REFUNDED"
        if refunded_amount == amount(session["fixture"]["amount"])
        else "SUCCESS"
    )
    require(
        payment["status"] == expected_status,
        "Payment status disagrees with persisted refund total",
    )
    require(
        set(receipt.get("provider_refund_ids", []))
        == {r["providerRefundId"] for r in owned},
        "Provider refund receipt mismatch",
    )
    save_evidence(
        args,
        session,
        "verify",
        "PASSED",
        {
            "payment_id": session["payment_id"],
            "refund_ids": session["refund_ids"],
            "provider_receipt_sha256": hashlib.sha256(
                Path(receipt_path).read_bytes()
            ).hexdigest(),
            "backend_and_provider_completed": True,
            "operation_identity_source": "independent signed operator attestation",
        },
    )
    return 0


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "command", choices=("preflight", "create-intent", "capture", "verify", "refund")
    )
    parser.add_argument("--provider", choices=SUPPORTED_PROVIDERS, required=True)
    for name in ("base-url", "expected-revision"):
        parser.add_argument("--" + name, required=True)
    for name in (
        "token-file",
        "session",
        "evidence-dir",
        "deployment-manifest",
        "signers",
    ):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--provider-receipt", type=Path)
    parser.add_argument("--order-id")
    return parser


def main():
    args = build_parser().parse_args()
    try:
        return globals()["run_" + args.command.replace("-", "_")](args)
    except (AcceptanceError, OSError, ValueError, KeyError, TypeError) as error:
        print("Acceptance blocked/failed: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
