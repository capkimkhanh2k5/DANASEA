# DANASEA Demo Data Handoff Package

This self-contained, Git-trackable handoff package contains the complete, verified demo dataset for DANASEA, including 228 base business records and 42 mock transaction records.

All 77 Cloudinary assets are 100% migrated and verified on the target cloud `xmxguqpi`.

---

## 1. Package Contents & Structure

```
demo_data_handoff/
├── base/                              # 228 Base business records & Cloudinary manifests
│   ├── categories.json               # 12 categories
│   ├── users.json                    # 12 users (1 admin, 2 customers, 9 vendors)
│   ├── vendors.json                  # 9 vendor profiles
│   ├── services.json                 # 24 services
│   ├── service_options.json          # 33 service pricing options
│   ├── service_slots.json            # 24 schedule slots
│   ├── vendor_documents.json         # 18 vendor compliance documents
│   ├── service_images.json           # 72 service listing images
│   ├── service_safety_documents.json # 24 service safety documents
│   ├── media_index.json              # 77 media metadata descriptors
│   └── storage_manifest.json         # 77 verified target-cloud storage mappings
├── transactions/                      # 42 Canonical mock transaction records
│   └── demo_transactions.json        # 6 bookings, 6 items, 6 orders, 6 sub-orders,
│                                     # 6 payments, 2 refunds, 3 invoices, 2 settlements,
│                                     # 3 line items, 2 payouts (RFC-4122 UUIDs)
├── installer/                         # Database installers & synchronization tools
│   ├── install_demo_data.py          # Base data importer (isolated dev guard)
│   ├── install_demo_transactions.py  # Mock transaction importer (isolated dev guard)
│   └── server_demo_sync.py           # Authorized server sync, reconciliation & cleanup
├── tests/                             # Automated test suite (30 offline tests)
│   ├── test_package.py               # Base installer safety & integrity tests
│   ├── test_transactions.py          # Financial & domain model tests
│   ├── test_transactions_installer.py# Transaction installer contracts & default alignment
│   └── test_server_demo_sync.py      # Server sync, reconciliation & cleanup logic tests
├── README.md                          # Handoff documentation and execution guide
└── MANIFEST.sha256                    # Cryptographic checksums of all handoff files
```

---

## 2. Expected Record Counts

| Category | Table Name | Record Count | Notes / Key Constraints |
| :--- | :--- | :---: | :--- |
| **Base** | `categories` | **12** | Core service categories and safety rule requirements |
| **Base** | `users` | **12** | 1 Admin, 2 Customers, 9 Vendors (`@danasea.test`, bcrypt hash) |
| **Base** | `vendors` | **9** | Vendor business profiles, tax codes, verified bank accounts |
| **Base** | `services` | **24** | Water sports listings (Jetski, SUP, Surfing, Parasailing, Diving) |
| **Base** | `service_options` | **33** | Pricing options (`SHARED`, `PRIVATE`, `PER_PERSON`, `PER_PACKAGE`) |
| **Base** | `service_slots` | **24** | Schedule slots with unique `(service_id, date, start_time)` |
| **Base** | `vendor_documents` | **18** | Business licenses and safety certificates (`status: PENDING`) |
| **Base** | `service_images` | **72** | Public gallery images (`sort_order 1..3`, unique per service) |
| **Base** | `service_safety_documents` | **24** | Service safety compliance documents (`status: PENDING`) |
| **Base Total** | **9 Entity Tables** | **228** | **Zero duplicate primary keys, zero orphan records** |
| **Transactions** | `bookings` | **6** | Customer reservations (`CONFIRMED`, `CANCELLED`) |
| **Transactions** | `booking_items` | **6** | Item allocations, pricing units, participant counts |
| **Transactions** | `master_orders` | **6** | Order master records (`COMPLETED`, `PAID`, `CANCELLED`) |
| **Transactions** | `sub_orders` | **6** | Vendor sub-orders with commission, waivers, check-in QR |
| **Transactions** | `payments` | **6** | Payment records (`VNPAY`, `MOMO`, `PAYPAL`, `SEPAY`) |
| **Transactions** | `refunds` | **2** | Customer cancellation and dispute refunds |
| **Transactions** | `invoices` | **3** | Generated electronic invoices |
| **Transactions** | `settlements` | **2** | Weekly vendor settlement reconciliations |
| **Transactions** | `settlement_line_items` | **3** | Itemized settlement lines with gross, refund, commission, net |
| **Transactions** | `payout_requests` | **2** | Vendor payout requests (`APPROVED`, `PAID`) |
| **Transactions Total**| **10 Entity Tables** | **42** | **100% RFC-4122 compliant UUIDs** |
| **GRAND TOTAL** | **19 Entity Tables** | **270** | **Fully linked, verified demo fixture dataset** |

