#!/usr/bin/env python3
"""Validate measured acceptance outcomes and distinguish unknown routing/business facts."""

import argparse
import json
import math
import re
import sys
from pathlib import Path

WORKER_REVISION = "233167bba5df61b5375a522bf8a042d8d2189379"
WORKER_CASES = {
    "intent",
    "service_category",
    "review",
    "moderation",
    "relevance",
    "complaint",
    "risk",
}
PROXY_CASES = {
    "reasoning-gemini-3.5-flash-lite",
    "reasoning-gpt-oss-120b-medium",
    "compaction",
}


class AiAcceptanceValidationError(ValueError):
    """Acceptance evidence cannot establish its claim."""


def require(condition, message):
    if not condition:
        raise AiAcceptanceValidationError(message)


def validate_ai_evidence(data):
    require(
        isinstance(data, dict) and data.get("schema_version") == 1,
        "Expected schema_version 1 object",
    )
    phase = data.get("phase")
    require(
        phase in ("java-worker-acceptance", "proxy-live-acceptance"), "Invalid phase"
    )
    require(
        isinstance(data.get("backend_revision"), str)
        and re.fullmatch("[0-9a-f]{40}", data["backend_revision"]),
        "Missing or invalid backend_revision SHA",
    )
    route = data.get("route")
    require(route in ("direct", "fallback", "unknown"), "Invalid route")
    require(
        not (data.get("fallback_occurred") is True and route == "direct"),
        "fallback occurred, but route is labeled direct",
    )
    technical = data.get("technical_pass")
    require(type(technical) is bool, "technical_pass must be boolean")
    require(data.get("status") in ("PASSED", "FAILED"), "Invalid status")
    require(data["status"] != "PASSED" or technical, "PASSED requires technical_pass")
    business = data.get("business_evaluation")
    require(isinstance(business, dict), "business_evaluation must be object")
    for flag in (
        "suggestion_only",
        "constraint_preservation",
        "policy_boundary_respected",
    ):
        require(
            flag in business
            and (business[flag] is None or type(business[flag]) is bool),
            "Invalid business evaluation: " + flag,
        )
        require(
            data["status"] != "PASSED" or business[flag] is not False,
            "Business evaluation failed: " + flag,
        )
    require(
        business["suggestion_only"] is not False, "AI decisions must be suggestion-only"
    )
    cases = data.get("case_outcomes")
    count = data.get("executed_cases")
    require(
        type(count) is int
        and count > 0
        and isinstance(cases, list)
        and len(cases) == count,
        "case_outcomes does not match executed_cases",
    )
    expected = WORKER_CASES if phase == "java-worker-acceptance" else PROXY_CASES
    require(all(isinstance(case, dict) for case in cases), "Invalid case object")
    names = [case.get("case_name") for case in cases]
    require(
        len(set(names)) == len(names) and set(names) == expected,
        "Missing, duplicate or unexpected acceptance case",
    )
    for case in cases:
        require(case.get("status") in ("PASSED", "FAILED"), "Invalid case status")
        require(
            not technical or case["status"] == "PASSED",
            "technical_pass is True, but case failed",
        )
        if case.get("http_status") is not None:
            code = case["http_status"]
            require(
                type(code) is int and (case["status"] != "PASSED" or 200 <= code < 300),
                "Non-success HTTP labeled PASSED",
            )
        latency = case.get("latency_ms")
        require(
            type(latency) in (int, float) and math.isfinite(latency) and latency >= 0,
            "Missing or invalid measured latency",
        )
        require(bool(case.get("requested_model")), "Missing requested model")
        if phase == "java-worker-acceptance":
            require(
                case.get("model_revision") == WORKER_REVISION
                and case.get("suggestion_only") is True,
                "Worker revision or suggestion-only observation missing",
            )
        elif case["case_name"] == "compaction":
            require(
                case.get("constraint_preservation") is True
                and case.get("finish_reason") == "stop"
                and type(case.get("prompt_tokens")) is int
                and case["prompt_tokens"] > 0,
                "Compaction constraint/finish/usage observation missing",
            )
        else:
            require(
                case.get("response_status") == "ANSWERED",
                "Proxy did not report ANSWERED",
            )
    if phase == "java-worker-acceptance":
        require(
            data.get("model_revision") == WORKER_REVISION,
            "Missing or invalid worker model_revision",
        )
        require(
            route == "direct" and data.get("actual_provider") == "quyet-small-local",
            "Invalid owned worker provenance",
        )
    else:
        require(
            route == "unknown" and data.get("actual_provider") is None,
            "Proxy upstream routing is unmeasured; use unknown/null",
        )
    usage = data.get("token_provenance")
    require(isinstance(usage, dict), "Missing token_provenance")
    for key in ("prompt_tokens", "completion_tokens", "total_tokens"):
        require(
            key in usage
            and (usage[key] is None or type(usage[key]) is int and usage[key] >= 0),
            "Invalid token measurement",
        )
    cost = usage.get("cost_usd")
    require(
        cost is None
        or type(cost) in (int, float)
        and math.isfinite(cost)
        and cost >= 0,
        "Invalid cost",
    )
    require(
        not (cost == 0 and usage.get("total_tokens") is None),
        "Cost cannot be 0 when token usage is not measured; must be null",
    )


def check_file(path):
    data = json.loads(Path(path).read_text())
    validate_ai_evidence(data)
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    args = parser.parse_args()
    try:
        data = check_file(args.evidence)
        require(data["status"] == "PASSED", "Acceptance did not pass")
        print("AI acceptance evidence valid: " + data["phase"])
        return 0
    except (ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
