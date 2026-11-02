import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


class FinancialMutationsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        script_path = Path(__file__).resolve().parents[1] / "verify_financial_mutations.py"
        spec = importlib.util.spec_from_file_location("verify_financial_mutations", script_path)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_baseline_failure_is_not_killed(self):
        backend_dir = Path(__file__).resolve().parents[2] / "backend"
        spec = {
            "mutation": "mock-mutation",
            "production_source": "src/main/java/com/danasea/backend/modules/order/application/PaymentWebhookSigner.java",
            "test_class": "com.danasea.backend.modules.order.application.PaymentWebhookSignerTest",
            "target": "mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))",
            "replacement": 'mac.doFinal("constant".getBytes(StandardCharsets.UTF_8))',
        }
        with patch.object(self.module, "run_launcher", return_value={"found": 10, "passed": 8, "failed": 2, "skipped": 0, "aborted": 0}):
            outcome = self.module.execute_mutation_proof(spec, backend_dir, "fake-cp")
            self.assertFalse(outcome["killed"])
            self.assertIn("Baseline test did not pass cleanly", outcome.get("error", ""))

    def test_pattern_mismatch_zero_or_multiple_times_is_inconclusive(self):
        backend_dir = Path(__file__).resolve().parents[2] / "backend"
        spec = {
            "mutation": "mock-mutation",
            "production_source": "src/main/java/com/danasea/backend/modules/order/application/PaymentWebhookSigner.java",
            "test_class": "com.danasea.backend.modules.order.application.PaymentWebhookSignerTest",
            "target": "non_existent_target_pattern_in_source",
            "replacement": "replacement",
        }
        with patch.object(self.module, "run_launcher", return_value={"found": 13, "passed": 13, "failed": 0, "skipped": 0, "aborted": 0}):
            outcome = self.module.execute_mutation_proof(spec, backend_dir, "fake-cp")
            self.assertFalse(outcome["killed"])
            self.assertIn("Target replacement pattern found 0 times", outcome.get("error", ""))

    def test_compile_error_is_inconclusive_and_not_killed(self):
        backend_dir = Path(__file__).resolve().parents[2] / "backend"
        spec = {
            "mutation": "mock-mutation",
            "production_source": "src/main/java/com/danasea/backend/modules/order/application/PaymentWebhookSigner.java",
            "test_class": "com.danasea.backend.modules.order.application.PaymentWebhookSignerTest",
            "target": "mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))",
            "replacement": "syntax error !!! invalid java",
        }
        with patch.object(self.module, "run_launcher", return_value={"found": 13, "passed": 13, "failed": 0, "skipped": 0, "aborted": 0}):
            outcome = self.module.execute_mutation_proof(spec, backend_dir, "fake-cp")
            self.assertFalse(outcome["killed"])
            self.assertIn("Mutant compilation failed", outcome.get("error", ""))

    def test_mutant_with_aborted_or_skipped_tests_is_not_killed(self):
        backend_dir = Path(__file__).resolve().parents[2] / "backend"
        spec = {
            "mutation": "mock-mutation",
            "production_source": "src/main/java/com/danasea/backend/modules/order/application/PaymentWebhookSigner.java",
            "test_class": "com.danasea.backend.modules.order.application.PaymentWebhookSignerTest",
            "target": "mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))",
            "replacement": 'mac.doFinal("constant".getBytes(StandardCharsets.UTF_8))',
        }
        # Baseline passes
        baseline_res = {"found": 13, "passed": 13, "failed": 0, "skipped": 0, "aborted": 0}
        # Mutant has skipped tests or aborted
        mutant_res = {"found": 13, "passed": 10, "failed": 2, "skipped": 1, "aborted": 0, "failures": [{"type": "AssertionError"}]}

        with patch.object(self.module, "compile_mutant"):
            with patch.object(self.module, "run_launcher", side_effect=[baseline_res, mutant_res]):
                outcome = self.module.execute_mutation_proof(spec, backend_dir, "fake-cp")
                self.assertFalse(outcome["killed"])

    def test_mutant_with_assertion_failure_and_clean_run_is_killed(self):
        backend_dir = Path(__file__).resolve().parents[2] / "backend"
        spec = {
            "mutation": "mock-mutation",
            "production_source": "src/main/java/com/danasea/backend/modules/order/application/PaymentWebhookSigner.java",
            "test_class": "com.danasea.backend.modules.order.application.PaymentWebhookSignerTest",
            "target": "mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))",
            "replacement": 'mac.doFinal("constant".getBytes(StandardCharsets.UTF_8))',
        }
        baseline_res = {"found": 13, "passed": 13, "failed": 0, "skipped": 0, "aborted": 0}
        mutant_res = {
            "found": 13,
            "passed": 10,
            "failed": 3,
            "skipped": 0,
            "aborted": 0,
            "failures": [{"test": "sign_ValidPayload", "type": "AssertionError", "message": "expected true but was false"}],
        }

        with patch.object(self.module, "compile_mutant"):
            with patch.object(self.module, "run_launcher", side_effect=[baseline_res, mutant_res]):
                outcome = self.module.execute_mutation_proof(spec, backend_dir, "fake-cp")
                self.assertTrue(outcome["killed"])
                self.assertEqual(outcome["mutant"]["failed"], 3)


if __name__ == "__main__":
    unittest.main()
