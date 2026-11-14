# Demo Data on the server

The `Demo Data` workflow replaces application data with the canonical
`DB_Demo` package on the server already configured for production CD.
Existing accounts, bookings and other application
data are removed unless needed by preserved system configuration. Schema,
Flyway history, system configuration and safety rules are retained. Importing
the package does not approve pending safety documents or prove every booking
flow ready for demonstration.

## Operating schedules and equipment

Flyway V46–V49 add cancellation, recurring schedule, equipment and compensation
records. Migrating an existing database keeps the dates and IDs of existing
departures and bookings. Recurring rules and equipment links start empty.
The scheduler generates the rolling 90-day horizon after a vendor saves a rule.

The reviewed `DB_Demo` replacement on Flyway V58 preserves historical booking
departures and sets the upcoming reschedule example relative to the import date.
It seeds daily rules for all 24 published demo services and materializes future
departures through the 90th calendar day in Vietnam time. Each service keeps its
reviewed demo hours and capacity. The backend scheduler extends the window over
time.

Replacement creates named synthetic equipment for the private package examples
and the shared snorkelling example. It skips dates and start times already
represented by a fixture booking anchor. The `demoOperatingSchedule` report
counts the generated records separately from the 687 canonical rows.

Replacement clears the target's previous operating rules, exceptions and
equipment before seeding the demo set. Normal migrations and non-replacement
sync do not seed the rolling window.

The V45 → V49 rehearsal on disposable PostgreSQL preserved the existing values
of 24 departures, 6 bookings, their order and payment records, and a legacy
seat allocation. New schedule and equipment tables remained empty.

## Demo media

The four synthetic disputes each include an illustration for the corresponding
activity. The `DB_Demo/base/storage_manifest.json` entries record the author,
source page, license, linked dispute and SHA-256. To check external media before
a demo, fetch the original URLs and compare the response bytes with the
manifest hashes.

## Before the first run

1. Merge and deploy the workflow's revision through the normal CD process.
   The workflow refuses to operate on a different deployed SHA.
2. The existing root-owned production controls (`deploy.json`, release signer
   policy and restricted SSH wrapper) must already be installed. This demo
   bootstrap extends those controls and refuses mismatched keys or trust files.
   On the Linux server, review and run the root bootstrap from that deployed
   checkout: `sudo bash scripts/demo/install-demo-data-controls.sh /path/to/ci-public-key.pub`.
   Use the public key corresponding to the existing CI SSH private key. This
   installs a bounded `demo-data <sha>` command; normal CD alone cannot upgrade
   the root-owned SSH entrypoint. Do not replace the key with unrestricted SSH.
3. Install the host Python bcrypt dependency from the server's trusted OS
   packages (Ubuntu/Debian: `sudo apt-get install python3-bcrypt`) and confirm
   `python3 -c 'import bcrypt'` succeeds for the deployment account.
   Ensure the production backend uses the distinct, non-superuser database
   runtime role provisioned by `scripts/ops/production.py init-db`. Application
   maintenance must be able to stop database writers before replacement.
4. Use the GitHub environment `production` with CD's `TS_OAUTH_CLIENT_ID`,
   `TS_OAUTH_SECRET`, `SERVER_TAILSCALE_IP`, `SERVER_USER`, `SSH_PRIVATE_KEY`
   and `SSH_KNOWN_HOSTS` secrets. Keep production restricted to protected branches.
   The shared password for imported demo accounts defaults to `danasea@2026`.
   An existing `DEMO_USER_PASSWORD` secret overrides it. Remove the secret
   from the workflow's applicable scopes or set it to the same value to use
   the default. Overrides must be at least 12 characters and at most 72 UTF-8
   bytes.
5. Ensure private local backup storage has room for the source dump and a
   disposable restored database. Resolve pending notification work before
   replacement. Active inventory holds also block replacement; let them finish
   or expire before retrying so rollback cannot lose seat reservations.
   Unknown tables, cache ownership or concurrent writers block
   the operation; do not bypass those checks to force a reset.

## Run

Open GitHub Actions → **Demo Data** → **Run workflow**, select branch `main`.
Run `inspect` first and check its summary before selecting `replace`.

- `inspect`: review preflight metadata without committing a replacement. It can
  also safely resume an interrupted operation proven to have stopped before
  any target commit; an uncertain commit still requires named-backup recovery.
- `replace`: enter confirmation `REPLACE_DEMO_DATA`. The server enters a
  maintenance window, creates and proves a backup, rehearses replacement on a
  restored disposable copy and then replaces data in a transaction. Successful
  replacement restarts the same deployed images and verifies application reads.
- `restore`: select the local backup identifier reported by a previous demo
  operation and enter confirmation `RESTORE_DEMO_DATA`. Arbitrary filesystem
  paths are rejected. Restore also protects the current state before recovery.

After a successful `replace`, sign in on the existing site using a demo account
such as `admin_demo@danasea.test` or `customer_1@danasea.test` and the password
used for the import. The workflow verifies admin sign-in after restarting the
backend.

CD and Demo Data share a concurrency group, and operations take the server's
deployment lock. Do not cancel an operation during replacement/recovery or
manually restart the backend while maintenance is active.

## Recovery and evidence

The job summary exposes only bounded operation metadata and counts. Database
dumps, passwords and ownership receipts are not uploaded as Actions artifacts.
Backups remain private on the server under the existing backup policy. Preserve
the active recovery point and operation journal until the operation is resolved.

An import failure before commit rolls back the transaction. A failure after
commit triggers recovery from the verified source backup. If recovery cannot
be verified, the server stays in maintenance and the operation reports failure.
Inspect the private journal and named backup locally before retrying; a failed
SSH connection does not prove that the database transaction failed.

`MANIFEST.sha256` binds the reviewed fixture files and importer to the signed
request. After intentional package edits, regenerate its covered hashes and
run the package tests; never bypass integrity checking on the server.

Implementation and disposable Docker tests do not execute a live server reset.
The first server run still requires the bootstrap and an explicit manual
workflow dispatch from an authorized operator.
