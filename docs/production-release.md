# Production releases and recovery

## Release contract

CI runs Java 21 verification, mandatory Flash Sale tests with owned PostgreSQL 17/Redis, Quyet boundary and pinned CPU inference tests, blocking frontend lint (zero errors and zero warnings)/build, dependency/secret checks, workflow/deployment guards, and production image builds/scans. `Required CI gate` must pass; absent suites and unexpected skips fail it. The allowlist in `.ci/test-policy.json` contains only historical superseded ServiceTier tests and live AI probes whose runtime acceptance runs elsewhere.

CD builds candidates once, scans their immutable digests, and emits BuildKit provenance and SBOMs. Candidates have run-specific tags; application deployment and rollback use their signed digest manifests, never `latest`. Private GitHub artifact attestations require Enterprise; the existing CI Ed25519 deployment key instead signs the release manifest with namespace `danasea-release`. This authenticates release authorization/digest integrity; BuildKit provenance separately records build inputs. It is not a claim of SLSA certification or independent builder attestation.

Before signing, CD obtains the current accepted release through the restricted SSH `status` command. It rehearses baseline migration, local dump/restore, candidate migration, preservation of an existing marker, public catalog/auth behavior, authenticated local inference and a real booking/order flow in a disposable Compose project. All test data, networks and volumes belong to that project. Real payment gateways are never charged. The rehearsal seeds an explicit synthetic commission policy only in its disposable database. The signed evidence binds the candidate images and current baseline SHA; the server rejects it if another release has changed that baseline.

The server requires root-owned policy/signer files, verifies the envelope signature, rejects a deploy that would move Git history backwards, checks prerequisites, creates a local restore-verified backup, pulls the exact images and starts the application. Public HTTPS/API checks, authorization rejection, read-only booking-policy readiness, worker inference and primary/fallback provider completions must pass, followed by a 120-second stability window. One-second API sampling enforces a configurable outage budget (default 60 seconds). Only then is release state advanced atomically. Image cleanup cannot make an accepted release appear to fail. The Quyet runtime contains CPU-only PyTorch and a minimal distroless userspace; interactive Tk/curses/readline, DBM, SQLite and the optional libuuid extension are intentionally absent. Copied shared libraries retain Debian package provenance for vulnerability scanning.

## External prerequisites: do these before enabling the new CD

1. **Protect `main`:** require PR review, code-owner review for `.github/`, deployment scripts, Dockerfiles and migrations; require `Required CI gate`; require branches up to date; block force pushes/deletion and restrict bypass. The live branch API must report `protected=true` and include the required gate. Account-plan limits must be resolved by the owner; this change does not upgrade billing or make the repository public.
2. **Production environment:** restrict deployments to protected branches. Store production SSH/Tailscale credentials in this environment. Add reviewers if the team's release policy requires a human gate. `environment: production` by itself does not establish those restrictions.
3. **Registry:** the deploy account needs read access to private GHCR packages. Keep accepted digests available beyond the recovery window. Candidate artifacts are retained for 90 days; GHCR retention must preserve accepted images independently of artifact retention.
4. **Host:** Linux, Python >=3.11, Docker Compose supporting `--wait`, OpenSSH signing support, `flock`, `systemd`, a separate unprivileged deployment user with Docker access, and the existing forced-command authorized key. Install `python3-yaml` if the proxy management panel writes YAML. Keep the current MacBook/operator access unchanged.
5. **Business configuration:** configure the platform commission through the existing admin workflow before release acceptance. CI never invents or overwrites production financial policy.
6. **Local backups:** per the owner's explicit choice, backups stay on this server in `~/BackUp/danasea` unless an absolute alternative is supplied. No rclone, age key, off-host bucket or additional service is required. Directory mode is 700 and archives are 600.

`scripts/check_repository_controls.py` checks branch/gate/environment restrictions at release time. A missing setting, insufficient API permission or unsupported account plan blocks CD rather than treating the check as successful. The integration available in the authoring session cannot administer branch/environment protections; local `gh` also has no authenticated session. Those settings require an account administrator.

## One-time host transition

Keep `PRODUCTION_DEPLOY_ENABLED=false` while installing the reviewed controls. Do not merge/enable a new deployment protocol against an old forced-command wrapper. The new workflow calls `status` before deployment, so an old wrapper rejects it before running a new release.

Get the **public** Ed25519 key corresponding to the existing CI `SSH_PRIVATE_KEY`. Never print or copy its private key into logs. On the host, run the installer from the reviewed checkout as root:

```bash
sudo bash scripts/install-production-controls.sh \
  DEPLOY_USER /absolute/path/to/danasea https://production.example \
  /absolute/path/to/ci-deploy-key.pub SUCCESSFUL_LEGACY_CD_RUN_ID
```

An optional sixth argument chooses an existing local backup location, e.g. `/absolute/path/to/BackUp/danasea`. Otherwise the deploy user's `~/BackUp/danasea` is used. The installer does not change the existing SSH authorized key or operator keys. Verify that its forced command remains `/usr/local/bin/danasea-deploy` with `restrict`.

The installer places helpers in `/usr/local/lib/danasea`, policy files in `/etc/danasea`, and a six-hour backup timer. It imports the current legacy release only when every expected application container is healthy, uses the current SHA tag, and exposes a matching GHCR digest. This is a one-time operator adoption of existing runtime state; it does not fabricate CI rehearsal evidence for the old release. If adoption fails, inspect the actual running release and registry digests; do not invent them.

