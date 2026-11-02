#!/usr/bin/env python3
"""Validate AI acceptance evidence against schema and semantic integrity rules.

Ensures strict separation between technical transport success, routing provenance,
and business evaluation (suggestion-only, constraint preservation, policy boundary).
Rejects falsified direct routes, missing revisions, fake 0 costs, and unmeasured metrics.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

COMMIT_SHA_PATTERN = re.compile(r"^[0-9a-f]{7,40}$", re.IGNORECASE)
ALLOWED_ROUTES = ("direct", "fallback", "unknown")
ALLOWED_PHASES = ("java-worker-acceptance", "proxy-live-acceptance")


class AiAcceptanceValidationError(Exception):
    """Validation failure in AI acceptance evidence."""


def validate_ai_evidence(data: dict) -> None:
    if not isinstance(data, dict):
        raise AiAcceptanceValidationError("Evidence root must be a JSON object")

    if data.get("schema_version") != 1:
        raise AiAcceptanceValidationError(f"Expected schema_version 1, got {data.get('schema_version')}")

    phase = data.get("phase")
    if phase not in ALLOWED_PHASES:
        raise AiAcceptanceValidationError(f"Invalid phase '{phase}', expected one of: {ALLOWED_PHASES}")

    backend_rev = data.get("backend_revision")
    if not backend_rev or not isinstance(backend_rev, str) or not COMMIT_SHA_PATTERN.match(backend_rev):
        raise AiAcceptanceValidationError(f"Missing or invalid backend_revision SHA: '{backend_rev}'")

    route = data.get("route")
    if route not in ALLOWED_ROUTES:
        raise AiAcceptanceValidationError(f"Invalid route '{route}', expected one of: {ALLOWED_ROUTES}")

    # Check for falsified direct label when fallback occurred
    if data.get("fallback_occurred") is True and route == "direct":
        raise AiAcceptanceValidationError("Evidence indicates fallback occurred, but route is labeled 'direct'")

    technical_pass = data.get("technical_pass")
    if not isinstance(technical_pass, bool):
        raise AiAcceptanceValidationError("Field 'technical_pass' must be a boolean")

    business_eval = data.get("business_evaluation")
    if not isinstance(business_eval, dict):
        raise AiAcceptanceValidationError("Field 'business_evaluation' must be an object")

    for req_flag in ("suggestion_only", "constraint_preservation", "policy_boundary_respected"):
        if req_flag not in business_eval or not isinstance(business_eval[req_flag], bool):
            raise AiAcceptanceValidationError(f"Missing or non-boolean business evaluation flag: '{req_flag}'")

    # Suggestion-only is a mandatory safety rule for AI decisions
    if not business_eval.get("suggestion_only"):
        raise AiAcceptanceValidationError("AI decisions must be suggestion-only; cannot be authoritative")

    executed_cases = data.get("executed_cases")
    if not isinstance(executed_cases, int) or executed_cases <= 0:
        raise AiAcceptanceValidationError(f"Invalid executed_cases count: {executed_cases}")

    case_outcomes = data.get("case_outcomes")
    if not isinstance(case_outcomes, list) or len(case_outcomes) != executed_cases:
        raise AiAcceptanceValidationError(
            f"case_outcomes count ({len(case_outcomes) if isinstance(case_outcomes, list) else 0}) "
            f"does not match executed_cases ({executed_cases})"
        )

    for case in case_outcomes:
        if not isinstance(case, dict) or not case.get("case_name") or case.get("status") not in ("PASSED", "FAILED"):
            raise AiAcceptanceValidationError(f"Invalid case outcome record: {case}")
        if technical_pass is True and case.get("status") != "PASSED":
            raise AiAcceptanceValidationError(
                f"technical_pass is True, but case '{case.get('case_name')}' failed"
            )

    token_prov = data.get("token_provenance")
    if token_prov is not None:
        if not isinstance(token_prov, dict):
            raise AiAcceptanceValidationError("token_provenance must be an object or null")
        # Check rule: cost_usd cannot be artificially 0 if not tracked
        cost = token_prov.get("cost_usd")
        if cost == 0 and token_prov.get("total_tokens") is None:
            raise AiAcceptanceValidationError(
                "Cost cannot be 0 when token usage is not measured; must be null"
            )

    overall_status = data.get("status")
    if overall_status not in ("PASSED", "FAILED"):
        raise AiAcceptanceValidationError(f"Invalid overall status: '{overall_status}'")

    if technical_pass is True and overall_status != "PASSED":
        raise AiAcceptanceValidationError("Status must be 'PASSED' when technical_pass is True and all cases passed")


def check_file(path: Path) -> dict:
    if not path.is_file():
        raise FileNotFoundError(f"Evidence file not found: {path}")
    content = path.read_text(encoding="utf-8")
    data = json.loads(content)
    validate_ai_evidence(data)
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description="Check AI acceptance evidence against schema and safety rules.")
    parser.add_argument("evidence", type=Path, help="Path to evidence JSON file")
    args = parser.parse_args()

    try:
        data = check_file(args.evidence.resolve())
        print(f"[OK] AI Acceptance Evidence valid: {data.get('phase')} ({data.get('status')})")
        return 0
    except Exception as e:
        sys.stderr.write(f"[ERROR] Invalid AI acceptance evidence: {e}\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
