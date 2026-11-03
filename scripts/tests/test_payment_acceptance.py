import importlib.util
import json
import subprocess
import tempfile
import unittest
from argparse import Namespace
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch


class PaymentAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(__file__).resolve().parents[1] / "acceptance_payments.py"
        spec = importlib.util.spec_from_file_location("payment_runner", path)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)
        cls.keys = tempfile.TemporaryDirectory()
        cls.key = Path(cls.keys.name) / "operator"
        subprocess.run(
            ["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", str(cls.key)],
            check=True,
        )
        cls.signers = Path(cls.keys.name) / "signers"
        cls.signers.write_text(
            "sandbox-operator " + Path(str(cls.key) + ".pub").read_text()
        )

    @classmethod
    def tearDownClass(cls):
        cls.keys.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.args = Namespace(
            provider="paypal",
            base_url="https://sandbox.example.invalid",
            expected_revision="1" * 40,
            token_file=self.root / "token",
            session=self.root / "session",
            evidence_dir=self.root / "evidence",
            deployment_manifest=self.root / "manifest",
            signers=self.signers,
            order_id="owned-order",
            provider_receipt=None,
        )
        self.args.token_file.write_text("test-bearer-never-in-evidence")
        now = datetime.now(timezone.utc)
        self.manifest = {
            "environment": "sandbox",
            "revision": "1" * 40,
            "base_url": self.args.base_url,
            "attested_at": now.isoformat(),
            "expires_at": (now + timedelta(minutes=30)).isoformat(),
            "gateways": {
                "paypal": {"api_host": "api-m.sandbox.paypal.com"},
                "vnpay": {"host": "sandbox.vnpayment.vn"},
            },
            "fixture": {
                "order_id": "owned-order",
                "customer_id": "owned-customer",
                "amount": "500000",
                "provider_amount": "19.31",
                "provider_currency": "USD",
                "refunds": [
                    {
                        "sub_order_id": "owned-suborder",
                        "amount": "500000",
                        "provider_amount": "19.31",
                        "currency": "USD",
                    }
                ],
            },
        }
        self.sign(self.args.deployment_manifest, self.manifest)
        self.network = patch.object(
            self.module, "send_http_request", side_effect=self.respond
        ).start()
        self.addCleanup(patch.stopall)
        self.payment = {
            "id": "owned-payment",
            "masterOrderId": "owned-order",
            "provider": "PAYPAL",
            "amount": "500000",
            "status": "SUCCESS",
            "providerOrderId": "PROVIDER-ORDER-1",
            "providerTransactionId": "CAPTURE-1",
        }
        self.refund = {
            "id": "owned-refund",
            "paymentId": "owned-payment",
            "provider": "PAYPAL",
            "status": "PROCESSED",
            "providerRefundId": "PROVIDER-REFUND-1",
            "subOrderId": "owned-suborder",
            "amount": "500000",
        }
        self.intent = {
            "paymentId": "owned-payment",
            "orderId": "owned-order",
            "provider": "PAYPAL",
            "amount": "500000",
            "paymentReference": "https://www.sandbox.paypal.com/checkoutnow?token=PROVIDER-ORDER-1",
        }

    def sign(self, path, data, namespace="danasea-payment-sandbox"):
        path.write_text(json.dumps(data))
        Path(str(path) + ".sig").unlink(missing_ok=True)
        subprocess.run(
            [
                "ssh-keygen",
                "-Y",
                "sign",
                "-f",
                str(self.key),
                "-n",
                namespace,
                str(path),
            ],
            check=True,
            capture_output=True,
        )

    def respond(self, url, method="GET", headers=None, body=None):
        if url.endswith("/create-intent"):
            data = self.intent
        elif url.endswith("/refund-request"):
            data = [
                {
                    "id": "owned-refund",
                    "subOrderId": "owned-suborder",
                    "amount": "500000",
                    "status": "PENDING",
                }
            ]
        elif url.endswith("/refunds"):
            data = [self.refund]
        elif "/api/payments/" in url:
            data = self.payment
        else:
            data = {
                "id": "owned-order",
                "customerId": "owned-customer",
                "totalAmount": "500000",
                "discountAmount": "0",
            }
        return 200, data, 0.01

    def create(self):
        self.module.run_create_intent(self.args)
        return json.loads(self.args.session.read_text())

    def complete(self):
        self.create()
        self.module.run_capture(self.args)
        self.module.run_refund(self.args)
        session = json.loads(self.args.session.read_text())
        receipt = {
            key: session[key]
            for key in (
                "environment",
                "backend_revision",
                "run_id",
                "provider",
                "order_id",
                "payment_id",
                "provider_order_id",
                "provider_transaction_id",
            )
        }
        receipt.update(
            currency="USD",
            status="COMPLETED",
            amount="19.31",
            provider_refund_ids=["PROVIDER-REFUND-1"],
            refunds=[
                {
                    "refund_id": "owned-refund",
                    "provider_refund_id": "PROVIDER-REFUND-1",
                    "status": "COMPLETED",
                    "currency": "USD",
                    "amount": "19.31",
                    "backend_operation_id": "OP-1",
                    "provider_operation_id": "OP-1",
                }
            ],
        )
        self.args.provider_receipt = self.root / "receipt"
        self.sign(
            self.args.provider_receipt, receipt, "danasea-payment-provider-receipt"
        )
        self.payment["status"] = "REFUNDED"
        return receipt

    def test_preflight_verifies_real_signature_without_network(self):
        self.assertEqual(self.module.run_preflight(self.args), 0)
        self.network.assert_not_called()

    def test_missing_or_unsigned_attestation_fails_before_network(self):
        Path(str(self.args.deployment_manifest) + ".sig").unlink()
        with self.assertRaises(self.module.AcceptanceError):
            self.module.run_preflight(self.args)
        self.network.assert_not_called()

    def test_tampered_signed_manifest_fails_before_network(self):
        self.args.deployment_manifest.write_text(json.dumps(self.manifest) + " ")
        with self.assertRaisesRegex(self.module.AcceptanceError, "signature"):
            self.module.run_preflight(self.args)
        self.network.assert_not_called()

    def test_production_gateway_and_environment_rejected(self):
        for field, value in [
            ("environment", "production"),
            ("gateways", {"paypal": {"api_host": "api-m.paypal.com"}}),
        ]:
            with self.subTest(field=field):
                data = dict(self.manifest)
                data[field] = value
                self.sign(self.args.deployment_manifest, data)
                with self.assertRaises(self.module.AcceptanceError):
                    self.module.run_preflight(self.args)
        self.network.assert_not_called()

    def test_wrong_revision_target_and_expired_attestation_block(self):
        for change in (
            {"revision": "2" * 40},
            {"base_url": "https://other.invalid"},
            {"expires_at": "2020-01-01T00:00:00+00:00"},
        ):
            with self.subTest(change=change):
                self.sign(self.args.deployment_manifest, self.manifest | change)
                with self.assertRaises(self.module.AcceptanceError):
                    self.module.run_preflight(self.args)
        self.network.assert_not_called()

    def test_missing_token_fails_before_network(self):
        self.args.token_file.unlink()
        with self.assertRaises(self.module.AcceptanceError):
            self.module.run_preflight(self.args)
        self.network.assert_not_called()

    def test_create_uses_real_dto_names_and_private_session(self):
        session = self.create()
        self.assertEqual(session["approval_url"], self.intent["paymentReference"])
        self.assertEqual(session["provider_order_id"], "PROVIDER-ORDER-1")
        self.assertEqual(self.args.session.stat().st_mode & 0o777, 0o600)
        self.assertNotIn("test-bearer", self.args.session.read_text())
        self.assertNotIn(
            "checkoutnow", next(self.args.evidence_dir.glob("*.json")).read_text()
        )

    def test_arbitrary_order_cannot_create_intent(self):
        self.args.order_id = "foreign"
        with self.assertRaises(self.module.AcceptanceError):
            self.create()
        self.network.assert_not_called()

    def test_create_replay_reuses_idempotency_and_owned_payment(self):
        first = self.create()
        second = self.create()
        self.assertEqual(first["idempotency_keys"], second["idempotency_keys"])
        self.intent["paymentId"] = "duplicate"
        with self.assertRaisesRegex(self.module.AcceptanceError, "different payment"):
            self.create()

    def test_missing_or_production_approval_url_is_rejected(self):
        for url in (None, "https://www.paypal.com/checkoutnow?token=bad"):
            self.intent["paymentReference"] = url
            with self.assertRaises(self.module.AcceptanceError):
                self.create()

    def test_capture_rejects_pending_and_each_payment_binding_mismatch(self):
        self.create()
        original = dict(self.payment)
        for field, value in [
            ("id", "other"),
            ("masterOrderId", "other"),
            ("provider", "VNPAY"),
            ("amount", "1"),
            ("status", "PENDING"),
            ("providerOrderId", "other"),
        ]:
            with self.subTest(field=field):
                self.payment = original | {field: value}
                with self.assertRaises(self.module.AcceptanceError):
                    self.module.run_capture(self.args)
        self.assertNotIn("capture_verified", json.loads(self.args.session.read_text()))

    def test_session_cannot_move_to_another_backend(self):
        self.create()
        session = json.loads(self.args.session.read_text())
        session["base_url"] = "https://other.invalid"
        self.args.session.write_text(json.dumps(session))
        with self.assertRaisesRegex(self.module.AcceptanceError, "another backend"):
            self.module.run_capture(self.args)

    def test_refund_requires_verified_capture_and_stores_real_id(self):
        self.create()
        with self.assertRaises(self.module.AcceptanceError):
            self.module.run_refund(self.args)
        self.module.run_capture(self.args)
        self.module.run_refund(self.args)
        self.assertEqual(
            json.loads(self.args.session.read_text())["refund_ids"], ["owned-refund"]
        )

    def test_verify_without_independent_provider_receipt_is_blocked(self):
        self.create()
        with self.assertRaisesRegex(self.module.AcceptanceError, "receipt"):
            self.module.run_verify(self.args)

    def test_complete_lifecycle_requires_provider_and_persisted_refund(self):
        self.complete()
        self.assertEqual(self.module.run_verify(self.args), 0)
        self.refund["status"] = "PENDING"
        with self.assertRaisesRegex(self.module.AcceptanceError, "PROCESSED"):
            self.module.run_verify(self.args)

    def test_receipt_wrong_currency_amount_and_ids_are_rejected(self):
        receipt = self.complete()
        for field, value in [
            ("currency", "EUR"),
            ("amount", "1"),
            ("payment_id", "foreign"),
            ("provider_refund_ids", ["foreign"]),
        ]:
            with self.subTest(field=field):
                self.sign(
                    self.args.provider_receipt,
                    receipt | {field: value},
                    "danasea-payment-provider-receipt",
                )
                with self.assertRaises(self.module.AcceptanceError):
                    self.module.run_verify(self.args)

    def test_discounted_order_total_is_already_final_payable(self):
        original = self.respond

        def response(url, method="GET", headers=None, body=None):
            if url.endswith("/api/orders/owned-order"):
                return (
                    200,
                    {
                        "id": "owned-order",
                        "customerId": "owned-customer",
                        "totalAmount": "500000",
                        "discountAmount": "100000",
                    },
                    0.01,
                )
            return original(url, method, headers, body)

        self.network.side_effect = response
        self.assertEqual(self.create()["payment_id"], "owned-payment")

    def test_full_refund_requires_refunded_payment_and_exact_refund_amount(self):
        self.complete()
        self.payment["status"] = "REFUNDED"
        self.assertEqual(self.module.run_verify(self.args), 0)
        self.refund["amount"] = "1"
        with self.assertRaises(self.module.AcceptanceError):
            self.module.run_verify(self.args)

    def test_vnpay_roundtrip_binds_normalized_wire_operation(self):
        self.args.provider = "vnpay"
        fixture = self.manifest["fixture"]
        fixture.update(provider_currency="VND", provider_amount="500000")
        fixture["refunds"][0].update(currency="VND", provider_amount="500000")
        self.sign(self.args.deployment_manifest, self.manifest)
        self.payment.update(provider="VNPAY", providerOrderId="owned-payment")
        self.intent.update(
            provider="VNPAY",
            paymentReference="https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=owned-payment",
        )
        self.refund["provider"] = "VNPAY"
        receipt = self.complete()
        receipt.update(currency="VND", amount="500000")
        receipt["refunds"][0].update(
            currency="VND",
            amount="500000",
            backend_operation_id="324e13a2-884c-4a30-b407-aeec27b1b1b0",
            provider_operation_id="324e13a2884c4a30b407aeec27b1b1b0",
        )
        self.sign(
            self.args.provider_receipt, receipt, "danasea-payment-provider-receipt"
        )
        self.assertEqual(self.module.run_verify(self.args), 0)
        self.assertFalse(
            any(
                "/paypal/capture" in call.args[0]
                for call in self.network.call_args_list
            )
        )

    def test_capture_identity_cannot_change_between_phases(self):
        self.complete()
        self.payment["providerTransactionId"] = "FOREIGN-TRANSACTION"
        with self.assertRaisesRegex(self.module.AcceptanceError, "transaction ID"):
            self.module.run_verify(self.args)

    def test_nonfinite_money_and_remote_http_are_rejected(self):
        for value in ("NaN", "Infinity", "0", "-1"):
            with self.assertRaises(self.module.AcceptanceError):
                self.module.amount(value)
        with self.assertRaises(self.module.AcceptanceError):
            self.module.validate_url("http://sandbox.example.invalid")


