# Demo Data Server Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Track steps below.

**Goal:** Implement manual, backup-verified application-data replacement and recovery on the existing server.

**Architecture:** Extend the current importer with an atomic reset/import mode. A root-installed Python orchestrator authenticates signed requests through the existing SSH forced command, takes the shared deployment lock, rehearses on a restored database, and maintains recovery state. A manual production workflow signs and dispatches that operation over Tailscale.

**Tech Stack:** Python 3.11 standard library, Bash, PostgreSQL 17, Docker Compose, OpenSSH signing, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-29-demo-data-workflow-design.md` (approved by owner).

## Global Constraints

- No live server reset during implementation/testing.
- Source restricted to `main`; requests bound to deployed SHA and manifest SHA256.
- Preserve schema, Flyway, system configuration, category safety rules and their reference closure.
- Backup and disposable restore verification precede replacement; uncertain recovery leaves maintenance active.
- Use existing production SSH/Tailscale/environment controls; no arbitrary shell, SQL, database names or paths from workflow input.
- Share `cd-production` concurrency and `.deploy.lock`; cancel-in-progress false.
- Demo passwords come from protected secrets, never command arguments or public artifacts.
- Existing pending safety documents remain pending; package import is not proof of complete business acceptance.

## Review Focus

- A table added by a future migration must block an unreviewed reset rather than be silently deleted.
- Protected configuration dependencies or conflicting fixture IDs must block rather than overwrite configuration.
- Process interruption after DB commit but before receipt publication must retain a recoverable journal.
- Outstanding queue messages, an unknown Redis layout or a second database writer must block unsafe replacement.
- Source/deployment drift, replay and recovery using a backup from another database/schema must fail before deletion.

### Task 1: Atomic database replacement and package integrity

**Files:** `demo_data_handoff/install_demo_data.py`, `demo_data_handoff/tests/*`, `demo_data_handoff/MANIFEST.sha256`, `scripts/tests/test_demo_database.py`.

**Interfaces:** Add `--replace` usable only with `--dry-run` or `--apply`. Existing database/container/user/password/backup/ownership arguments stay compatible. The full reset, import and verification run in one existing `Session` transaction. CLI emits a JSON replacement report; errors remain secret-safe.

- [x] Write failing tests for reset inventory, preservation, unsupported tables, protected conflicts and manifest integrity; demonstrate failure.
- [x] Implement explicit schema inventory/FK checks and protected row closure. Delete only reviewed application data without unrestricted CASCADE; preserve required categories/configuration.
- [x] Add transactional `--replace`, pre-commit verification and durable pending-receipt recovery state. Keep normal import/cleanup contracts intact.
- [x] Verify real PostgreSQL migrations: old sentinel removal, configuration preserved, 270 canonical records, repeated reset, deliberate import failure rollback. Test unknown tables/FKs fail closed.
- [x] Regenerate the exact manifest after package edits; run all package tests.

### Task 2: Request authentication and host bootstrap

**Files:** `scripts/demo_request.py`, `scripts/deploy.sh`, `scripts/install-demo-data-controls.sh`, `scripts/tests/test_demo_request.py`, `scripts/tests/test_demo_controls.py`.

**Interfaces:**

- Request JSON: `version=1`, `repository`, 40-hex `sha`, 64-hex `package_sha256`, action `inspect|replace|restore`, positive integer `run_id` and `run_attempt`, integer `issued_at`, 32-hex `nonce`, action confirmation, optional restore `backup_id`.
- `validate(body, sha=None, repository=None, now=None) -> dict` validates types, expiry (10 minutes), confirmations (`REPLACE_DEMO_DATA`, `RESTORE_DEMO_DATA`), and backup basename.
- `verify(envelope: str, signers: Path, sha: str, repository: str) -> (body: dict, password: str|None)` uses namespace `danasea-demo-data` and identity `github-actions`; bounded JSON/base64 payload; secret excluded from returned public metadata.
- CLI `create --action --sha --repository --package-sha256 --run-id --run-attempt --confirmation [--backup-id] --output`; `envelope --request --signature [--password-env DEMO_USER_PASSWORD]` produces ephemeral stdin envelope.
- SSH command `demo-data <sha>` dispatches `python3 "$TOOLS_DIR/demo_data.py" --sha "$tag" --policy "$DEPLOY_POLICY"` with stdin intact. Existing status/deploy/rollback unchanged.
- Root bootstrap `install-demo-data-controls.sh <CI-public-key-file>` reads existing deploy policy, installs reviewed demo tools and a separate `/etc/danasea/demo-data-signers`, and updates the existing root-owned wrapper atomically. No silent host trust/key rotation.

- [x] Write failing tests for signature namespace, expiry, wrong SHA/repository, invalid confirmations, payload/path injection and oversized input.
- [x] Implement request module, restricted dispatch and one-time bootstrap.
- [x] Verify real OpenSSH signing tests plus owned-container installer tests and existing deploy/installer regressions.

### Task 3: Server operation lifecycle and recovery

**Files:** `scripts/demo_data.py`, optional focused `scripts/demo_runtime.py`, `scripts/tests/test_demo_data.py`.

**Interfaces:** CLI `demo_data.py --sha SHA --policy /etc/danasea/deploy.json`; consume signed stdin via Task 2. Policy already contains `repository`, `app_dir`, `backup_config`, `public_origin`; separate signer path defaults to `/etc/danasea/demo-data-signers`. Importer CLI is Task 1. Existing `backup.backup(config)` creates a verified local archive, with JSON metadata.

- [x] Write failing lifecycle tests for source drift, duplicate nonce, lock contention, failed backup/rehearsal, failed import, interrupted receipt and post-commit recovery.
- [x] Validate root-owned policy/tools, deployed manifest and accepted image identities. Hold `.deploy.lock`; maintain a private operation journal and consume nonce before execution.
- [x] Inspect without committing data. For replacement: stop writers and public writes, require safe queue state and exclusive DB access, create/verify backup, protect current recovery archive, restore/rehearse on a disposable DB, then invoke atomic replacement.
- [x] Audit Redis keys before invalidating known application cache/session/hold state; refuse an unverified shared layout. Restart exact deployed images; verify health, demo login, catalog and representative APIs.
- [x] Add compatible named-backup restore and automatic recovery after post-commit failure. Preserve state/backup and maintenance on recovery failure; no blind retries after uncertain commit.
- [x] Verify state transitions and secret-safe errors with real fixture commands wherever practical; coordinate disposable DB end-to-end coverage with Task 1.

### Task 4: GitHub workflow, CI and operator delivery

**Files:** `.github/workflows/demo-data.yml`, `.github/workflows/ci.yml`, `docs/demo-data-workflow.md`, `scripts/tests/test_demo_workflow.py`.

**Interfaces:** Use Task 2 CLI and SSH `demo-data <sha>`; read deployed SHA with existing SSH `status`. Workflow operation inputs are action, confirmation and named backup ID only. Bind checkout main SHA to deployed SHA. Password is environment secret `DEMO_USER_PASSWORD`; reuse existing connection secret names from CD.

- [x] Write failing contract tests for manual-only trigger, main restriction, environment/concurrency, secret transport, immutable SHA binding and summary sanitization.
- [x] Implement pinned actions, production guards, private ephemeral request/signature/envelope, Tailscale dispatch and safe run summary. Keep database dumps off Actions artifacts.
- [x] Add package/unit/disposable DB checks to CI without requiring production secrets or server access. Match current workflow lint/runtime versions.
- [x] Document one-time root bootstrap, protected secret, deployment prerequisite, inspect/replace/restore inputs, maintenance behavior, backup retention and failed-recovery handling.
- [x] Run actionlint, Bash syntax, Ruff configured CI rules, all package and scripts tests, disposable PostgreSQL acceptance and relevant repository CI checks. Review branch and resolve material findings.

## Execution

The owner explicitly approved implementation and testing. Use independent file ownership for parallel database, host/request and server-lifecycle tasks, then integrate the workflow and run whole-change checks. Do not dispatch the workflow against the live server as part of this development run.

## Verification boundary

Local package, tooling, signature, installed-container and disposable PostgreSQL tests passed. Runtime queue/cache/frontend boundaries use fault tests; the production server was not accessed or reset. Server bootstrap and manual dispatch remain operator actions after merge/deployment.
