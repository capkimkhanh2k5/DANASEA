"""Authenticated local inference worker; one pinned Quyet model per process."""

from __future__ import annotations

import hashlib
import hmac
import json
import math
import os
import threading
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import quyet
import torch
from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from starlette.concurrency import run_in_threadpool

from config.rubrics import RUBRICS, VERSION

MODEL_ID = "chinhnc/Quyet-1.0-Small"
REVISION = "233167bba5df61b5375a522bf8a042d8d2189379"
DEFAULT_MODEL = Path.home() / ".cache/danasea/quyet-small/models" / REVISION
MAX_BODY_BYTES = 32_768


class DecisionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    task: str = Field(min_length=1, max_length=40)
    state: dict[str, Any]


def verify_model(path: Path) -> None:
    manifest = path / "MANIFEST.sha256"
    if (
        hashlib.sha256(manifest.read_bytes()).hexdigest()
        != "9ef88d40e9fe14003195b15d181e3bd7bb2802ba5100998c198338b7a640a457"
    ):
        raise ValueError("The model manifest does not match the pinned Small release")
    for line in manifest.read_text().splitlines():
        if not line.strip():
            continue
        expected, name = line.split(maxsplit=1)
        name = name.strip().lstrip("*")
        candidate = (path / name).resolve()
        if not candidate.is_relative_to(path.resolve()):
            raise ValueError("Invalid model manifest path")
        with candidate.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        if actual != expected:
            raise ValueError(f"Model checksum mismatch: {name}")


def validate_response(response: dict, questions: dict) -> None:
    answers = response.get("answers", {})
    if set(answers) != set(questions):
        raise ValueError("Incomplete decision response")
    for name, answer in answers.items():
        if answer.get("type") != questions[name]["type"] or answer.get("truncated"):
            raise ValueError("Invalid decision type or truncated state")
        if answer["type"] == "noul":
            value = answer["noul"]
            if (
                not isinstance(value, (int, float))
                or not math.isfinite(value)
                or not 0 <= value <= 1
            ):
                raise ValueError("Invalid Noul probability")
            continue
        probabilities = answer["probabilities"]
        if any(
            not math.isfinite(p) or not 0 <= p <= 1 for p in probabilities.values()
        ) or not math.isclose(sum(probabilities.values()), 1, abs_tol=0.001):
            raise ValueError("Invalid decision distribution")
        if answer["type"] == "choice" and (
            set(probabilities) != set(questions[name]["criteria"])
            or answer["choice"] not in probabilities
        ):
            raise ValueError("Invalid choice labels")
        if (
            answer["type"] == "score"
            and not 0 <= answer["score"] <= len(questions[name]["criteria"]) - 1
        ):
            raise ValueError("Invalid rubric score")


def create_app(model: Any = None, api_key: str | None = None) -> FastAPI:
    lock = threading.Lock()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        secret = api_key or os.getenv("AI_QUYET_API_KEY", "")
        if not secret or len(secret) < 24:
            raise ValueError("AI_QUYET_API_KEY must contain at least 24 characters")
        app.state.api_key = secret
        if model is None:
            os.environ["HF_HUB_OFFLINE"] = "1"
            os.environ["TRANSFORMERS_OFFLINE"] = "1"
            os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
            torch.set_num_threads(int(os.getenv("QUYET_CPU_THREADS", "4")))
            torch.set_num_interop_threads(1)
            torch.manual_seed(0)
            path = Path(os.getenv("QUYET_MODEL_DIR", str(DEFAULT_MODEL)))
            verify_model(path)
            app.state.model = await run_in_threadpool(
                quyet.load, str(path), device="cpu"
            )
        else:
            app.state.model = model
        yield
        app.state.model = None

    app = FastAPI(
        title="DANASEA Quyet Decisions",
        lifespan=lifespan,
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )

    @app.get("/health")
    def health() -> dict:
        return {
            "status": "UP",
            "model": MODEL_ID,
            "revision": REVISION,
            "rubricVersion": VERSION,
        }

    def infer(body: DecisionRequest) -> dict:
        if not lock.acquire(blocking=False):
            raise HTTPException(status_code=429, detail="The inference worker is busy")
        try:
            started = time.perf_counter()
            questions = RUBRICS[body.task]
            response = app.state.model.predict(body.state, questions, strict=True)
            validate_response(response, questions)
            return {
                "model": MODEL_ID,
                "revision": REVISION,
                "rubricVersion": VERSION,
                "task": body.task,
                "latencyMs": round((time.perf_counter() - started) * 1000, 2),
                "answers": response["answers"],
                "warnings": response.get("warnings", []),
                "calibratedOnDanasea": False,
            }
        except (ValueError, KeyError, TypeError) as exception:
            raise HTTPException(
                status_code=422,
                detail="The input or model output does not satisfy the decision contract",
            ) from exception
        finally:
            lock.release()

    @app.post("/v1/decisions")
    async def decide(
        request: Request, authorization: str | None = Header(default=None)
    ) -> dict:
        expected = "Bearer " + app.state.api_key
        if authorization is None or not hmac.compare_digest(authorization, expected):
            raise HTTPException(status_code=401, detail="Invalid service credentials")
        if (
            request.headers.get("content-type", "").split(";", 1)[0]
            != "application/json"
        ):
            raise HTTPException(
                status_code=415, detail="Content-Type must be application/json"
            )
        data = bytearray()
        async for chunk in request.stream():
            data.extend(chunk)
            if len(data) > MAX_BODY_BYTES:
                raise HTTPException(
                    status_code=413, detail="Decision input is too large"
                )
        try:
            body = DecisionRequest.model_validate(json.loads(data))
        except (ValidationError, ValueError, RecursionError) as exception:
            raise HTTPException(
                status_code=422, detail="Invalid decision request"
            ) from exception
        if body.task not in RUBRICS:
            raise HTTPException(status_code=422, detail="Unknown decision task")
        return await run_in_threadpool(infer, body)

    return app


app = create_app()
