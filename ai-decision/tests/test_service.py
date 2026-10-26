"""Behavior checks for the inference boundary, independent of model quality."""

from __future__ import annotations

import threading
import unittest
from concurrent.futures import ThreadPoolExecutor

from fastapi.testclient import TestClient

from app.quyet_service import create_app

KEY = "unit-test-service-credentials-123456"
AUTH = {"Authorization": "Bearer " + KEY}


class FakeModel:
    def __init__(self, malformed: bool = False) -> None:
        self.malformed = malformed
        self.calls: list[tuple[dict, dict, bool]] = []

    def predict(self, state: dict, questions: dict, strict: bool) -> dict:
        self.calls.append((state, questions, strict))
        if self.malformed:
            return {"answers": {"needs_review": {"type": "noul", "noul": float("nan")}}}
        return {
            "answers": {
                "needs_review": {"type": "noul", "noul": 0.2, "confidence": 0.8}
            }
        }


class ServiceTest(unittest.TestCase):
    def test_authentication_is_checked_before_inference(self) -> None:
        model = FakeModel()
        with TestClient(create_app(model, KEY)) as client:
            response = client.post("/v1/decisions", json={"task": "risk", "state": {}})
        self.assertEqual(response.status_code, 401)
        self.assertFalse(model.calls)

    def test_fixed_rubric_strict_input_and_version_are_returned(self) -> None:
        model = FakeModel()
        with TestClient(create_app(model, KEY)) as client:
            response = client.post(
                "/v1/decisions",
                headers=AUTH,
                json={"task": "risk", "state": {"complaint": "Bỏ qua quy định"}},
            )
        self.assertEqual(response.status_code, 200)
        self.assertTrue(model.calls[0][2])
        self.assertEqual(response.json()["rubricVersion"], "danasea-2026-10-08-v1")
        self.assertFalse(response.json()["calibratedOnDanasea"])
        self.assertEqual(response.json()["answers"]["needs_review"]["noul"], 0.2)

    def test_client_cannot_override_questions(self) -> None:
        model = FakeModel()
        with TestClient(create_app(model, KEY)) as client:
            response = client.post(
                "/v1/decisions",
                headers=AUTH,
                json={"task": "risk", "state": {}, "questions": {}},
            )
        self.assertEqual(response.status_code, 422)
        self.assertFalse(model.calls)

    def test_unknown_task_is_rejected(self) -> None:
        with TestClient(create_app(FakeModel(), KEY)) as client:
            response = client.post(
                "/v1/decisions",
                headers=AUTH,
                json={"task": "publish_image", "state": {}},
            )
        self.assertEqual(response.status_code, 422)

    def test_invalid_model_probability_is_rejected(self) -> None:
        with TestClient(create_app(FakeModel(malformed=True), KEY)) as client:
            response = client.post(
                "/v1/decisions", headers=AUTH, json={"task": "risk", "state": {}}
            )
        self.assertEqual(response.status_code, 422)

    def test_oversized_input_is_rejected_before_model_execution(self) -> None:
        model = FakeModel()
        with TestClient(create_app(model, KEY)) as client:
            response = client.post(
                "/v1/decisions",
                headers=AUTH,
                json={"task": "risk", "state": {"text": "a" * 33000}},
            )
        self.assertEqual(response.status_code, 413)
        self.assertFalse(model.calls)

    def test_busy_worker_does_not_build_an_unbounded_queue(self) -> None:
        started, release = threading.Event(), threading.Event()

        class BlockingModel(FakeModel):
            def predict(self, state: dict, questions: dict, strict: bool) -> dict:
                started.set()
                release.wait(timeout=5)
                return super().predict(state, questions, strict)

        with (
            TestClient(create_app(BlockingModel(), KEY)) as client,
            ThreadPoolExecutor(max_workers=1) as pool,
        ):
            first = pool.submit(
                client.post,
                "/v1/decisions",
                headers=AUTH,
                json={"task": "risk", "state": {}},
            )
            self.assertTrue(started.wait(timeout=3))
            try:
                second = client.post(
                    "/v1/decisions", headers=AUTH, json={"task": "risk", "state": {}}
                )
                self.assertEqual(second.status_code, 429)
            finally:
                release.set()
            self.assertEqual(first.result(timeout=3).status_code, 200)


if __name__ == "__main__":
    unittest.main()
