# Demo Data on the server

The `Demo Data` workflow replaces application data with the canonical
`demo_data_handoff` package. Existing accounts, bookings and other application
data are removed unless needed by preserved system configuration. Schema,
Flyway history, system configuration and safety rules are retained. Importing
the package does not approve pending safety documents or prove every booking
flow ready for demonstration.

## Before the first run

1. Merge and deploy the workflow's revision through the normal CD process.
   The workflow refuses to operate on a different deployed SHA.
2. The existing root-owned production controls (`deploy.json`, release signer
   policy and restricted SSH wrapper) must already be installed. This demo
   bootstrap extends those controls and refuses mismatched keys or trust files.
   On the Linux server, review and run the root bootstrap from that deployed
   checkout: `sudo bash scripts/install-demo-data-controls.sh /path/to/ci-public-key.pub`.
   Use the public key corresponding to the existing CI SSH private key. This
   installs a bounded `demo-data <sha>` command; normal CD alone cannot upgrade
   the root-owned SSH entrypoint. Do not replace the key with unrestricted SSH.
3. Install the host Python bcrypt dependency from the server's trusted OS
   packages (Ubuntu/Debian: `sudo apt-get install python3-bcrypt`) and confirm
   `python3 -c 'import bcrypt'` succeeds for the deployment account.
   Ensure the production backend uses the distinct, non-superuser database
   runtime role provisioned by `scripts/production.py init-db`. Application
   maintenance must be able to stop database writers before replacement.
4. Set the GitHub environment `production` secret `DEMO_USER_PASSWORD` to a
   strong demo account password, at least 12 characters and at most 72 UTF-8
   bytes. Do not enter it as a workflow input. Reuse CD's `TS_OAUTH_CLIENT_ID`,
   `TS_OAUTH_SECRET`, `SERVER_TAILSCALE_IP`, `SERVER_USER`, `SSH_PRIVATE_KEY`
   and `SSH_KNOWN_HOSTS` secrets. Keep production restricted to protected branches.
5. Ensure private local backup storage has room for the source dump and a
   disposable restored database. Resolve pending notification work before
   replacement. Active inventory holds also block replacement; let them finish
   or expire before retrying so rollback cannot lose seat reservations.
   Unknown tables, cache ownership or concurrent writers block
   the operation; do not bypass those checks to force a reset.

## Run

Open GitHub Actions → **Demo Data** → **Run workflow**, select branch `main`.

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
