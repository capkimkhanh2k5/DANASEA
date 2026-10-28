# Production release controls

The approved audit remediation preserves the single-host Docker Compose deployment. No production data, provider credentials, GitHub billing, or live deployment is changed during implementation.

## Approach

Keep CI and CD separate. CI must run worker boundary tests, Flash Sale integration tests on isolated PostgreSQL 17/Redis, lint, dependency/secret scans, and production Docker builds. CD builds each candidate once, scans it, records SBOM/provenance and immutable digests, rehearses migration and recovery against disposable infrastructure, then signs a release manifest using the existing deployment SSH key. The forced command accepts the signed manifest on stdin and verifies it against a root-owned signer policy before deploying digest-pinned images. GitHub attestations for private repositories require Enterprise; a signed manifest avoids silently depending on an unavailable paid feature. It proves release authorization and digest integrity, while BuildKit provenance records build inputs; these are distinct guarantees.

Automatic deploy only advances the deployed Git history. Explicit rollback requires a separately signed rollback authorization and a rehearsed compatibility contract. Any changed migration disables application rollback; this implementation does not attempt to prove reverse schema compatibility. The first hardened release blocks rollback to a legacy release when infrastructure differs or a digest manifest is unavailable.

Backup uses PostgreSQL custom-format archives, verifies restore into an isolated database, and retains the verified archive in the operator-selected local BackUp directory before migration. Each deployment attempt creates exactly one verified archive; retain at most seven archives. There is no scheduled backup service or timer. Credentials remain server-side. Restore is explicit and never automatically overwrites the live database.

Acceptance checks the public HTTPS frontend/API, authorization rejection, worker authenticated inference, and provider inference. Destructive booking/payment checks run only in isolated rehearsal. Successful acceptance and a configurable stability window precede atomic state publication. A single host permits measurable maintenance downtime; high availability needs additional infrastructure and is not represented as implemented.

## Constraints

- Preserve environment overrides and existing MacBook/server access.
- Never log credentials, provider output, database contents, or private manifests containing secrets.
- No test may use a shared database; Flash Sale tests own disposable infrastructure.
- Pin tools/actions/base images and fail closed on missing evidence or configuration.
- Repository/environment protection is applied only through supported authenticated administration; a plan/permission error remains an explicit external blocker.

## Verification

Exercise release validation/signatures, stale deployment rejection, compatibility decisions, backup corruption/restore failures, acceptance failures, and state publication with automated tests. Run full Maven verify, worker tests, frontend lint/build, workflow validation, image builds/scans and disposable release rehearsal where the local runtime supports them. Record real boundaries and outstanding host/GitHub setup in the production runbook.

User clarification (2026-10-28): local server BackUp storage is sufficient; off-host replication and age encryption are explicitly out of scope. Default to ~/BackUp/danasea, with an installer override.

User correction (2026-10-28): backup only immediately before deploy, one archive per attempt, maximum seven retained archives. Periodic backups are not required and their draft systemd units have been removed.
