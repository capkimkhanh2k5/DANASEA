#!/usr/bin/env python3
"""DANASEA Management CLI - Entrypoint for running system scripts."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent

COMMAND_MAP = {
    # AI Group
    "ai:chat": SCRIPTS_DIR / "ai" / "test_interactive_ai.py",
    "ai:acceptance": SCRIPTS_DIR / "ai" / "check_ai_acceptance.py",
    "ai:evidence": SCRIPTS_DIR / "ai" / "ai_evidence.py",

    # QA Group
    "qa:acceptance": SCRIPTS_DIR / "qa" / "acceptance.py",
    "qa:payments": SCRIPTS_DIR / "qa" / "acceptance_payments.py",
    "qa:coverage": SCRIPTS_DIR / "qa" / "check_coverage.py",
    "qa:financial": SCRIPTS_DIR / "qa" / "verify_financial_mutations.py",

    # Ops Group
    "ops:availability": SCRIPTS_DIR / "ops" / "availability.py",
    "ops:backup": SCRIPTS_DIR / "ops" / "backup.py",
    "ops:deploy": SCRIPTS_DIR / "ops" / "deploy.sh",
    "ops:production": SCRIPTS_DIR / "ops" / "production.py",
    "ops:rehearse": SCRIPTS_DIR / "ops" / "rehearse.py",
    "ops:release": SCRIPTS_DIR / "ops" / "release.py",

    # Demo Group
    "demo:data": SCRIPTS_DIR / "demo" / "demo_data.py",
    "demo:report": SCRIPTS_DIR / "demo" / "demo_report.py",
    "demo:request": SCRIPTS_DIR / "demo" / "demo_request.py",

    # Governance Group
    "gov:dependabot": SCRIPTS_DIR / "governance" / "check_dependabot.py",
    "gov:repo": SCRIPTS_DIR / "governance" / "check_repository_controls.py",
    "gov:reports": SCRIPTS_DIR / "governance" / "check_test_reports.py",
}

def print_help():
    print("DANASEA Script Manager")
    print("Usage: python3 scripts/manage.py <group:command> [arguments...]")
    print("\nAvailable commands:")
    for cmd, path in sorted(COMMAND_MAP.items()):
        rel_path = path.relative_to(SCRIPTS_DIR.parent)
        print(f"  {cmd:<18} -> {rel_path}")

def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help", "help"):
        print_help()
        sys.exit(0)

    cmd = sys.argv[1]
    if cmd not in COMMAND_MAP:
        # Try shorthand without colon, e.g. "ai chat" -> "ai:chat"
        if len(sys.argv) >= 3 and f"{cmd}:{sys.argv[2]}" in COMMAND_MAP:
            cmd = f"{cmd}:{sys.argv[2]}"
            extra_args = sys.argv[3:]
        else:
            print(f"Error: Unknown command '{cmd}'. Run 'python3 scripts/manage.py help' for a list of commands.")
            sys.exit(1)
    else:
        extra_args = sys.argv[2:]

    target_path = COMMAND_MAP[cmd]
    if not target_path.exists():
        print(f"Error: Script not found at {target_path}")
        sys.exit(1)

    if target_path.suffix == ".py":
        runner = [sys.executable, str(target_path)]
    else:
        runner = ["bash", str(target_path)]

    cmd_line = runner + extra_args
    sys.exit(subprocess.call(cmd_line))

if __name__ == "__main__":
    main()
