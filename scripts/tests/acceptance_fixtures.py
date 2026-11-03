"""Synthetic subprocess boundary for runner tests; checker and JSON validator stay real."""

import json
from pathlib import Path
from types import SimpleNamespace


def fake_maven(command, backend, phase, omit=None):
    from scripts.ai_evidence import PROXY_CASES, WORKER_REVISION, WORKER_TASKS
    from scripts.ci.run_java_worker_acceptance import REPO_ROOT

    policy = json.loads(
        (REPO_ROOT / ".ci" / (phase + "-acceptance-policy.json")).read_text()
    )
    reports = backend / "target/surefire-reports"
    reports.mkdir(parents=True, exist_ok=True)
    cases = "".join(
        f'<testcase classname="{identity.split("#")[0]}" name="{identity.split("#")[1]}"/>'
        for identity, count in policy["required_tests"].items()
        for _ in range(count)
        if identity != omit
    )
    (reports / "TEST-acceptance.xml").write_text("<testsuite>" + cases + "</testsuite>")
    directory = Path(
        next(
            arg.split("=", 1)[1]
            for arg in command
            if arg.startswith("-Dai.acceptance.dir=")
        )
    )
    for name in WORKER_TASKS if phase == "worker" else PROXY_CASES:
        data = {
            "case_name": name,
            "status": "PASSED",
            "latency_ms": 1.0,
            "requested_model": "chinhnc/Quyet-1.0-Small"
            if phase == "worker"
            else "gemini-3.5-flash-lite",
        }
        if phase == "worker":
            data.update(model_revision=WORKER_REVISION, suggestion_only=True)
        elif name == "compaction":
            data.update(
                http_status=200,
                finish_reason="stop",
                prompt_tokens=100,
                constraint_preservation=True,
            )
        else:
            data["response_status"] = "ANSWERED"
        (directory / (name + ".json")).write_text(json.dumps(data))
    return SimpleNamespace(returncode=0, stdout="", stderr="")