---

## 3. Cloudinary Configuration & Media URL Contract

- **Target Cloud:** `xmxguqpi`
- **Root Folder:** `danasea_demo_data_package/`
- **Asset Breakdown:**
  - **35 Public Service Images:** Target folder `danasea_demo_data_package/services/`, delivery type `upload` (PUBLIC). Accessible directly via HTTP 200 without authentication.
  - **18 Authenticated Vendor Documents:** Target folder `danasea_demo_data_package/vendor_documents/`, delivery type `authenticated`. Unsigned access returns HTTP 401.
  - **24 Authenticated Safety Documents:** Target folder `danasea_demo_data_package/service_safety_documents/`, delivery type `authenticated`. Unsigned access returns HTTP 401.
- **Database URL Persistence Contract:**
  Database rows persist clean, **unsigned canonical URLs** (`https://res.cloudinary.com/xmxguqpi/image/authenticated/v.../...png`). Temporary tokenized signatures (`s--...--`) are NEVER stored in database fixtures. The backend dynamically generates expiring download links via `private_download_url` at request time.
- **Legacy Cloud Cleared:** Zero active references to legacy cloud `dbkxwc5gr` remain.

---

## 4. Prerequisites

1. **Python 3.10+** with standard library (no third-party dependencies required for validation; `bcrypt` required only during `--apply` for user password hashing).
2. **PostgreSQL 16 or 17** database with Flyway migrations applied through **V43** (`flyway_schema_history` must report version `43` with `0` failed migrations).
3. **Target Database Safety Guard:** The target database name must start with `danasea_demo_` (e.g. `danasea_demo_local` or `danasea_demo_test`). Connecting to production (`danasea`, `pbl6_httt-postgres-1`, or volume `pbl6_httt_postgres_data`) is strictly blocked.
4. **Isolated Development Flag:** The explicit `--development` flag is required for any database operation.

### Environment Isolation: Local Testing vs Controlled Production Promotion
- **Local / Disposable Database Testing:**
  The importers are strictly configured for isolated environments. The target database **must** start with `danasea_demo_` (e.g. `danasea_demo_local`, `danasea_demo_test`), and the `--development` flag must be explicitly passed. Any attempt to point to a production database (e.g. `danasea`), the production Docker container (`pbl6_httt-postgres-1`), or production Docker volume mounts (`pbl6_httt_postgres_data`) immediately aborts with a fatal validation error.
- **Controlled Production Promotion:**
  These installers are intentionally restricted to development and staging databases. For production promotion, do **not** bypass these safety guards. The database administrator should promote verified data via controlled `pg_dump`/`pg_restore` archives or authorized administrative deployment pipelines during scheduled maintenance.

---

## 5. Execution Guide

### Step 1: Run Offline Automated Tests (0 Database Connections)
```bash
python -B -X utf8 -m unittest discover -s demo_data_handoff/tests -p "test_*.py"
```
*Expected result: 25 tests pass in < 0.2s.*

