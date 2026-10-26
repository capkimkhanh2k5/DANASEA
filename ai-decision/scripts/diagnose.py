"""Run offline Quyet Small diagnostics or one local Vietnamese intent query."""

from __future__ import annotations

import argparse
import importlib.metadata
import json
import math
import os
import platform
import resource
import statistics
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import quyet
import torch

DIRECTORY = Path(__file__).resolve().parents[1]
REVISION = "233167bba5df61b5375a522bf8a042d8d2189379"
DEFAULT_MODEL_DIR = Path.home() / ".cache/danasea/quyet-small/models" / REVISION


def synchronize(device: str) -> None:
    if device == "mps":
        torch.mps.synchronize()


def validate_answers(response: dict[str, Any], questions: dict[str, Any]) -> None:
    if set(response["answers"]) != set(questions):
        raise ValueError("The response does not contain every requested question")
    for key, answer in response["answers"].items():
        if answer["type"] != questions[key]["type"] or answer.get("truncated"):
            raise ValueError(f"Unexpected answer type or truncated state: {key}")
        if answer["type"] == "noul":
            if not math.isfinite(answer["noul"]) or not 0 <= answer["noul"] <= 1:
                raise ValueError(f"Invalid Noul probability: {key}")
            continue
        probabilities = answer["probabilities"]
        if any(not math.isfinite(p) or not 0 <= p <= 1 for p in probabilities.values()):
            raise ValueError(f"Invalid probability distribution: {key}")
        if not math.isclose(sum(probabilities.values()), 1, abs_tol=0.001):
            raise ValueError(f"Probabilities do not sum to one: {key}")
        if answer["type"] == "choice":
            if set(probabilities) != set(questions[key]["criteria"]):
                raise ValueError(f"Unexpected choice labels: {key}")
            if answer["choice"] not in probabilities:
                raise ValueError(f"Unknown selected label: {key}")


def selected_value(answer: dict[str, Any]) -> str | bool:
    if answer["type"] == "choice":
        return str(answer["choice"])
    if answer["type"] == "noul":
        return bool(answer["noul"] >= 0.5)
    probabilities = answer["probabilities"]
    return str(max(probabilities, key=probabilities.get))


def quantile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", choices=["cpu", "mps"], default="cpu")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--model-dir", type=Path, default=DEFAULT_MODEL_DIR)
    parser.add_argument(
        "--cases",
        type=Path,
        default=DIRECTORY / "tests/fixtures/quyet-small/cases.json",
    )
    parser.add_argument("--output", type=Path)
    parser.add_argument("--limit", type=int)
    inputs = parser.add_mutually_exclusive_group()
    inputs.add_argument("--message", help="Run one message against the intent rubric")
    inputs.add_argument(
        "--request", type=Path, help="JSON file containing state and questions"
    )
    args = parser.parse_args()
    if args.threads < 1 or (args.limit is not None and args.limit < 1):
        parser.error("threads and limit must be positive")
    if not args.model_dir.is_dir():
        parser.error("Local model not found. Run prepare_model.py first.")
    if args.device == "mps" and not torch.backends.mps.is_available():
        parser.error("Apple MPS is unavailable on this machine")

    fixture = json.loads(args.cases.read_text())
    torch.set_num_threads(args.threads)
    torch.set_num_interop_threads(1)
    torch.manual_seed(0)
    started = time.perf_counter()
    model = quyet.load(str(args.model_dir), device=args.device)
    synchronize(args.device)
    load_seconds = time.perf_counter() - started
    metadata = {
        "model_id": "chinhnc/Quyet-1.0-Small",
        "revision": REVISION,
        "model_dir": str(args.model_dir),
        "device": args.device,
        "dtype": str(next(model.model.parameters()).dtype),
        "parameters": sum(p.numel() for p in model.model.parameters()),
        "cpu_threads": args.threads,
        "machine": platform.machine(),
        "python": platform.python_version(),
        "versions": {
            package: importlib.metadata.version(package)
            for package in ["quyet", "torch", "transformers", "huggingface-hub"]
        },
        "offline_mode": os.getenv("HF_HUB_OFFLINE") == "1"
        and os.getenv("TRANSFORMERS_OFFLINE") == "1",
        "load_seconds": round(load_seconds, 3),
        "recorded_at": datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).isoformat(),
    }
    print(json.dumps({"loaded": metadata}, ensure_ascii=False), flush=True)

    if args.message or args.request:
        request = (
            json.loads(args.request.read_text())
            if args.request
            else {
                "state": {"message": args.message},
                "questions": fixture["question_sets"]["intent"],
            }
        )
        started = time.perf_counter()
        response = model.predict(request["state"], request["questions"], strict=True)
        synchronize(args.device)
        validate_answers(response, request["questions"])
        result = {
            "metadata": metadata,
            "latency_ms": round((time.perf_counter() - started) * 1000, 2),
            "request": request,
            "response": response,
        }
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(
                json.dumps(result, ensure_ascii=False, indent=2) + "\n"
            )
        print(
            json.dumps(
                result,
                ensure_ascii=False,
                indent=2,
            ),
            flush=True,
        )
        return

    cases = fixture["cases"][: args.limit]
    warmup = cases[0]
    for _ in range(2):
        model.predict(
            warmup["state"], fixture["question_sets"][warmup["suite"]], strict=True
        )
        synchronize(args.device)

    rows = []
    groups: dict[str, dict[str, int]] = {}
    for case in cases:
        questions = fixture["question_sets"][case["suite"]]
        synchronize(args.device)
        started = time.perf_counter()
        response = model.predict(case["state"], questions, strict=True)
        synchronize(args.device)
        latency_ms = (time.perf_counter() - started) * 1000
        validate_answers(response, questions)
        judgments = {
            name: {
                "expected": expected,
                "actual": selected_value(response["answers"][name]),
                "match": selected_value(response["answers"][name]) == expected,
            }
            for name, expected in case["expected"].items()
        }
        counts = groups.setdefault(
            case["suite"], {"questions": 0, "matched": 0, "requests": 0}
        )
        counts["requests"] += 1
        counts["questions"] += len(judgments)
        counts["matched"] += sum(item["match"] for item in judgments.values())
        row = {
            **case,
            "latency_ms": round(latency_ms, 2),
            "response": response,
            "judgments": judgments,
        }
        rows.append(row)
        print(
            json.dumps(
                {
                    "id": case["id"],
                    "latency_ms": row["latency_ms"],
                    "judgments": judgments,
                },
                ensure_ascii=False,
            ),
            flush=True,
        )

    latencies = [row["latency_ms"] for row in rows]
    summary = {
        "scope": fixture["description"],
        "requests": len(rows),
        "groups": groups,
        "schema_valid_requests": len(rows),
        "noul_threshold": 0.5,
        "score_evaluation": "Compare the highest-probability rubric level, not the expected score",
        "latency_ms": {
            "p50": round(statistics.median(latencies), 2),
            "p95_nearest_rank": round(quantile(latencies, 0.95), 2),
            "mean": round(statistics.mean(latencies), 2),
        },
        "peak_process_rss_mib": round(
            resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / (1024**2), 1
        ),
    }
    output = (
        args.output or DIRECTORY / "reports/quyet-small" / f"results-{args.device}.json"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {"metadata": metadata, "summary": summary, "cases": rows},
            ensure_ascii=False,
            indent=2,
        )
        + "\n"
    )
    print(
        json.dumps(
            {"summary": summary, "output": str(output)}, ensure_ascii=False, indent=2
        ),
        flush=True,
    )


if __name__ == "__main__":
    main()
