import importlib.util
import json
import tempfile
import threading
import unittest
from argparse import Namespace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class PaymentAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        script_path = Path(__file__).resolve().parents[1] / "acceptance_payments.py"
        spec = importlib.util.spec_from_file_location("acceptance_payments", script_path)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_missing_deployment_manifest_is_rejected(self):
        missing_path = Path("/nonexistent/manifest.json")
        with self.assertRaises(self.module.AcceptanceError) as ctx:
            self.module.validate_deployment_manifest(missing_path, "paypal")
        self.assertIn("not found", str(ctx.exception))

    def test_manifest_environment_must_be_sandbox(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            json.dump({"environment": "production", "revision": "abcdef1234567"}, f)
            f.flush()
            with self.assertRaises(self.module.AcceptanceError) as ctx:
                self.module.validate_deployment_manifest(Path(f.name), "paypal")
            self.assertIn("must be 'sandbox'", str(ctx.exception))

    def test_manifest_missing_or_invalid_revision_is_rejected(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            json.dump({"environment": "sandbox", "revision": "invalid!sha"}, f)
            f.flush()
            with self.assertRaises(self.module.AcceptanceError) as ctx:
                self.module.validate_deployment_manifest(Path(f.name), "paypal")
            self.assertIn("missing or invalid backend revision", str(ctx.exception))

    def test_paypal_api_host_must_be_sandbox_host(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            json.dump({
                "environment": "sandbox",
                "revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
                "gateways": {
                    "paypal": {
                        "api_host": "api-m.paypal.com"  # Production host!
                    }
                }
            }, f)
            f.flush()
            with self.assertRaises(self.module.AcceptanceError) as ctx:
                self.module.validate_deployment_manifest(Path(f.name), "paypal")
            self.assertIn("api-m.sandbox.paypal.com", str(ctx.exception))

    def test_vnpay_host_must_be_sandbox_host(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            json.dump({
                "environment": "sandbox",
                "revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
                "gateways": {
                    "vnpay": {
                        "host": "vnpayment.vn"  # Production host!
                    }
                }
            }, f)
            f.flush()
            with self.assertRaises(self.module.AcceptanceError) as ctx:
                self.module.validate_deployment_manifest(Path(f.name), "vnpay")
            self.assertIn("sandbox.vnpayment.vn", str(ctx.exception))

    def test_empty_or_missing_token_file_is_rejected(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            f.write("   \n")
            f.flush()
            with self.assertRaises(self.module.AcceptanceError) as ctx:
                self.module.read_customer_token(Path(f.name))
            self.assertIn("empty", str(ctx.exception))

        with self.assertRaises(self.module.AcceptanceError):
            self.module.read_customer_token(Path("/nonexistent/token"))

    def test_session_from_different_backend_revision_is_rejected(self):
        manifest = {
            "environment": "sandbox",
            "revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
            "gateways": {"paypal": {"api_host": "api-m.sandbox.paypal.com"}}
        }
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            json.dump({
                "schema_version": 1,
                "environment": "sandbox",
                "backend_revision": "1111111111111111111111111111111111111111",  # Different revision
                "provider": "paypal",
                "order_id": "11111111-1111-1111-1111-111111111111",
                "payment_id": "22222222-2222-2222-2222-222222222222",
            }, f)
            f.flush()
            with self.assertRaises(self.module.AcceptanceError) as ctx:
                self.module.validate_session(Path(f.name), manifest, "paypal")
            self.assertIn("does not match deployment manifest", str(ctx.exception))

    def test_session_with_different_provider_is_rejected(self):
        manifest = {
            "environment": "sandbox",
            "revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
            "gateways": {"paypal": {"api_host": "api-m.sandbox.paypal.com"}}
        }
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            json.dump({
                "schema_version": 1,
                "environment": "sandbox",
                "backend_revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
                "provider": "vnpay",  # Mismatch: expected paypal
                "order_id": "11111111-1111-1111-1111-111111111111",
                "payment_id": "22222222-2222-2222-2222-222222222222",
            }, f)
            f.flush()
            with self.assertRaises(self.module.AcceptanceError) as ctx:
                self.module.validate_session(Path(f.name), manifest, "paypal")
            self.assertIn("Session provider mismatch", str(ctx.exception))

    def test_sanitize_evidence_redacts_tokens_and_secrets(self):
        sensitive_data = {
            "access_token": "secret_bearer_token_12345",
            "client_secret": "my_super_secret_key",
            "nested": {
                "auth_token": "token_nested_xyz",
                "api_key": "raw_api_key",
                "amount": "19.31",
                "currency": "USD"
            },
            "headers": ["Authorization: Bearer secret_123"]
        }
        sanitized = self.module.sanitize_evidence(sensitive_data)
        self.assertEqual(sanitized["access_token"], "[REDACTED]")
        self.assertEqual(sanitized["client_secret"], "[REDACTED]")
        self.assertEqual(sanitized["nested"]["auth_token"], "[REDACTED]")
        self.assertEqual(sanitized["nested"]["api_key"], "[REDACTED]")
        self.assertEqual(sanitized["nested"]["amount"], "19.31")
        self.assertEqual(sanitized["nested"]["currency"], "USD")

    def test_validate_url_rejects_embedded_credentials(self):
        with self.assertRaises(self.module.AcceptanceError):
            self.module.validate_url("http://user:pass@localhost:8080")
        self.assertEqual(self.module.validate_url("https://sandbox.danasea.com/"), "https://sandbox.danasea.com")

    def test_preflight_end_to_end_succeeds_and_produces_evidence(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            manifest_file = temp_path / "manifest.json"
            token_file = temp_path / "token.txt"
            session_file = temp_path / "session.json"
            evidence_dir = temp_path / "evidence"

            manifest_file.write_text(json.dumps({
                "environment": "sandbox",
                "revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
                "gateways": {"paypal": {"api_host": "api-m.sandbox.paypal.com"}}
            }), encoding="utf-8")
            token_file.write_text("test-customer-jwt-token", encoding="utf-8")

            args = Namespace(
                provider="paypal",
                base_url="https://sandbox.danasea.com",
                token_file=token_file,
                session=session_file,
                evidence_dir=evidence_dir,
                deployment_manifest=manifest_file
            )

            exit_code = self.module.run_preflight(args)
            self.assertEqual(exit_code, 0)
            evidence_path = evidence_dir / "preflight-paypal.json"
            self.assertTrue(evidence_path.is_file())
            evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
            self.assertEqual(evidence["schema_version"], 1)
            self.assertEqual(evidence["preflight_status"], "PASSED")
            self.assertIn("sandbox_environment_verified", evidence["checks"])

    def test_create_intent_with_mock_backend(self):
        class MockHandler(BaseHTTPRequestHandler):
            def do_POST(self):
                if "/create-intent" in self.path:
                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({
                        "paymentId": "550e8400-e29b-41d4-a716-446655440000",
                        "orderId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                        "provider": "PAYPAL",
                        "amount": 19.31,
                        "reference": "https://www.sandbox.paypal.com/checkoutnow?token=EC-SANDBOX123"
                    }).encode("utf-8"))
                else:
                    self.send_response(404)
                    self.end_headers()

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), MockHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()

        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                temp_path = Path(temp_dir)
                manifest_file = temp_path / "manifest.json"
                token_file = temp_path / "token.txt"
                session_file = temp_path / "session.json"
                evidence_dir = temp_path / "evidence"

                manifest_file.write_text(json.dumps({
                    "environment": "sandbox",
                    "revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
                    "gateways": {"paypal": {"api_host": "api-m.sandbox.paypal.com"}}
                }), encoding="utf-8")
                token_file.write_text("test-customer-jwt-token", encoding="utf-8")

                args = Namespace(
                    provider="paypal",
                    base_url=f"http://127.0.0.1:{server.server_port}",
                    token_file=token_file,
                    session=session_file,
                    evidence_dir=evidence_dir,
                    deployment_manifest=manifest_file,
                    order_id="6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                )

                exit_code = self.module.run_create_intent(args)
                self.assertEqual(exit_code, 0)
                self.assertTrue(session_file.is_file())
                session_data = json.loads(session_file.read_text(encoding="utf-8"))
                self.assertEqual(session_data["payment_id"], "550e8400-e29b-41d4-a716-446655440000")
                self.assertIn("sandbox.paypal.com", session_data["approval_url"])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_missing_payment_id_or_http_failure_raises_acceptance_error(self):
        class FailHandler(BaseHTTPRequestHandler):
            def do_POST(self):
                body = b'{"error": "Internal Server Error"}'
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), FailHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()

        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                temp_path = Path(temp_dir)
                manifest_file = temp_path / "manifest.json"
                token_file = temp_path / "token.txt"
                session_file = temp_path / "session.json"
                evidence_dir = temp_path / "evidence"

                manifest_file.write_text(json.dumps({
                    "environment": "sandbox",
                    "revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
                    "gateways": {"paypal": {"api_host": "api-m.sandbox.paypal.com"}}
                }), encoding="utf-8")
                token_file.write_text("test-customer-jwt-token", encoding="utf-8")

                args = Namespace(
                    provider="paypal",
                    base_url=f"http://127.0.0.1:{server.server_port}",
                    token_file=token_file,
                    session=session_file,
                    evidence_dir=evidence_dir,
                    deployment_manifest=manifest_file,
                    order_id="6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                )

                with self.assertRaises(self.module.AcceptanceError) as ctx:
                    self.module.run_create_intent(args)
                self.assertIn("create-intent failed with HTTP 500", str(ctx.exception))
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
