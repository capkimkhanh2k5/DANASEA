# Production CI/CD validation — 2026-10-28

Scope: local implementation on `codex/production-cicd-hardening`, based on main `870a1264609a70c86212772917e5fe203d003571`. This is measured local evidence, not an assertion that the new controls are active on production.

## Executed checks

- Backend: `./mvnw -B clean verify` passed; 2,647 cases, 0 failures, 0 errors, 138 allowlisted skips, 2,509 executed. The report gate confirmed all 6 required classes. Flash Sale HTTP cases exercise all 33 owned service-option fixtures on disposable PostgreSQL 17/Redis, including hold/cancel behavior.
- Deployment tooling: 43 unittests passed. These use real SSH signatures and the real deployment shell, with simulated external deployment commands. Cases cover tampering, immutable SHA bindings, stale history, migration rollback rejection, backup/acceptance/outage failures, policy app directory and isolated rehearsal resources.
- Frontend: `npm run lint` passed with `--max-warnings 0`; TypeScript/Vite production build passed; `npm audit --audit-level=high` reported 0 vulnerabilities. Vite still reports a non-blocking >500 kB chunk advisory; no performance/load conclusion is drawn.
- Worker: 7 boundary tests passed inside the new runtime image. All 7 pinned CPU HTTP inference tasks returned 200 without warnings. This validates execution and contracts, not semantic quality.
- Workflow/shell/source: actionlint, Bash syntax, isolated Ruff and `git diff --check` passed. Trivy source vulnerability/secret and worker lock dependency checks passed.

## Built and scanned images

All three images were built locally on Docker Desktop Linux arm64. Trivy 0.76.1 reported 0 HIGH/CRITICAL vulnerabilities across the detected OS and application package inventories. No vulnerability ignore list was added. CycloneDX SBOMs were generated for each actual image. Build-time packages outside runtime images are outside these runtime findings.

- backend: `sha256:f0ddc29911cb5acf609b2e57610b4402351f5c4d1d7945198237f6ee10fc5061`. Scan recorded `2026-10-28T13:58:52.055593+07:00`.
- frontend: `sha256:2af491e4775b8165c709a2749cfb8552ab4c7aa6b29adbcb8633b511cd311636`. Scan recorded `2026-10-28T14:01:41.453377+07:00`.
- worker: `sha256:8da2e5307753ce268895c88ce198b620e855272c0520e3a8dcbe543adae36471`. Scan recorded `2026-10-28T13:58:53.202905+07:00`.

Scanner database snapshot: vulnerability DB updated 2026-10-28 01:21 UTC; Java DB updated 2026-10-28 01:07 UTC. Findings can change as databases and dependencies change.

## Disposable Compose rehearsal

The final three local images completed the release rehearsal in **53.68 seconds**. The rehearsal restored a custom PostgreSQL archive into a random temporary database, preserved an existing marker across migration, started the real backend/frontend/worker/proxy stack, verified public catalog and unauthenticated rejection, invoked actual model inference, and registered/authenticated a synthetic user to hold a booking and create/read an order. All five signed-promotion evidence flags were true. Cleanup removed only the randomized project resources. Baseline SHA was empty (fresh database); this does not establish an actual historical production upgrade or production downtime.

## Independent review

One fresh review identified four material issues. All were corrected: production named volumes/environment leakage in rehearsal, Python application import on worker startup, candidate configuration not extracted from the exact SHA, and installer app directory ignored by the forced-command wrapper. Isolation and custom-directory regressions pass; the rebuilt worker starts and executes native inference; actionlint validates the exact-SHA extraction workflow.

## Production activation still required

- GitHub administrator: enable protected main with Required CI gate/code-owner review and production environment branch restrictions. The live branch was unprotected during inspection; the connected integration cannot administer protection and ruleset access returned an account-plan restriction. No billing or visibility change was made.
- Linux host: install the reviewed root-owned wrapper/policy/helpers, adopt the verified current release, test the real local backup, inspect the six-hour timer, verify SSH status and required commission configuration, then enable the new CD protocol. See [the production runbook](production-release.md).
- Run the new pipeline on GitHub Linux amd64 and rehearse the actual accepted production baseline. The local fresh-database rehearsal does not prove compatibility with unknown production data or older application images.
- Real public HTTPS, provider routes, rollout outage budget, systemd backup execution and host reboot/recovery have not been exercised with this new implementation. No production deployment, provider/payment call or live database replacement was performed in this local verification.
- Backups remain local in the selected server BackUp directory, per the owner. Host-loss recovery, high availability, semantic AI accuracy and payment settlement are outside the evidence above.