### Step 2: Validate Data Packages Offline
```bash
# Validate Base Data
python -B -X utf8 demo_data_handoff/installer/install_demo_data.py --validate-only

# Validate Transactions
python -B -X utf8 demo_data_handoff/installer/install_demo_transactions.py --validate-only
```
*Expected output:*
- Base: `{"status": "OFFLINE_VALIDATION_PASS", "counts": {...}, "mediaFiles": 77, "database": "NOT_CONNECTED"}`
- Transactions: `{"status": "OFFLINE_VALIDATION_PASS", "counts": {...}, "totalRecords": 42, "uuid_syntax": {"status": "VALID", "nonHexOccurrences": 0, "normalized": true}}`

### Step 3: Database Dry-Run (With Automatic Rollback)
Always execute Base Data first, followed by Transactions:
```bash
# Dry-run Base Data against local database
python -B -X utf8 demo_data_handoff/installer/install_demo_data.py \
  --dry-run --development --database danasea_demo_test --host 127.0.0.1 --port 5433 --reuse-storage

# Dry-run Transactions against local database
python -B -X utf8 demo_data_handoff/installer/install_demo_transactions.py \
  --dry-run --development --database danasea_demo_test --host 127.0.0.1 --port 5433
```
*Result: Enforces table locks, validates foreign keys and column lengths, and issues `ROLLBACK`.*

### Step 4: Apply to Local Database (Commit)
```bash
# Apply Base Data (inserts 228 records)
python -B -X utf8 demo_data_handoff/installer/install_demo_data.py \
  --apply --development --database danasea_demo_test --host 127.0.0.1 --port 5433 --reuse-storage

# Apply Transactions (inserts 42 records)
python -B -X utf8 demo_data_handoff/installer/install_demo_transactions.py \
  --apply --development --database danasea_demo_test --host 127.0.0.1 --port 5433
```
*Result: Commits records in a single serializable transaction and conducts an independent post-COMMIT verification query.*

---

## 6. Server / Deployed Database Import & Reconciliation (`server_demo_sync.py`)

For the **project leader or server administrator** deploying demo data into an existing deployed database (e.g. `danasea` on a staging or production Docker host), use the dedicated `server_demo_sync.py` utility.

### Key Server Features:
1. **READ-ONLY Preflight Audit:** Detects Flyway migration version, existing fixture UUIDs, missing records, legacy Cloudinary media URLs, and any conflicting data.
2. **Safe URL Reconciliation:** Reconciles legacy Cloudinary media references (`dbkxwc5gr`) to verified target URLs (`xmxguqpi`) **ONLY** for matching demo fixture UUIDs with expected old values. Never modifies unrelated rows.
3. **Selective Insertion:** Inserts missing demo records without duplicating or overwriting existing identical fixtures.
4. **Conflict Guard:** Stops immediately if an existing record has conflicting field values (e.g. modified user details or prices).
5. **Mandatory Backup Confirmation:** Requires `--confirm-backup` (or `--backup-file`) before any write operation.
6. **Reverse FK Cleanup:** Includes a `--cleanup` mode to safely delete only the 270 demo fixture UUIDs in reverse dependency order, leaving all real user/customer data untouched.

### Step-by-Step Server Instructions:

#### Step 1: Ensure Flyway Migrations V25–V43 are Applied
The target database must have Flyway migrations applied through version **43**. Run the standard migration container or backend startup if pending:
```bash
# In production Docker Compose:
docker compose -f docker-compose.prod.yml run --rm migrate
```

