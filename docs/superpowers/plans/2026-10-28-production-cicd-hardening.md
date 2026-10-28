# Production CI/CD Hardening Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Close the audited repository-level release gaps with executable controls and measured validation.

**Architecture:** Keep Compose and GitHub Actions. Introduce signed digest manifests, fail-closed promotion/rollback checks, disposable rehearsals, verified local backups, and post-deploy acceptance.

**Tech Stack:** Python 3.11 stdlib, Bash, OpenSSH signatures, Docker Compose, Java 21/Maven, Node 22, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-10-28-production-cicd-design.md

## Global Constraints

- No changes to production data or billing and no live deployment during local implementation.
- Preserve environment overrides and access; secrets never enter logs or source.
- Test infrastructure and restore destinations must be disposable.
- Missing release evidence blocks promotion.

## Review Focus

- Signed manifests with a mismatched SHA, image repository, digest, action, or signer must fail before service mutation.
- Rerunning an old successful CI must never silently downgrade production.
- Changed migrations or infrastructure must not enable an unproven rollback.
- A corrupt/unverified backup and failed acceptance must leave successful release state untouched.
- Test skips and unavailable security databases must not be reported as passing release gates.

### Task 1: Release trust and deployment state

Files: scripts/release.py, scripts/tests/test_release.py, scripts/deploy.sh, docker-compose.prod.yml.

- [x] Write failing tests for manifest validation, signature verification, stale Git history, and migration compatibility.
- [x] Implement digest overrides, signed stdin envelope, immutable saved manifests, and atomic state writes.
- [x] Run release tests and existing deployment guards; update fixtures only for intentional interface changes.

### Task 2: Verified backup and acceptance

Files: scripts/backup.py, scripts/acceptance.py, scripts/tests/test_backup.py, scripts/tests/test_acceptance.py, scripts/deploy.sh, scripts/systemd/*.

- [x] Write failing tests for backup/restore isolation, failed local backup verification and HTTP contract failures; verify acceptance-before-state behavior in the deploy harness.
- [x] Implement reusable backup verification, verified local archive retention, timer units, readiness checks and acceptance-before-state ordering.
- [x] Run full scripts unittest discovery.

### Task 3: Required tests and lint

Files: backend/src/test/.../flashsale/*, scripts/check_test_reports.py, scripts/tests/test_test_reports.py, frontend/src/*, .github/workflows/ci.yml.

- [x] Capture current lint and Flash Sale failures; add report-gate failure tests.
- [x] Give Flash Sale suites PostgreSQL 17/Redis Testcontainers, remove stale schema-version assumptions, enforce critical-suite execution and worker tests.
- [x] Fix ESLint errors at source; run frontend lint/build, Maven verify and worker tests.

### Task 4: Immutable builds and security gates

Files: Dockerfiles, .github/workflows/ci.yml, .github/workflows/cd.yml, .github/dependabot.yml, scripts/ci/*.

- [x] Verify current upstream action/tool/base-image revisions.
- [x] Add pinned tooling, secret/dependency/image scans, SBOM/provenance, candidate digests and signed promotion.
- [x] Validate workflows and execute available local security/build checks; report scanner/provider network failures honestly.

### Task 5: Disposable release rehearsal and operations

Files: scripts/rehearse.py, scripts/tests/test_rehearse.py, docs/production-release.md, scripts/systemd/*.

- [x] Write isolation and incompatible-migration rollback failure tests.
- [x] Rehearse migration, backup/restore and candidate acceptance on disposable Docker resources; bind evidence to candidate images and baseline SHA. Actual historical-production-image rehearsal remains a CD-time check.
- [x] Add host bootstrap, backup/rollback/recovery instructions, required GitHub settings, capacity/downtime measurement and first-release transition instructions.
- [x] Attempt supported GitHub administration; record account-plan/API limits without changing billing or visibility.

### Task 6: Integration verification and review

- [x] Run appropriate full suites and complete available build/rehearsal checks.
- [x] Obtain one fresh code review and address material findings with regression tests.
- [x] Report implemented, verified and externally blocked controls separately. Do not claim production readiness from local checks.

User clarification (2026-10-28): local server BackUp storage is sufficient; off-host replication and age encryption are explicitly out of scope. Default to ~/BackUp/danasea, with an installer override.

## Validation and external work

- Local full Maven verify: 2,647 cases, 0 failures/errors, 138 allowlisted skips (2,509 executed); all 6 required classes present.
- Release tooling: 43 tests pass; Ruff/actionlint/Bash syntax pass.
- Frontend: strict zero-warning lint, TypeScript/Vite build and npm audit pass. Vite reports a non-blocking bundle-size advisory.
- Worker runtime: 7 boundary tests pass; all 7 pinned CPU HTTP inference tasks return 200 without warnings. This does not measure answer quality.
- Three local production images: HIGH/CRITICAL vulnerability scans pass and CycloneDX SBOMs generated. Source dependency/secret checks pass.
- Disposable final image rehearsal: successful dump/restore, migration/data preservation, real inference and booking/order API path.
- Independent review: four material findings resolved (named volume isolation, worker Python imports, exact candidate config revision, policy app directory). Regression tests and real container checks cover the fixes.
- No production deployment, data replacement, account-plan changes or repository-visibility changes performed.
- External controls remain pending: GitHub account administrator protection/environment setup; reviewed Linux host installation and real backup/timer/SSH/provider acceptance. The implementation blocks deployment until controls are available.
- Fresh Linux amd64 GitHub Actions execution and actual older-production-image upgrade rehearsal have not run in this local session.

Latest main d0bda178ec4624aa2d5167a18c240258bdf9cfcd was integrated locally. Its additional 4 ESLint errors and 6 warnings were fixed; the merged frontend passes strict lint/build, image scan and the disposable rehearsal. No push: the new repository push principles require explicit written user authorization.