class DuplicateRefundRegressionTest(unittest.TestCase):
    setUp = PaymentAcceptanceTest.setUp
    sign = PaymentAcceptanceTest.sign
    respond = PaymentAcceptanceTest.respond
    create = PaymentAcceptanceTest.create
    complete = PaymentAcceptanceTest.complete

    @classmethod
    def setUpClass(cls):
        PaymentAcceptanceTest.setUpClass.__func__(cls)

    @classmethod
    def tearDownClass(cls):
        PaymentAcceptanceTest.tearDownClass.__func__(cls)

    def test_additional_financial_refund_cannot_be_ignored(self):
        self.complete()
        original = self.respond

        def response(url, method="GET", headers=None, body=None):
            if url.endswith("/refunds"):
                return (
                    200,
                    [
                        self.refund,
                        self.refund
                        | {"id": "duplicate-refund", "providerRefundId": "EXTRA"},
                    ],
                    0.01,
                )
            return original(url, method, headers, body)

        self.network.side_effect = response
        with self.assertRaises(self.module.AcceptanceError):
            self.module.run_verify(self.args)

    def test_completed_refund_phase_retry_reuses_the_owned_operation(self):
        self.complete()
        before = json.loads(self.args.session.read_text())
        self.assertEqual(self.module.run_refund(self.args), 0)
        after = json.loads(self.args.session.read_text())
        self.assertEqual(before["refund_ids"], after["refund_ids"])
        self.assertEqual(before["idempotency_keys"], after["idempotency_keys"])
