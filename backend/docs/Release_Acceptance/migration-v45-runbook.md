# V45 migration and recovery runbook

V45 changes `services.latitude/longitude` from numeric(38,2) to numeric(46,10), retaining 36 integer digits. Already rounded historical decimals cannot be recovered.

## Database fixture proof

From repository root, run:

```sh
python3 scripts/ops/rehearse_migration.py --rows 10000 \
  --max-lock-seconds 5 --max-migration-seconds 60 \
  --output /tmp/migration-candidate.json
```

Rows and both budgets are mandatory positive, finite operator inputs. The 10,000-row/5s/60s values are illustrative technical fixture budgets, not a production SLA. Use measured staging size and an approved maintenance window for release evaluation.

The runner uses a unique output directory and run nonce, clean Maven reports and one exact non-skipped testcase. Missing measurements, stale JSON, wrong row counts or failed report verification cannot pass. It never runs against an operator database.

The PostgreSQL test creates V44 with all migrations/FKs, fingerprints every baseline table and all coordinates, takes a real custom-format pg_dump, then observes migration/reader/writer lock contention through pg_locks. SQL failures are propagated. It verifies both numeric definitions, preserved rows/coordinates and exact ten-decimal readback.

Recovery uses pg_restore into another database owned by the same disposable Testcontainers run. All table counts/content/Flyway history must match the pre-migration snapshot. An intentional corruption control must be rejected even when row counts and coordinates still agree. The artifact contains actual dump hash, restored data hash, dataset bytes, timings and observed reader/writer executions.

`actual_lock_seconds` measures blocked query duration, not exact ACCESS EXCLUSIVE lock hold time. Database fixture pass does not establish a production maintenance budget or baseline-image startup. Evidence therefore explicitly sets release_ready=false and baseline_application_started=false.

## Candidate-image/staging proof

Use scripts/ops/rehearse.py with a candidate manifest, baseline manifest, verified model directory and immutable image identities. The script labels run-owned resources, checks ownership before cleanup, upgrades the candidate, verifies all ten coordinate decimals and seven worker tasks, then restores into a separate owned database and starts the baseline application against it. A staging pass requires its actual artifact; source code or a unit test is not that artifact.

Before any production rollout, retain a private restore-verified backup and the independently approved dataset/maintenance budget. Rehearsal archives and sessions may contain private data and must not be committed or publicly uploaded.

## Production boundary

Production migration/deployment/restore is a separate authorized operation. Do not execute destructive commands from this runbook merely because CI passed. Flyway does not undo V45; do not cast new decimals back to numeric(38,2). Recover from an independently verified backup into an approved destination, verify integrity and application startup, then perform the approved recovery procedure.

The historical 02/11 migration artifact predates the repaired integrity checks and cannot establish release recovery acceptance.
