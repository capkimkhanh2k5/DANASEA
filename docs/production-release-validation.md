# Production CI/CD validation — 2026-10-28

> **Current scope:** the owner has now authorized fixing frontend issues directly and removing the former FE handoff. FE source/image fixes are restored. Fresh strict lint reports zero errors/warnings, TypeScript/Vite build and npm audit pass, and the rebuilt FE image has zero HIGH/CRITICAL findings. Earlier withdrawal/failure results below are historical checkpoints. The complete PR gate and host activation still require their own evidence.

Scope: local implementation on `codex/production-cicd-hardening`, started from main `870a1264609a70c86212772917e5fe203d003571` and integrated main `d0bda178ec4624aa2d5167a18c240258bdf9cfcd` locally. Frontend lint/build/image/rehearsal and release-tooling checks were repeated after integration; backend/worker code and tests were unchanged by it. This is measured local evidence, not an assertion that the new controls are active on production.

## Earlier implementation checks

- Backend: `./mvnw -B clean verify` passed; 2,647 cases, 0 failures, 0 errors, 138 allowlisted skips, 2,509 executed. The report gate confirmed all 6 required classes. Flash Sale HTTP cases exercise all 33 owned service-option fixtures on disposable PostgreSQL 17/Redis, including hold/cancel behavior.
- Deployment tooling: 43 unittests passed. These use real SSH signatures and the real deployment shell, with simulated external deployment commands. Cases cover tampering, immutable SHA bindings, stale history, migration rollback rejection, backup/acceptance/outage failures, policy app directory and isolated rehearsal resources.
- Browser: the local authentication page rendered with 0 captured console errors; other interactive UI flows were not exhaustively exercised.
- Frontend: `npm run lint` passed with `--max-warnings 0`; TypeScript/Vite production build passed; `npm audit --audit-level=high` reported 0 vulnerabilities. Vite still reports a non-blocking >500 kB chunk advisory; no performance/load conclusion is drawn.
- Worker: 7 boundary tests passed inside the new runtime image. All 7 pinned CPU HTTP inference tasks returned 200 without warnings. This validates execution and contracts, not semantic quality.
- Workflow/shell/source: actionlint, Bash syntax, isolated Ruff and `git diff --check` passed. Trivy source vulnerability/secret and worker lock dependency checks passed.

## Earlier built images

All three images were built locally on Docker Desktop Linux arm64. Trivy 0.76.1 reported 0 HIGH/CRITICAL vulnerabilities across the detected OS and application package inventories. No vulnerability ignore list was added. CycloneDX SBOMs were generated for each actual image. Build-time packages outside runtime images are outside these runtime findings.

- backend: `sha256:f0ddc29911cb5acf609b2e57610b4402351f5c4d1d7945198237f6ee10fc5061`. Scan recorded `2026-10-28T13:58:52.055593+07:00`.
- frontend: `sha256:328ca4da64c7c4c868c051880d107f19786e805de7cf0e5090c814b38d172e58`. Scan recorded `2026-10-28T14:08:48.487346+07:00`.
- worker: `sha256:8da2e5307753ce268895c88ce198b620e855272c0520e3a8dcbe543adae36471`. Scan recorded `2026-10-28T13:58:53.202905+07:00`.

Scanner database snapshot: vulnerability DB updated 2026-10-28 01:21 UTC; Java DB updated 2026-10-28 01:07 UTC. Findings can change as databases and dependencies change.

## Earlier Compose rehearsal

The final three local images completed the release rehearsal in **59.51 seconds**. The rehearsal restored a custom PostgreSQL archive into a random temporary database, preserved an existing marker across migration, started the real backend/frontend/worker/proxy stack, verified public catalog and unauthenticated rejection, invoked actual model inference, and registered/authenticated a synthetic user to hold a booking and create/read an order. All five signed-promotion evidence flags were true. Cleanup removed only the randomized project resources. Baseline SHA was empty (fresh database); this does not establish an actual historical production upgrade or production downtime.

## Independent review

One fresh review identified four material issues. All were corrected: production named volumes/environment leakage in rehearsal, Python application import on worker startup, candidate configuration not extracted from the exact SHA, and installer app directory ignored by the forced-command wrapper. Isolation and custom-directory regressions pass; the rebuilt worker starts and executes native inference; actionlint validates the exact-SHA extraction workflow.

Before FE withdrawal, latest-main integration exposed 4 additional ESLint errors and 6 warnings and they were corrected without rule exceptions. They were temporarily removed at the owner's request and have now been restored under the latest instruction. The service-edit form also no longer refetches and overwrites editable fields whenever its category changes, and the detail page retains its locale subscription after the keyed-route refactor.

## Production activation still required

