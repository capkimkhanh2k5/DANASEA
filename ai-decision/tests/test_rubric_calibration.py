"""Contract, taxonomy, and fixture integrity tests for Quyet rubric calibration."""

from __future__ import annotations

import json
import os
import unittest
from pathlib import Path

from quyet.questions import parse_questions

from config.rubrics import RUBRICS, VERSION

AI_DECISION_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = AI_DECISION_DIR.parent
CASES_PATH = AI_DECISION_DIR / "tests/fixtures/quyet-small/cases.json"
CALIBRATION_HOLDOUT_PATH = (
    AI_DECISION_DIR / "tests/fixtures/quyet-small/calibration_holdout.json"
)
ADAPTER_PATH = (
    REPO_ROOT
    / "backend/src/main/java/com/danasea/backend/modules/ai/infrastructure/quyet/QuyetDecisionAdapter.java"
)
ADAPTER_TEST_PATH = (
    REPO_ROOT
    / "backend/src/test/java/com/danasea/backend/modules/ai/QuyetDecisionAdapterTest.java"
)

UNIFIED_INTENT_LABELS = {
    "SEARCH",
    "BOOKING",
    "WEATHER",
    "COMPARE",
    "ITINERARY",
    "SUPPORT",
    "REVIEW",
    "NEARBY",
    "OTHER",
}


class RubricCalibrationContractTest(unittest.TestCase):
    def test_all_server_rubrics_parse_cleanly(self) -> None:
        self.assertEqual(
            set(RUBRICS),
            {
                "intent",
                "service_category",
                "review",
                "moderation",
                "relevance",
                "complaint",
                "risk",
            },
        )
        for task, questions in RUBRICS.items():
            parsed = parse_questions(questions)
            self.assertTrue(parsed, msg=f"Empty parsed questions for task {task}")

    def test_unified_nine_label_intent_contract_matches_cases_and_adapter(self) -> None:
        server_intent = set(RUBRICS["intent"]["intent"]["criteria"])
        self.assertEqual(server_intent, UNIFIED_INTENT_LABELS)

        cases_fixture = json.loads(CASES_PATH.read_text(encoding="utf-8"))
        fixture_intent = set(
            cases_fixture["question_sets"]["intent"]["intent"]["criteria"]
        )
        self.assertEqual(fixture_intent, UNIFIED_INTENT_LABELS)
        self.assertNotIn("UNCLEAR", fixture_intent)

        intent14 = next(
            row for row in cases_fixture["cases"] if row["id"] == "intent-14"
        )
        self.assertEqual(intent14["expected"], {"intent": "OTHER"})
        self.assertEqual(intent14["legacy_expected"], {"intent": "UNCLEAR"})

        if ADAPTER_PATH.is_file():
            adapter_text = ADAPTER_PATH.read_text(encoding="utf-8")
            self.assertIn(f'RUBRIC_VERSION = "{VERSION}"', adapter_text)
            for label in UNIFIED_INTENT_LABELS:
                self.assertIn(f'"{label}"', adapter_text)
        if ADAPTER_TEST_PATH.is_file():
            adapter_test_text = ADAPTER_TEST_PATH.read_text(encoding="utf-8")
            self.assertIn(VERSION, adapter_test_text)

    def test_calibration_and_holdout_fixture_integrity_and_disjointness(self) -> None:
        suite = json.loads(CALIBRATION_HOLDOUT_PATH.read_text(encoding="utf-8"))
        cal = suite["calibration"]
        holdout = suite["holdout"]

        self.assertGreaterEqual(len(cal), 40)
        self.assertGreaterEqual(len(holdout), 30)

        cal_ids = [row["id"] for row in cal]
        hold_ids = [row["id"] for row in holdout]
        self.assertEqual(len(cal_ids), len(set(cal_ids)))
        self.assertEqual(len(hold_ids), len(set(hold_ids)))
        self.assertFalse(set(cal_ids) & set(hold_ids))

        def state_text(row: dict) -> str:
            return json.dumps(row["state"], ensure_ascii=False, sort_keys=True)

        cal_states = {state_text(row) for row in cal}
        hold_states = {state_text(row) for row in holdout}
        self.assertFalse(cal_states & hold_states)

        legacy_ab = [row for row in cal if row.get("legacy_ab_29")]
        self.assertEqual(len(legacy_ab), 29)
        self.assertEqual(
            sum(len(row["legacy_expected"]) for row in legacy_ab),
            37,
        )

        # Ensure unified intent labels are valid and holdout covers REVIEW/NEARBY/moderation slices
        holdout_intents = {
            row["expected"]["intent"] for row in holdout if row["task"] == "intent"
        }
        self.assertEqual(holdout_intents, UNIFIED_INTENT_LABELS)

        for row in cal + holdout:
            self.assertIn(row["task"], RUBRICS)
            expected_keys = set(row["expected"])
            self.assertTrue(expected_keys.issubset(set(RUBRICS[row["task"]])))

    @unittest.skipUnless(
        os.getenv("QUYET_RUN_CALIBRATION_TESTS") == "1",
        "Opt-in real Quyet Small calibration regression check",
    )
    def test_real_model_calibration_and_holdout_thresholds(self) -> None:
        import torch
        import quyet
        from scripts.evaluate_rubrics import (
            compute_legacy_ab_report,
            evaluate_split,
        )
        from app.quyet_service import DEFAULT_MODEL

        suite = json.loads(CALIBRATION_HOLDOUT_PATH.read_text(encoding="utf-8"))
        model_dir = os.getenv("QUYET_MODEL_DIR", str(DEFAULT_MODEL))
        torch.set_num_threads(4)
        torch.set_num_interop_threads(1)
        model = quyet.load(model_dir, device="cpu")

        cal_report = evaluate_split(model, suite["calibration"], "cpu", 0.5)
        hold_report = evaluate_split(model, suite["holdout"], "cpu", 0.5)
        ab = compute_legacy_ab_report(
            cal_report["rows"], suite["taxonomy"]["legacy_ab_baseline_reference"]
        )

        self.assertEqual(ab["baseline_passing_row_regressions"], [])
        self.assertGreaterEqual(ab["unified_taxonomy_37_labels"]["labels_matched"], 36)
        self.assertGreaterEqual(cal_report["summary"]["label_accuracy"], 0.94)
        self.assertGreaterEqual(hold_report["summary"]["label_accuracy"], 0.80)
        self.assertGreaterEqual(
            hold_report["by_task"]["moderation"]["label_accuracy"], 0.90
        )


if __name__ == "__main__":
    unittest.main()
