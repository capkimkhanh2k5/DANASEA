# Demo Data workflow for the application server

Status: approved by owner on 2026-10-29; implementation and disposable testing authorized.
Branch: `code/demo-data-workflow`, based on `main` after PR #155.

## Intent and agreed behavior

Provide a manually triggered GitHub Actions workflow named `Demo Data` that
loads `demo_data_handoff` into the existing DANASEA server database.
The owner selected: replace application data with the demo package after a
restore-verified backup, preserving schema and system configuration.
Writing and testing the workflow does not execute a live server reset.

The package currently contains 228 base rows, 42 transaction rows and 77
Cloudinary URL mappings. It requires Flyway >=43, a runtime demo password and
an ownership receipt. Its importer currently reconciles URLs and inserts
missing rows; it deliberately refuses conflicting data. It does not yet
implement a full application-data replacement. Eight committed file hashes
currently disagree with `MANIFEST.sha256`; the package test reproduces this.

## Approaches

1. Backup then add/reconcile demo records, preserving other application data.
   This mostly reuses the importer but does not produce a clean demo dataset.
2. Backup then replace application data, preserving schema/configuration.
   This needs an explicit reset inventory, maintenance window and recovery
   path. The owner selected this option.

Use the existing CD Tailscale connection, pinned SSH host key, deployment key
and production environment. The installed SSH forced command currently
accepts only status/deploy/rollback. Extend that reviewed command with narrowly
validated demo operations instead of adding unrestricted SSH access or exposing
PostgreSQL externally. A one-time host-tool upgrade must be documented and
tested; merging a workflow does not automatically upgrade root-owned tools.

## Workflow contract

- `.github/workflows/demo-data.yml`: manual trigger only, restricted to `main`.
- Operations: inspect/preflight, replace, and restore a named local demo-reset
  backup. Replacement and restore require an exact action-specific confirmation.
- Reuse environment `production` and its existing connection secrets; introduce
  a protected demo password secret, never a plaintext workflow input.
- Share the `cd-production` concurrency group with CD, with cancellation off,
  and the server `.deploy.lock`. Refuse a reset while a deploy/backup is active.
- Bind each request to the deployed release SHA and package checksum; reject
  source/deployment drift. The server executes installed reviewed tooling and
  data from the accepted deployed checkout, never an arbitrary runner shell.
- Authenticate the operation through the existing release signer trust using a
  separate signature namespace. Do not put passwords or database dumps into
  signatures retained as artifacts, command arguments, logs or job summaries.
- Return only operation IDs, backup identifiers, checksums, counts and status
  in the workflow summary. Keep full dumps and private receipts on the server.

## Replacement and preservation boundary

Add a server orchestrator and a reviewed reset operation beside the importer.
Inventory every application table in the deployed schema before mutation.
Use an explicit classified inventory, with foreign-key closure checks, and
refuse unknown tables or dependencies instead of using unrestricted CASCADE.

Replace business/application rows: accounts, vendors, services, inventory,
bookings, orders, payments/refunds, settlements, promotions, reviews, messages,
notifications, authentication sessions/tokens, AI conversations and audit data.
Import all 270 canonical demo rows; rows required by preserved configuration
may be additional and must be identified in the report.

Preserve the complete schema, constraints, extensions and Flyway history;
system configuration values; category safety rules and their required category
rows; and any other configuration inventory proven by the source/schema audit.
Preserve required referenced rows for those protected records. Refuse a demo
fixture conflict with protected configuration instead of silently overwriting
the configuration. Do not recreate the database, rerun old seed migrations,
rewrite migration history, or auto-approve safety certificates. Existing
PENDING safety samples remain PENDING.

Perform reset, fixture import and verification within one PostgreSQL
transaction. Prefer an isolated reset API in the importer so the orchestrator
does not parse SQL or call separate committing cleanup/import commands.
Receipt publication must have a durable recovery record for an uncertain
commit or interrupted filesystem rename.

## Server sequence and recovery

1. Validate request, source revision, manifest, schema inventory, writable
   recovery storage and installed tool capability before taking services down.
2. Enter a short maintenance window: stop the application writer/consumer,
   block public writes and retain the current immutable image references.
   Refuse unsafe outstanding notification work rather than silently purging
   queues or sending old messages after replacement.
3. Use the existing backup tooling to create a private local custom-format dump
   and restore it into a disposable database. Record checksum, source database,
   release SHA and configuration fingerprints. No mutation if verification fails.
   Protect the active operation's recovery point from retention pruning.
4. Rehearse the actual reset/import on a disposable restored copy, then run the
   same reset/import transaction on the target. Refuse concurrent database
   writers; service maintenance alone must not be treated as exclusive access.
5. Verify demo IDs, relationships, amounts, future booking slots and preserved
   configuration before commit. Keep generated passwords private and retain
   the ownership receipt outside the checkout.
6. Invalidate application caches/session state using an audited key policy;
   do not flush an unverified shared Redis database. Restart the same images
   and check health, demo login, catalog and representative read APIs.
7. On failure before commit, roll back. On post-commit verification failure,
   keep writes blocked and restore the verified backup with an explicit tested
   production recovery path (the existing backup helper only restores to
   disposable databases). Resume only after restored health/config checks.
   If recovery fails, leave maintenance active and report the backup ID.

Explicit restore selects a verified backup recorded for this reset operation,
checks checksum and compatible deployed schema, backs up the current state,
then restores under the same maintenance/lock and health-check rules. Never
accept an arbitrary path supplied through the workflow.

## Files and implementation decisions

Expected changes: the new workflow; a demo request/orchestration module;
restricted SSH dispatch and host-tool installer integration; importer reset
support and manifest refresh; focused unit/integration tests; CI coverage for
the package; and an operator runbook for bootstrap, dispatch and recovery.
The builder may choose exact module boundaries and the cache/queue inspection
commands after auditing their current runtime configuration. New unknown
runtime conditions must fail before deletion, not broaden the reset scope.

## Validation and delivery boundary

- Reproduce and repair manifest failure; run all package tests and checksum checks.
- Test branch/SHA/confirmation/signature guards, concurrent lock rejection,
  unavailable backup storage, backup/restore failure and secret-safe reporting.
- Use disposable PostgreSQL with real migrations: old-data sentinel removal,
  configuration preservation, 270 demo rows, idempotent repeated replacement,
  failed import rollback, explicit restore and post-commit failure recovery.
- Test the installed restricted SSH dispatch/bootstrap in owned containers.
- Lint the workflow, shell and Python changes; run affected release tooling
  tests and the required repository CI checks.
- Report local/disposable evidence separately from actual server execution.
  Do not reset the live server while merely implementing this branch.