#### Step 2: Create and Verify a Database Backup (Mandatory)
Before applying demo data or cleanup, create a PostgreSQL custom archive backup and verify its integrity. Do not rely on `--confirm-backup` alone without an actual verified backup archive:
```bash
# 1. Create a custom archive backup inside container:
docker exec danasea-postgres pg_dump -U postgres -d danasea -F c -b -v -f /tmp/danasea_pre_demo_backup.dump

# 2. Copy the backup to host:
docker cp danasea-postgres:/tmp/danasea_pre_demo_backup.dump ./danasea_pre_demo_backup.dump

# 3. Verify backup archive integrity (must list table data and valid TOC entries):
docker exec danasea-postgres pg_restore --list /tmp/danasea_pre_demo_backup.dump | head -n 30
```
*Note: Pass `--backup-file ./danasea_pre_demo_backup.dump` to the sync tool to verify the archive magic header before any write operation.*

#### Step 3: Run Read-Only Preflight Audit
```bash
# Direct host connection:
python -B -X utf8 demo_data_handoff/installer/server_demo_sync.py \
  --preflight --host 127.0.0.1 --port 5433 --database danasea --user postgres

# Or via Docker container:
python -B -X utf8 demo_data_handoff/installer/server_demo_sync.py \
  --preflight --docker-container danasea-postgres --database danasea
```
*Expected Output: `status: PREFLIGHT_PASS`, `flywayCompliant: true`, `reconcilableUrlsCount: <N>`, `conflictsCount: 0`.*

#### Step 4: Run Server Dry-Run (With Automatic Rollback)
```bash
python -B -X utf8 demo_data_handoff/installer/server_demo_sync.py \
  --dry-run --docker-container danasea-postgres --database danasea
```
*Result: Simulates URL reconciliation and missing record insertion in a serializable transaction, then executes `ROLLBACK`.*

#### Step 5: Apply to Server (Commit with Verified Backup)
```bash
python -B -X utf8 demo_data_handoff/installer/server_demo_sync.py \
  --apply --backup-file ./danasea_pre_demo_backup.dump --docker-container danasea-postgres --database danasea
```
*Result: Reconciles media URLs, inserts missing records, validates 270 demo records in PostgreSQL, and commits.*

#### Step 6: (Optional) Clean Up Demo Records in Future
To safely remove demo data without affecting real customer/vendor records:
```bash
# Dry-run cleanup:
python -B -X utf8 demo_data_handoff/installer/server_demo_sync.py \
  --cleanup --dry-run --docker-container danasea-postgres --database danasea

# Apply cleanup (requires verified backup):
python -B -X utf8 demo_data_handoff/installer/server_demo_sync.py \
  --cleanup --apply --backup-file ./danasea_pre_demo_backup.dump --docker-container danasea-postgres --database danasea
```

---

## 7. Safety & Idempotency Guarantee

- **Zero Duplicates on Re-Run:** The installers use `WHERE NOT EXISTS (SELECT 1 FROM public.<table> t WHERE t.id = r.id)`. Re-running either importer against an already populated database cleanly reports `inserted: 0, existing: <total>` without inserting duplicates or raising primary key conflicts.
- **No Silent Overwrites:** If an existing database record with matching `id` has different field values, the installer immediately aborts with `Existing fixture differs; no overwrite: <table>`.
- **Zero Deletions:** Importers contain zero `DELETE` or `TRUNCATE` operations.
- **Pure Database Inserts:** The transaction importer performs zero external HTTP requests; mock payments, refunds, and webhooks are ingested strictly as database state fixtures.

---

## 7. Troubleshooting

| Error | Root Cause | Solution |
| :--- | :--- | :--- |
| `Database/schema/Flyway mismatch` | Flyway migrations not applied or failed | Run Flyway migrations through V43 and ensure `public.flyway_schema_history` has no failed migrations. |
| `Only isolated DEVELOPMENT targets allowed` | Target DB does not start with `danasea_demo_` | Name your development database `danasea_demo_*`. |
| `Missing prerequisite base records in <parent>` | Transactions run before Base data | Run `install_demo_data.py` before running `install_demo_transactions.py`. |
| `Storage readback/checksum differs` | Cloudinary credentials missing for readback | Use the `--reuse-storage` flag to rely on verified Cloudinary storage metadata in `base/storage_manifest.json`. |
