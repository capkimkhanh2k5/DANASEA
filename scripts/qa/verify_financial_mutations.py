#!/usr/bin/env python3
"""Run targeted mutation proofs against financial guards in Danasea backend.

Verifies that specific deliberate mutations in payment and refund safety guards
are strictly detected and killed by our test suites, without modifying the workspace.
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

MUTATIONS = [
    {
        "mutation": "constant-hmac-payload",
        "production_source": "src/main/java/com/danasea/backend/modules/order/application/PaymentWebhookSigner.java",
        "test_class": "com.danasea.backend.modules.order.application.PaymentWebhookSignerTest",
        "target": "mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))",
        "replacement": 'mac.doFinal("constant".getBytes(StandardCharsets.UTF_8))',
    },
    {
        "mutation": "capture-amount-guard-removed",
        "production_source": "src/main/java/com/danasea/backend/modules/order/application/OrderPaymentService.java",
        "test_class": "com.danasea.backend.modules.order.application.PaymentGatewaySafetyTest",
        "target": "|| expectedAmount.compareTo(result.amount()) != 0",
        "replacement": "|| false",
    },
    {
        "mutation": "capture-currency-guard-removed",
        "production_source": "src/main/java/com/danasea/backend/modules/order/application/OrderPaymentService.java",
        "test_class": "com.danasea.backend.modules.order.application.PaymentGatewaySafetyTest",
        "target": "|| result.currency() == null || !expectedCurrency.equalsIgnoreCase(result.currency())",
        "replacement": "|| false",
    },
    {
        "mutation": "refund-currency-guard-removed",
        "production_source": "src/main/java/com/danasea/backend/modules/order/application/RefundProcessingService.java",
        "test_class": "com.danasea.backend.modules.order.application.RefundProcessingSafetyBranchesTest",
        "target": "|| !refund.getProviderCurrency().equals(result.currency())",
        "replacement": "|| false",
    },
]


def resolve_classpath(backend_dir: Path) -> str:
    target_dir = backend_dir / "target"
    cp_file = target_dir / "test-cp.txt"
    if not cp_file.is_file():
        mvnw = backend_dir / ("mvnw.cmd" if sys.platform == "win32" else "mvnw")
        cmd = [str(mvnw), "-B", "-q", "dependency:build-classpath", f"-Dmdep.outputFile={cp_file}"]
        subprocess.run(cmd, cwd=str(backend_dir), check=True)

    dependency_cp = cp_file.read_text(encoding="utf-8").strip()
    test_classes = str(target_dir / "test-classes")
    classes = str(target_dir / "classes")
    return os.pathsep.join([test_classes, classes, dependency_cp])


def run_launcher(classpath: str, test_class: str, backend_dir: Path) -> dict:
    launcher_class = "com.danasea.backend.tools.FinancialMutationLauncher"
    cmd = ["java", "-cp", classpath, launcher_class, test_class]
    result = subprocess.run(cmd, cwd=str(backend_dir), capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError("JUnit launcher process failed: " + result.stderr[-1000:])
    stdout = result.stdout.strip()
    # Find JSON line in stdout
    for line in stdout.splitlines():
        line = line.strip()
        if line.startswith("{") and line.endswith("}"):
            try:
                return json.loads(line, strict=False)
            except json.JSONDecodeError:
                continue

    # Fallback if launcher JSON was split by platform newline translation
    start = stdout.find("{")
    end = stdout.rfind("}")
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(stdout[start : end + 1], strict=False)
        except json.JSONDecodeError:
            pass

    raise RuntimeError(
        f"Launcher failed to output valid JSON for {test_class}. Exit: {result.returncode}. Stderr: {result.stderr.strip()}"
    )


def compile_mutant(mutant_source: Path, output_classes_dir: Path, classpath: str, backend_dir: Path):
    cmd = [
        "javac",
        "-d",
        str(output_classes_dir),
        "-cp",
        classpath,
        str(mutant_source),
    ]
    res = subprocess.run(cmd, cwd=str(backend_dir), capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Failed to compile mutant {mutant_source.name}: {res.stderr.strip()}")


def execute_mutation_proof(mutation_spec: dict, backend_dir: Path, base_classpath: str) -> dict:
    source_rel = mutation_spec["production_source"]
    source_path = backend_dir / source_rel
    if not source_path.is_file():
        raise FileNotFoundError(f"Source file not found: {source_path}")

    source_content = source_path.read_text(encoding="utf-8")
    source_sha256 = hashlib.sha256(source_content.encode("utf-8")).hexdigest()

    test_class = mutation_spec["test_class"]
    target = mutation_spec["target"]
    replacement = mutation_spec["replacement"]

    # 1. Run baseline test on original code
    baseline_result = run_launcher(base_classpath, test_class, backend_dir)
    baseline_clean = (
        baseline_result.get("found", 0) > 0
        and baseline_result.get("passed") == baseline_result.get("found")
        and baseline_result.get("containers_failed", 0) == 0
        and baseline_result.get("failed", 0) == 0
        and baseline_result.get("skipped", 0) == 0
        and baseline_result.get("aborted", 0) == 0
    )
    if not baseline_clean:
        return {
            "mutation": mutation_spec["mutation"],
            "production_source": f"backend/{source_rel}",
            "source_sha256": source_sha256,
            "test_class": test_class,
            "baseline": baseline_result,
            "mutant": None,
            "killed": False,
            "error": "Baseline test did not pass cleanly",
        }

    # 2. Check target count in source
    count = source_content.count(target)
    if count != 1:
        return {
            "mutation": mutation_spec["mutation"],
            "production_source": f"backend/{source_rel}",
            "source_sha256": source_sha256,
            "test_class": test_class,
            "baseline": baseline_result,
            "mutant": None,
            "killed": False,
            "error": f"Target replacement pattern found {count} times (must be exactly 1)",
        }

    # 3. Create mutated file in temporary directory
    with tempfile.TemporaryDirectory(prefix="danasea-mutant-") as temp_dir:
        temp_path = Path(temp_dir)
        mutant_classes = temp_path / "classes"
        mutant_classes.mkdir(parents=True, exist_ok=True)

        mutated_content = source_content.replace(target, replacement, 1)
        temp_source = temp_path / source_path.name
        temp_source.write_text(mutated_content, encoding="utf-8")

        # 4. Compile mutant
        try:
            compile_mutant(temp_source, mutant_classes, base_classpath, backend_dir)
        except Exception as e:
            return {
                "mutation": mutation_spec["mutation"],
                "production_source": f"backend/{source_rel}",
                "source_sha256": source_sha256,
                "test_class": test_class,
                "baseline": baseline_result,
                "mutant": None,
                "killed": False,
                "error": f"Mutant compilation failed: {e}",
            }

        # 5. Run test with mutated class prepended to classpath
        mutant_cp = f"{mutant_classes}{os.pathsep}{base_classpath}"
        mutant_result = run_launcher(mutant_cp, test_class, backend_dir)

        # 6. Assess whether mutant was killed by assertion failure
        same_test_count = mutant_result.get("found") == baseline_result.get("found")
        has_failures = mutant_result.get("failed", 0) > 0
        no_aborted_or_skipped = (mutant_result.get("skipped", 0) == 0 and mutant_result.get("aborted", 0) == 0
                                 and mutant_result.get("containers_failed", 0) == 0
                                 and mutant_result.get("passed", 0) + mutant_result.get("failed", 0) == mutant_result.get("found"))

        # Check failure type to ensure it is an assertion failure
        failures = mutant_result.get("failures", [])
        assertion_failures = [f for f in failures if f.get("assertion") is True]
        has_assertion_failure = len(assertion_failures) == mutant_result.get("failed", 0) and bool(assertion_failures)

        killed = same_test_count and has_failures and no_aborted_or_skipped and has_assertion_failure

        clean_baseline = {
            "found": baseline_result.get("found"),
            "passed": baseline_result.get("passed"),
            "failed": baseline_result.get("failed"),
            "skipped": baseline_result.get("skipped"),
            "aborted": baseline_result.get("aborted"),
        }
        clean_mutant = {
            "found": mutant_result.get("found"),
            "passed": mutant_result.get("passed"),
            "failed": mutant_result.get("failed"),
            "skipped": mutant_result.get("skipped"),
            "aborted": mutant_result.get("aborted"),
        }

        return {
            "mutation": mutation_spec["mutation"],
            "production_source": f"backend/{source_rel}",
            "source_sha256": source_sha256,
            "test_class": test_class,
            "baseline": clean_baseline,
            "mutant": clean_mutant,
            "killed": killed,
            "failures": failures,
        }


def main():
    parser = argparse.ArgumentParser(description="Automate targeted financial mutation proofs.")
    parser.add_argument(
        "--backend-dir",
        type=Path,
        default=Path("backend"),
        help="Path to backend directory containing pom.xml",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("backend/target/financial-mutation-proof.json"),
        help="Path to output JSON verification evidence",
    )
    args = parser.parse_args()

    backend_dir = args.backend_dir.resolve()
    if not (backend_dir / "pom.xml").is_file():
        sys.stderr.write(f"Error: {backend_dir} is not a valid Maven project directory.\n")
        return 1

    print(f"Resolving classpath for backend at {backend_dir}...")
    base_cp = resolve_classpath(backend_dir)

    all_results = []
    all_killed = True

    for spec in MUTATIONS:
        name = spec["mutation"]
        print(f"Executing mutation proof: {name} ...", end=" ", flush=True)
        outcome = execute_mutation_proof(spec, backend_dir, base_cp)
        all_results.append(outcome)
        if outcome.get("killed"):
            print("KILLED (Assertion caught mutant)")
        else:
            print(f"FAILED / ALIVE (Error: {outcome.get('error', 'mutant not killed')})")
            all_killed = False

    evidence = {
        "method": "Four deliberate source mutations compiled and executed entirely under /tmp; "
        "workspace production files were unchanged. All selected tests passed first on unmodified classes. "
        "This is targeted proof, not a full mutation score.",
        "results": all_results,
    }

    output_path = args.output.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(f"Evidence successfully written to {output_path}")

    return 0 if all_killed else 1


if __name__ == "__main__":
    sys.exit(main())