Review `/etc/danasea/deploy.json` and `/etc/danasea/backup.json`; both must remain root-owned and not group/other writable. Signer rotation and changes to local policy are explicit operations: the installer refuses silently replacing different existing policy files. Test a backup as the deployment user before enabling CD:

```bash
python3 /usr/local/lib/danasea/backup.py --config /etc/danasea/backup.json
systemctl list-timers danasea-backup.timer
systemctl status danasea-backup.service
journalctl -u danasea-backup.service --since today
```

Then run the repository-policy checker with a token that can read the repository/environment, verify the restricted SSH `status` output, and enable `PRODUCTION_DEPLOY_ENABLED=true`. The first hardened release is expected to block automatic rollback if its infrastructure configuration differs from the adopted baseline (e.g. newly pinned PostgreSQL). That requires a forward-recovery plan before rollout.

## Backups and explicit restoration

Each backup uses `pg_dump -Fc --no-owner --no-privileges`, restores the entire archive to a random `danasea_restore_*` database on the same PostgreSQL instance, checks that application and Flyway tables exist, drops only that temporary database, computes a SHA-256 checksum and publishes the verified archive. A failed dump or restore aborts deployment. The timer and deployment share a lock. Retention keeps 28 successful local backups; unrelated files in the BackUp folder are untouched.

Measure backup duration and disk requirements with representative data before relying on the one-hour timer timeout. Six-hour scheduling is a target interval, not a guaranteed RPO: inspect timer/journal failures. External uptime alerts remain outside this change, consistent with the prior operational choice. Local-only storage does not provide recovery from losing this server; it is the explicitly selected scope.

To rehearse an existing archive, first compare its SHA-256 with its `.json` metadata. Create a new database whose name starts with `danasea_restore_`, then restore the custom archive into it with `pg_restore --no-owner --no-privileges --exit-on-error`. Never pass the live database as the target. The Python `backup.restore(archive, container, database)` helper enforces that temporary-name boundary. Validate business invariants in the restored database before considering any production recovery.

A live database replacement requires a maintenance window, stopped writers/consumers, a current backup of the current state, transaction reconciliation and explicit operator approval. Automatic application rollback never restores a database or silently discards post-backup bookings/payments.

## Rollback and forward recovery

Normal deployment advances the accepted Git ancestry. An old CI rerun cannot downgrade production. For an intentional rollback, manually dispatch CD from `main`, providing a previously accepted SHA and the successful CD run containing its `accepted-release` artifact. The pipeline rescans/rehearses those exact images, signs action `rollback`, and the server verifies that the target was previously accepted and is an ancestor of the running release.

Both automatic and manual rollback require unchanged migration files and compatible data-service, proxy, worker mount/runtime, network and volume configuration. Backend, frontend and worker are the only restarted services during automatic rollback (`--no-deps`). Changed migrations disable rollback regardless of a green startup probe: recover forward using an additive migration/new image, or perform an explicitly planned database recovery. Design schema changes as expand/contract releases so older application versions remain usable across the expansion phase.

If health, acceptance or the outage budget fails, release state remains on the last accepted SHA. The script attempts application rollback only when the compatibility contract passes. If rollback fails or is blocked, inspect local service state, recover forward and rerun acceptance. State describes accepted releases, not a guarantee that currently running containers match it after a failed deployment.

## Availability and operational boundary

This remains a single-host, single-instance Compose deployment. Public API samples and measured outage budgets are retained in `.release-events/<sha>.json`. They quantify this rollout's observed interruption; they do not create zero downtime, host failover, load capacity or an availability SLA. Rehearsal elapsed time similarly does not measure production downtime. Blue/green traffic switching, another host and recovery from host loss require additional infrastructure and an explicit availability requirement.

Provider acceptance performs one small completion through each configured primary/fallback route and suppresses response content. It proves connectivity/authentication/response contracts at that moment; it does not grade AI quality, payment-provider settlement, authorization isolation for every role or business load capacity. The database/booking/order rehearsal uses synthetic data; provider-specific payment acceptance remains a separate controlled validation.

## Local validation

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_test_reports.py backend/target/surefire-reports --policy .ci/test-policy.json
bash -n scripts/deploy.sh scripts/install-production-controls.sh
actionlint
```

Run `./mvnw -B clean verify` from `backend`, `npm ci && npm run lint && npm run build` from `frontend`, and the worker unittest/smoke commands in CI. For a disposable local image rehearsal:

```bash
python3 scripts/rehearse.py \
  --model-dir /absolute/path/to/verified/Quyet-Small \
  --backend-image LOCAL_BACKEND_IMAGE \
  --frontend-image LOCAL_FRONTEND_IMAGE \
  --quyet-image LOCAL_WORKER_IMAGE \
  --output /tmp/danasea-rehearsal.json
```

CI/CD use signed, namespace-validated digest manifests instead of local image names. Rehearsal diagnostics contain only disposable data and stay in a mode-600 local failure log; production diagnostics intentionally omit application/provider/customer logs. Do not upload whole database dumps, `.env`, OAuth tokens, SSH keys, private manifests or raw provider responses as workflow artifacts.