- GitHub administrator: enable protected main with Required CI gate/code-owner review and production environment branch restrictions. The live branch was unprotected during inspection; the connected integration cannot administer protection and ruleset access returned an account-plan restriction. No billing or visibility change was made.
- Linux host: install the reviewed root-owned wrapper/policy/helpers, adopt the verified current release, verify the single predeploy backup, seven-archive retention and SSH status and required commission configuration, then enable the new CD protocol. See [the production runbook](production-release.md).
- Run the new pipeline on GitHub Linux amd64 and rehearse the actual accepted production baseline. The local fresh-database rehearsal does not prove compatibility with unknown production data or older application images.
- Real public HTTPS, provider routes, rollout outage budget, predeploy backup execution and host reboot/recovery have not been exercised with this new implementation. No production deployment, provider/payment call or live database replacement was performed in this local verification.
- Backups remain local in the selected server BackUp directory, per the owner. Host-loss recovery, high availability, semantic AI accuracy and payment settlement are outside the evidence above.

At that initial local checkpoint, no remote push had been performed. The owner subsequently authorized pushing the hardening branch, and the changes are available in [PR 117](https://github.com/capkimkhanh2k5/DANASEA/pull/117); no production merge/deployment has been performed by this task.

## Backup scope correction

The owner requires one backup before each deployment attempt and at most seven retained archives. The draft periodic backup service/timer and installer schedule have been removed. The latest archive is always retained, older archives rotate only after successful verification, and a legacy retention setting above seven is capped. No server schedule or production backup was modified during this local change.

Verification of the backup correction: all 48 release-tooling unittests passed; isolated Ruff, actionlint, Bash syntax and diff whitespace checks passed. The real deploy-shell harness confirms exactly one backup call before image pull/service update. Retention tests exercise real archive/metadata files, with only Docker dump/restore boundaries simulated. Frontend lint/image blockers were delegated at that checkpoint; that temporary delegation is superseded.

## Local production server recheck

After Tailscale was enabled, operator SSH with strict host-key checking succeeded. The actual server is Linux x86_64 with Python 3.14.4, Docker 29.9.2 and Compose 5.6.1; it has four CPUs, approximately 3.2 GiB total RAM and 62.6 GiB free disk at inspection. This matches the workflow's Linux amd64 build target. Disposable rehearsal stays on the GitHub runner, rather than adding a second application stack to this small production host.

All seven existing production containers were healthy. HTTPS frontend, catalog JSON, unauthenticated order rejection and hidden actuator checks passed from the server. Authenticated native worker inference and one small completion through each configured primary/fallback provider route passed; response bodies and credentials were suppressed. This is runtime connectivity/contract evidence for the existing release, not semantic AI quality or validation of the unpublished new image. No payment or booking was created on production.

The server still runs accepted release `870a1264609a70c86212772917e5fe203d003571`. The latest main CD run `40633563219` built images successfully but its home-server deploy job was skipped; it does not establish that main `d0bda178ec4624aa2d5167a18c240258bdf9cfcd` reached production. Main remained unprotected during the live branch API check.

The existing root-owned forced-command wrapper has no group/other write permission and already implements predeploy-only backups with retention seven. It does not support the new signed envelope or restricted status command, and `/etc/danasea/deploy.json` is absent. No backup agent, service or schedule is needed. To use this new signed protocol, update the forced-command script/helpers and trusted policy once; leaving the current wrapper unchanged will block the new CD safely. No server files, credentials, schedules or containers were changed in this inspection.

The production database is approximately 11.1 MiB. `PLATFORM_COMMISSION_RATE` has zero rows in `system_configs`; the new read-only booking-readiness acceptance gate will block release until the owner configures the real business rate through the existing admin workflow. The synthetic rehearsal rate is never copied to production.

The installer was corrected to validate configuration before updating helpers and to adopt the healthy legacy release before atomically replacing the forced command. A failed validation/adoption preserves the existing deploy entrypoint. The deploy account must be non-root. Four new Linux-container installer regressions cover these transitions without changing the author's machine or production server.

Pre-push verification after the local-server recheck: 52/52 release-tooling tests passed, including four Linux-container installer regressions; Ruff, actionlint, Bash syntax and source vulnerability/secret checks passed. Fresh frontend production build and npm audit passed; the built authentication page rendered with zero captured console errors. Frontend still matched main at that checkpoint; the latest instruction now permits source fixes. Operator authorized pushing the hardening branch; production deployment/host installation remains unperformed. The last actual successful home-server deployment matching the running release is CD run `40446270790`; do not adopt the newer build-only run as deployment evidence.

The live PostgreSQL server reports version 17.11, compatible with the pinned PostgreSQL 17 image family. The existing backup directory contains seven legacy gzip archives. A first-transition retention regression now ensures legacy application archives and new verified dumps share the same seven-snapshot limit when that directory is reused. Unrelated files remain untouched.

The first GitHub PR run passed all 52 deployment-tooling tests and the seven-task CPU worker job. Source scanning stopped at Maven Central HTTP 429 rather than treating unavailable evidence as a pass. The quality job now waits for backend verification and restores its Maven cache before resolving POMs for Trivy. Frontend failures in that run belonged to the temporary unmodified-FE state; FE is now being fixed directly.

## Frontend correction restored under the latest instruction

The owner authorized fixing FE directly and deleting its former handoff report. The source corrections fix the original 16 ESLint errors and 35 warnings without rule suppression. They preserve route/locale updates, explicitly initialize derived state, retain request loading/retry behavior, type HTTP errors and Google callbacks, and avoid service-detail fetch dependencies that caused refetch loops. The service-edit form loads categories separately from the service so changing category does not overwrite unsaved fields.

Fresh strict lint has zero errors/warnings; TypeScript/Vite build and npm audit pass. The rebuilt pinned frontend runtime was scanned again with Trivy: zero HIGH/CRITICAL findings, with no vulnerability ignore list. The source still builds non-root Nginx and retains configurable Google client ID handling. The removed FE handoff has no remaining links in current documentation.

Browser smoke used an owned disposable Compose project and synthetic customer/vendor accounts. Customer login and the real created order rendered, service detail rendered with availability, changing language refetched the detail view, the vendor service form retained an unsaved edited name after selecting a different category, and the vendor schedule displayed the seeded slot. No payment or production write was performed. The HTTP loopback browser setup needed a temporary proxy adaptation because the production backend correctly rejects an HTTP CORS origin; production CORS policy/source files were not weakened. A missing external weather result returned a handled 404 in the synthetic environment and was logged by the existing weather widget; these checks are not a zero-console-error assertion for every page or proof of external weather-provider availability.

Fresh FE image: `sha256:328ca4da64c7c4c868c051880d107f19786e805de7cf0e5090c814b38d172e58`, scanned `2026-10-28T16:05:58.17171+07:00`.

The preceding GitHub run passed the complete backend Maven/report gate, the native seven-task worker job, backend/worker image scans and deployment/source quality checks. Its only remaining failures were the then-unmodified frontend lint/image jobs, which this correction now addresses.

After browser inspection, the unmodified release-rehearsal entrypoint was run again against the final images and passed in 52.72 seconds. Restore verification, existing-data preservation, booking/order and native worker flags were all true; only owned temporary resources were removed. This clean run did not use the browser-only loopback proxy adaptation. The local deployment-tooling suite also passed all 53 tests after restoring FE.

## Backend CI failures: reproduced and corrected

The final FE commit `d18c172` passed GitHub frontend/worker and all three image jobs. Its first backend attempt failed an immediate user-cache read; the diagnostic second attempt failed transactional cache eviction and 39 AI fixtures with missing `users.locale` defaults. An unchanged local pass and a 500-repetition probe had not established that these bugs were fixed. The owner explicitly authorized backend fixes.

Two root causes were reproduced before implementation:

- Spring Data Redis 4.1.2 defaults to asynchronous cache writes/eviction/clear with Lettuce. Transaction awareness schedules eviction after commit, but does not itself wait for that asynchronous Redis operation. Three regression cases using the real configured cache and real Redis, with only the asynchronous transport deliberately deferred, failed deterministically on the old configuration: put returned before visibility, and evict/clear returned while stale values remained. `CacheConfig` now uses an immediate-write writer while retaining transaction awareness and the existing serializer/TTL. See [the Spring API contract](https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/cache/RedisCacheWriter.RedisCacheWriterConfigurer.html).
- `ServiceIntegrationE2ETest` activated the test profile's `create-drop` against the PostgreSQL database shared by security/AI suites. Running Service before AI reproduced missing migration history/schema; a new test-profile regression also reproduced the missing locale default. The shared base now enforces Flyway migration and Hibernate validation for all inherited contexts. Global fixture deletion then exposed a real foreign-key conflict in the full suite; Service now uses unique transactional fixtures/rollback, and RBAC deletes only the user IDs it created. The old polling sleep for email-cache eviction was removed so the assertion checks immediate visibility.

Four new regression cases fail before these fixes and pass afterwards. The complete final local `clean verify` passes **2,651 cases: 2,513 executed, 138 allowlisted skips, zero failures/errors**. The report policy passes and now requires nine classes, including RBAC and the new cache/schema regressions. The 53 deployment-tooling tests, strict FE lint/build, actionlint, Bash syntax and diff whitespace checks also pass. A second full suite with alphabetical class ordering and GitHub CI are separate follow-up evidence; no production installation/deployment was performed.

The production runbook now includes Vietnamese, click-by-click GitHub setup. It also identifies that the current FE Admin Settings page still uses mock/local state, so production commission must be persisted and read back through the real Admin API.
