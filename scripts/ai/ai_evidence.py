"""Build acceptance artifacts from fresh Java observations, never inferred routes."""

import json
import re
import subprocess
from pathlib import Path

WORKER_REVISION = "233167bba5df61b5375a522bf8a042d8d2189379"
WORKER_TASKS = (
    "intent",
    "service_category",
    "review",
    "moderation",
    "relevance",
    "complaint",
    "risk",
)
PROXY_CASES = (
    "reasoning-gemini-3.5-flash-lite",
    "reasoning-gpt-oss-120b-medium",
    "compaction",
)


def revision(root):
    value = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=root, text=True
    ).strip()
    if not re.fullmatch("[0-9a-f]{40}", value):
        raise ValueError("Cannot establish backend revision")
    return value


def collect(directory, expected):
    paths = list(Path(directory).glob("*.json"))
    if {path.stem for path in paths} != set(expected):
        raise ValueError("Missing or unexpected measured acceptance cases")
    cases = [
        json.loads((Path(directory) / (name + ".json")).read_text())
        for name in expected
    ]
    for name, case in zip(expected, cases, strict=True):
        if case.get("case_name") != name or case.get("status") != "PASSED":
            raise ValueError("Acceptance observation did not pass: " + name)
    return cases


def evidence(phase, backend_revision, cases, elapsed):
    worker = phase == "java-worker-acceptance"
    return {
        "schema_version": 1,
        "phase": phase,
        "backend_revision": backend_revision,
        "model_revision": WORKER_REVISION if worker else None,
        "requested_model": sorted({case["requested_model"] for case in cases}),
        "actual_provider": "quyet-small-local" if worker else None,
        "route": "direct" if worker else "unknown",
        "technical_pass": True,
        "business_evaluation": {
            "suggestion_only": all(
                case.get("suggestion_only") is True for case in cases
            )
            if worker
            else None,
            "constraint_preservation": next(
                (
                    case.get("constraint_preservation")
                    for case in cases
                    if case["case_name"] == "compaction"
                ),
                None,
            ),
            "policy_boundary_respected": None,
        },
        "executed_cases": len(cases),
        "case_outcomes": cases,
        "timing_seconds": elapsed,
        "token_provenance": {
            "prompt_tokens": None,
            "completion_tokens": None,
            "total_tokens": None,
            "cost_usd": None,
        },
        "status": "PASSED",
    }
