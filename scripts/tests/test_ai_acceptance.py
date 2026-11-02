import importlib.util
import tempfile
import unittest
from pathlib import Path


class AiAcceptanceValidationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        script_path = Path(__file__).resolve().parents[1] / "check_ai_acceptance.py"
        spec = importlib.util.spec_from_file_location("check_ai_acceptance", script_path)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def base_valid_evidence(self):
        return {
            "schema_version": 1,
            "phase": "java-worker-acceptance",
            "backend_revision": "4e03b8e6f3b73a598dc3042d9a3c8c351d50991a",
            "model_revision": "233167bba5df61b5375a522bf8a042d8d2189379",
            "requested_model": "chinhnc/Quyet-1.0-Small",
            "actual_provider": "quyet-small-local",
            "route": "direct",
            "technical_pass": True,
            "business_evaluation": {
                "suggestion_only": True,
                "constraint_preservation": True,
                "policy_boundary_respected": True,
            },
            "executed_cases": 1,
            "case_outcomes": [
                {"case_name": "RISK", "status": "PASSED", "latency_ms": 12.5}
            ],
            "token_provenance": {
                "prompt_tokens": None,
                "completion_tokens": None,
                "total_tokens": None,
                "cost_usd": None,
            },
            "status": "PASSED",
        }

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
        self.assertIn("technical_pass is True, but case 'RISK' failed", str(ctx.exception))

    def test_fallback_misrepresented_as_direct_is_rejected(self):
        evidence = self.base_valid_evidence()
        evidence["fallback_occurred"] = True
        evidence["route"] = "direct"
        with self.assertRaises(self.module.AiAcceptanceValidationError) as ctx:
            self.module.validate_ai_evidence(evidence)
        self.assertIn("fallback occurred, but route is labeled 'direct'", str(ctx.exception))

    def test_untracked_cost_falsely_recorded_as_zero_is_rejected(self):
        evidence = self.base_valid_evidence()
        # total_tokens is null, but cost_usd is set to 0 instead of null
        evidence["token_provenance"] = {
            "total_tokens": None,
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
        self.assertIn("AI decisions must be suggestion-only", str(ctx.exception))

    def test_check_file_integration(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            import json
            json.dump(self.base_valid_evidence(), f)
            f.flush()
            parsed = self.module.check_file(Path(f.name))
            self.assertEqual(parsed["status"], "PASSED")


if __name__ == "__main__":
    unittest.main()
