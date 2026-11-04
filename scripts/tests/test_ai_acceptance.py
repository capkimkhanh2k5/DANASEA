import importlib.util
import tempfile
import unittest
from pathlib import Path


class AiAcceptanceValidationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        script_path = Path(__file__).resolve().parents[1] / "ai" / "check_ai_acceptance.py"
        spec = importlib.util.spec_from_file_location("check_ai_acceptance", script_path)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def base_valid_evidence(self):
        from scripts.ai_evidence import WORKER_TASKS, evidence
        return evidence('java-worker-acceptance', '4e03b8e6f3b73a598dc3042d9a3c8c351d50991a', [
            {'case_name': name, 'status': 'PASSED', 'requested_model': 'chinhnc/Quyet-1.0-Small',
             'model_revision': '233167bba5df61b5375a522bf8a042d8d2189379',
             'suggestion_only': True, 'latency_ms': 12.5} for name in WORKER_TASKS], 1.0)

    def test_valid_evidence_passes(self):
        evidence = self.base_valid_evidence()
        self.module.validate_ai_evidence(evidence)

    def test_missing_backend_revision_is_rejected(self):
        evidence = self.base_valid_evidence()
        evidence["backend_revision"] = "not_a_sha!"
        with self.assertRaises(self.module.AiAcceptanceValidationError) as ctx:
            self.module.validate_ai_evidence(evidence)
        self.assertIn("Missing or invalid backend_revision", str(ctx.exception))

    def test_case_count_mismatch_is_rejected(self):
        evidence = self.base_valid_evidence()
        evidence["executed_cases"] = 2  # Declares 2, but only 1 in array
        with self.assertRaises(self.module.AiAcceptanceValidationError) as ctx:
            self.module.validate_ai_evidence(evidence)
        self.assertIn("does not match executed_cases", str(ctx.exception))

    def test_failed_case_with_technical_pass_true_is_rejected(self):
        evidence = self.base_valid_evidence()
        evidence["case_outcomes"][0]["status"] = "FAILED"
        evidence["technical_pass"] = True
        with self.assertRaises(self.module.AiAcceptanceValidationError) as ctx:
            self.module.validate_ai_evidence(evidence)
        self.assertIn("technical_pass is True, but case failed", str(ctx.exception))

    def test_fallback_misrepresented_as_direct_is_rejected(self):
        evidence = self.base_valid_evidence()
        evidence["fallback_occurred"] = True
        evidence["route"] = "direct"
        with self.assertRaises(self.module.AiAcceptanceValidationError) as ctx:
            self.module.validate_ai_evidence(evidence)
        self.assertIn("fallback occurred, but route is labeled direct", str(ctx.exception))

    def test_untracked_cost_falsely_recorded_as_zero_is_rejected(self):
        evidence = self.base_valid_evidence()
        # total_tokens is null, but cost_usd is set to 0 instead of null
        evidence["token_provenance"] = {
            "prompt_tokens": None, "completion_tokens": None, "total_tokens": None,
            "cost_usd": 0,
        }
        with self.assertRaises(self.module.AiAcceptanceValidationError) as ctx:
            self.module.validate_ai_evidence(evidence)
        self.assertIn("Cost cannot be 0 when token usage is not measured; must be null", str(ctx.exception))

    def test_non_suggestion_only_is_rejected(self):
        evidence = self.base_valid_evidence()
        evidence["business_evaluation"]["suggestion_only"] = False
        with self.assertRaises(self.module.AiAcceptanceValidationError) as ctx:
            self.module.validate_ai_evidence(evidence)
        self.assertIn("suggestion_only", str(ctx.exception))

    def test_check_file_integration(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            import json
            json.dump(self.base_valid_evidence(), f)
            f.flush()
            parsed = self.module.check_file(Path(f.name))
            self.assertEqual(parsed["status"], "PASSED")



class AiEvidenceRegressionTest(unittest.TestCase):
    base_valid_evidence = AiAcceptanceValidationTest.base_valid_evidence

    @classmethod
    def setUpClass(cls):
        AiAcceptanceValidationTest.setUpClass.__func__(cls)

    def test_failed_technical_result_cannot_be_passed(self):
        data = self.base_valid_evidence()
        data['technical_pass'] = False
        data['case_outcomes'][0]['status'] = 'FAILED'
        with self.assertRaises(self.module.AiAcceptanceValidationError):
            self.module.validate_ai_evidence(data)

    def test_non_200_response_cannot_be_success(self):
        data = self.base_valid_evidence()
        data['case_outcomes'][0]['http_status'] = 500
        with self.assertRaises(self.module.AiAcceptanceValidationError):
            self.module.validate_ai_evidence(data)

    def test_missing_worker_revision_is_rejected(self):
        data = self.base_valid_evidence()
        data.pop('model_revision')
        with self.assertRaises(self.module.AiAcceptanceValidationError):
            self.module.validate_ai_evidence(data)


if __name__ == '__main__':
    unittest.main()
