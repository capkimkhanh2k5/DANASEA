# Production CI/CD validation — 2026-10-28

> **Current scope after FE handoff:** the owner requested removing all hardening changes under `frontend/`. That directory now matches main `d0bda178ec4624aa2d5167a18c240258bdf9cfcd`. Fresh checks on the restored FE report **16 ESLint errors, 35 warnings** (strict lint exit 1), a passing TypeScript/Vite build, npm audit with 0 vulnerabilities, and **3 HIGH image findings across 2 CVEs** (image scan exit 1). See [the FE handoff](FE_CICD_HANDOFF.md) for every location and acceptance criteria. CI remains blocking. The earlier FE/browser/image/rehearsal successes below are historical evidence for the pre-withdrawal implementation, not current release acceptance. No production-ready or full-green claim applies to the current branch.

Scope: local implementation on `codex/production-cicd-hardening`, started from main `870a1264609a70c86212772917e5fe203d003571` and integrated main `d0bda178ec4624aa2d5167a18c240258bdf9cfcd` locally. Frontend lint/build/image/rehearsal and release-tooling checks were repeated after integration; backend/worker code and tests were unchanged by it. This is measured local evidence, not an assertion that the new controls are active on production.

## Historical checks before FE withdrawal

- Backend: `./mvnw -B clean verify` passed; 2,647 cases, 0 failures, 0 errors, 138 allowlisted skips, 2,509 executed. The report gate confirmed all 6 required classes. Flash Sale HTTP cases exercise all 33 owned service-option fixtures on disposable PostgreSQL 17/Redis, including hold/cancel behavior.
- Deployment tooling: 43 unittests passed. These use real SSH signatures and the real deployment shell, with simulated external deployment commands. Cases cover tampering, immutable SHA bindings, stale history, migration rollback rejection, backup/acceptance/outage failures, policy app directory and isolated rehearsal resources.
- Browser: the local authentication page rendered with 0 captured console errors; other interactive UI flows were not exhaustively exercised.
- Frontend: `npm run lint` passed with `--max-warnings 0`; TypeScript/Vite production build passed; `npm audit --audit-level=high` reported 0 vulnerabilities. Vite still reports a non-blocking >500 kB chunk advisory; no performance/load conclusion is drawn.
- Worker: 7 boundary tests passed inside the new runtime image. All 7 pinned CPU HTTP inference tasks returned 200 without warnings. This validates execution and contracts, not semantic quality.
- Workflow/shell/source: actionlint, Bash syntax, isolated Ruff and `git diff --check` passed. Trivy source vulnerability/secret and worker lock dependency checks passed.

## Historical images before FE withdrawal

All three images were built locally on Docker Desktop Linux arm64. Trivy 0.76.1 reported 0 HIGH/CRITICAL vulnerabilities across the detected OS and application package inventories. No vulnerability ignore list was added. CycloneDX SBOMs were generated for each actual image. Build-time packages outside runtime images are outside these runtime findings.

- backend: `sha256:f0ddc29911cb5acf609b2e57610b4402351f5c4d1d7945198237f6ee10fc5061`. Scan recorded `2026-10-28T13:58:52.055593+07:00`.
- frontend: `sha256:328ca4da64c7c4c868c051880d107f19786e805de7cf0e5090c814b38d172e58`. Scan recorded `2026-10-28T14:08:48.487346+07:00`.
- worker: `sha256:8da2e5307753ce268895c88ce198b620e855272c0520e3a8dcbe543adae36471`. Scan recorded `2026-10-28T13:58:53.202905+07:00`.

Scanner database snapshot: vulnerability DB updated 2026-10-28 01:21 UTC; Java DB updated 2026-10-28 01:07 UTC. Findings can change as databases and dependencies change.

## Historical Compose rehearsal before FE withdrawal

The final three local images completed the release rehearsal in **59.51 seconds**. The rehearsal restored a custom PostgreSQL archive into a random temporary database, preserved an existing marker across migration, started the real backend/frontend/worker/proxy stack, verified public catalog and unauthenticated rejection, invoked actual model inference, and registered/authenticated a synthetic user to hold a booking and create/read an order. All five signed-promotion evidence flags were true. Cleanup removed only the randomized project resources. Baseline SHA was empty (fresh database); this does not establish an actual historical production upgrade or production downtime.

## Independent review

One fresh review identified four material issues. All were corrected: production named volumes/environment leakage in rehearsal, Python application import on worker startup, candidate configuration not extracted from the exact SHA, and installer app directory ignored by the forced-command wrapper. Isolation and custom-directory regressions pass; the rebuilt worker starts and executes native inference; actionlint validates the exact-SHA extraction workflow.

Before FE withdrawal, latest-main integration exposed 4 additional ESLint errors and 6 warnings and they were corrected without rule exceptions. These FE corrections were subsequently removed at the owner's request. The service-edit form also no longer refetches and overwrites editable fields whenever its category changes, and the detail page retains its locale subscription after the keyed-route refactor.

## Production activation still required

- GitHub administrator: enable protected main with Required CI gate/code-owner review and production environment branch restrictions. The live branch was unprotected during inspection; the connected integration cannot administer protection and ruleset access returned an account-plan restriction. No billing or visibility change was made.
- Linux host: install the reviewed root-owned wrapper/policy/helpers, adopt the verified current release, test the real local backup, inspect the six-hour timer, verify SSH status and required commission configuration, then enable the new CD protocol. See [the production runbook](production-release.md).
- Run the new pipeline on GitHub Linux amd64 and rehearse the actual accepted production baseline. The local fresh-database rehearsal does not prove compatibility with unknown production data or older application images.
- Real public HTTPS, provider routes, rollout outage budget, systemd backup execution and host reboot/recovery have not been exercised with this new implementation. No production deployment, provider/payment call or live database replacement was performed in this local verification.
- Backups remain local in the selected server BackUp directory, per the owner. Host-loss recovery, high availability, semantic AI accuracy and payment settlement are outside the evidence above.

No remote push was performed: `docs/GIT_PUSH_PRINCIPLES.md` requires an explicit written user instruction before any push. The implementation and merge are saved as local commits.
