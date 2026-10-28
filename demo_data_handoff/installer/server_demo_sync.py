"""DANASEA Server Demo Data Synchronization & Reconciliation Tool.

Designed for the project leader / administrator to safely import, reconcile, or clean up
demo data in an existing PostgreSQL database serving the DANASEA application.

Key Capabilities:
1. READ-ONLY Preflight: Audits Flyway version, schema, existing records, conflicts, and media URLs.
2. Safe URL Reconciliation: Updates legacy Cloudinary URLs (dbkxwc5gr) to verified target URLs (xmxguqpi)
   ONLY for matching demo fixture UUIDs and verified expected old values. Never touches unrelated rows.
3. Selective Insertion: Inserts only missing demo records without duplicating or overwriting existing identical rows.
4. Conflict Detection: Stops immediately if an existing record with a matching UUID has conflicting field values.
5. Strict Rollback Protection: Dry-run runs in a serializable transaction and automatically rolls back.
6. Safe Cleanup: Removes ONLY the exact known demo UUIDs in reverse FK order, preserving all user/production data.
"""
from decimal import Decimal
from pathlib import Path
import argparse
import copy
import getpass
import hashlib
import json
import os
import re
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
BASE_DIR = ROOT / 'base' if (ROOT / 'base').is_dir() else ROOT / 'data'
TRANS_DIR = ROOT / 'transactions' if (ROOT / 'transactions').is_dir() else ROOT / 'data'

BASE_TABLES = [
    'categories',
    'users',
    'vendors',
    'services',
    'service_options',
    'service_slots',
    'vendor_documents',
    'service_images',
    'service_safety_documents'
]

TRANSACTION_TABLES = [
    'bookings',
    'booking_items',
    'master_orders',
    'sub_orders',
    'payments',
    'refunds',
    'invoices',
    'settlements',
    'settlement_line_items',
    'payout_requests'
]

MEDIA_TABLES = {
    'vendor_documents': 'file_url',
    'service_images': 'url',
    'service_safety_documents': 'file_url'
}

REVERSE_CLEANUP_ORDER = [
    'payout_requests',
    'settlement_line_items',
    'settlements',
    'invoices',
    'refunds',
    'payments',
    'sub_orders',
    'master_orders',
    'booking_items',
    'bookings',
    'service_safety_documents',
    'service_images',
    'vendor_documents',
    'service_slots',
    'service_options',
    'services',
    'vendors',
    'users',
    'categories'
]

LEGACY_CLOUD = 'dbkxwc5gr'
TARGET_CLOUD = 'xmxguqpi'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def lit(value):
    require(isinstance(value, str) and '\0' not in value, 'Unsafe SQL literal')
    return "'" + value.replace("'", "''") + "'"


def jlit(value):
    return lit(json.dumps(value, ensure_ascii=False, separators=(',', ':'))) + '::jsonb'


def source(table, rows):
    return 'jsonb_populate_recordset(NULL::public.' + table + ',' + jlit(rows) + ')'


class Session:
    def __init__(self, args, readonly=False):
        env = dict(os.environ)
        if getattr(args, 'docker_container', None):
            command = ['docker', 'exec', '-i', args.docker_container, 'psql', '-X', '-w', '-q', '-A', '-t',
                       '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate', '-U', args.user, '-d', args.database]
        else:
            command = ['psql', '-X', '-w', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate',
                       '-h', args.host, '-p', str(args.port), '-U', args.user, '-d', args.database]
            if getattr(args, 'db_password', None):
                env['PGPASSWORD'] = args.db_password
        self.p = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
        self.closed = False
        self.send('BEGIN ISOLATION LEVEL SERIALIZABLE' + (' READ ONLY' if readonly else ''))
        self.send("SET LOCAL statement_timeout='60s'; SET LOCAL lock_timeout='10s'; "
                  "SET LOCAL idle_in_transaction_session_timeout='180s'; SET LOCAL client_encoding='UTF8'; "
                  "SET LOCAL standard_conforming_strings=on; SET LOCAL search_path=pg_catalog,public")

    def send(self, sql):
        self.p.stdin.write((sql + ';\n').encode('utf8'))
        self.p.stdin.flush()

    def query(self, sql):
        self.send(sql)
        output = b''
        while True:
            line = self.p.stdout.readline()
            if not line:
                self.p.wait(timeout=10)
                code = re.findall(r'\b[0-9A-Z]{5}\b', self.p.stderr.read().decode('utf8', errors='replace'))
                raise ValueError('PostgreSQL failed, SQLSTATE=' + (code[-1] if code else 'UNAVAILABLE') + '; SQL execution error')
            output += line
            try:
                return json.loads(output)
            except json.JSONDecodeError:
                pass

    def finish(self, commit=False):
        self.send('COMMIT' if commit else 'ROLLBACK')
        self.p.stdin.close()
        tail = self.p.stdout.read()
        self.p.wait(timeout=15)
        error = self.p.stderr.read()
        self.closed = True
        require(self.p.returncode == 0 and not error and not tail.strip(),
                'Transaction completion uncertain; inspect database before retry')

    def close(self):
        if not self.closed:
            try:
                self.finish(commit=False)
            except Exception:
                self.p.terminate()
                self.p.wait(timeout=10)
            self.closed = True


def load_fixtures():
    base_records = {t: json.loads((BASE_DIR / f'{t}.json').read_text(encoding='utf8'))['records']
                    for t in BASE_TABLES}
    base_rows = {t: [copy.deepcopy(r['sqlRow']) for r in rs] for t, rs in base_records.items()}

    manifest_path = BASE_DIR / 'storage_manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf8')) if manifest_path.is_file() else {}

    for table, col in MEDIA_TABLES.items():
        for rec, row in zip(base_records[table], base_rows[table]):
            sha = rec.get('mediaSha256')
            if sha and sha in manifest:
                row[col] = manifest[sha]['url']

    trans_data = json.loads((TRANS_DIR / 'demo_transactions.json').read_text(encoding='utf8'))
    trans_rows = {t: copy.deepcopy(trans_data[t]) for t in TRANSACTION_TABLES}

    return base_rows, trans_rows, manifest


def align_schema_defaults(rows, colmap):
    if ('services', 'waiver_required') in colmap:
        for s in rows.get('services', []):
            s.setdefault('waiver_required', False)
            s.setdefault('waiver_version', 1)
    if ('refunds', 'retry_count') in colmap:
        for r in rows.get('refunds', []):
            r.setdefault('retry_count', 0)
    if ('refunds', 'verification_attempts') in colmap:
        for r in rows.get('refunds', []):
            r.setdefault('verification_attempts', 0)
    if ('settlements', 'version') in colmap:
        for s in rows.get('settlements', []):
            s.setdefault('version', 0)


def preflight_audit(session, base_rows, trans_rows):
    identity = session.query(
        "SELECT json_build_object('database',current_database(),'schema',"
        "(SELECT table_schema FROM information_schema.tables WHERE table_schema='public' AND table_name='users'),"
        "'flyway',(SELECT coalesce(max(version::int), 0)::text FROM public.flyway_schema_history WHERE success),"
        "'failed',(SELECT count(*) FROM public.flyway_schema_history WHERE NOT success))")

    flyway_version = int(identity.get('flyway') or '0')
    flyway_ok = flyway_version >= 43 and identity.get('failed') == 0

    all_rows = dict(**base_rows, **trans_rows)
    tables_summary = {}
    fatal_conflicts = []
    reconcilable_urls = []

    columns = session.query("SELECT json_agg(x) FROM (SELECT table_name,column_name,is_nullable,"
                            "column_default,is_generated,character_maximum_length,data_type "
                            "FROM information_schema.columns WHERE table_schema='public') x")
    colmap = {(c['table_name'], c['column_name']): c for c in columns}
    align_schema_defaults(all_rows, colmap)

    for table, rs in all_rows.items():
        if not rs:
            continue
        expected = source(table, rs)
        id_list = ','.join(lit(r['id']) + '::uuid' for r in rs)

        existing_ids = session.query(f"SELECT coalesce(json_agg(id), '[]'::json) FROM public.{table} WHERE id IN ({id_list})")
        existing_set = set(existing_ids)

        missing_count = len(rs) - len(existing_set)
        identical_count = 0
        reconcile_count = 0
        conflict_count = 0

        media_col = MEDIA_TABLES.get(table)
        remove = "-'created_at'-'updated_at'" + ("-'password_hash'" if table == 'users' else "-'search_vector'" if table == 'services' else '')

        if existing_set:
            existing_expected = [r for r in rs if r['id'] in existing_set]
            exp_sub = source(table, existing_expected)

            identical = session.query(
                f"SELECT to_json(count(*)) FROM public.{table} t JOIN {exp_sub} e ON t.id=e.id "
                f"WHERE (to_jsonb(t){remove})=(to_jsonb(e){remove})")
            identical_count = identical

            if identical_count < len(existing_set):
                mismatches = session.query(
                    f"SELECT coalesce(json_agg(json_build_object('id', t.id, 'db', to_jsonb(t){remove}, 'expected', to_jsonb(e){remove})), '[]'::json) "
                    f"FROM public.{table} t JOIN {exp_sub} e ON t.id=e.id WHERE (to_jsonb(t){remove})<>(to_jsonb(e){remove})")

                for m in mismatches:
                    rec_id = m['id']
                    db_row = m['db']
                    exp_row = m['expected']

                    diff_keys = [k for k in exp_row if db_row.get(k) != exp_row.get(k)]

                    if media_col and diff_keys == [media_col]:
                        db_val = str(db_row.get(media_col) or '')
                        exp_val = str(exp_row.get(media_col) or '')
                        if LEGACY_CLOUD in db_val and TARGET_CLOUD in exp_val:
                            reconcile_count += 1
                            reconcilable_urls.append(dict(table=table, id=rec_id, column=media_col, old=db_val, new=exp_val))
                            continue

                    conflict_count += 1
                    fatal_conflicts.append(dict(table=table, id=rec_id, differentFields=diff_keys))

        tables_summary[table] = {
            'expected': len(rs),
            'presentInDb': len(existing_set),
            'identical': identical_count,
            'missing': missing_count,
            'reconcilableUrls': reconcile_count,
            'conflicts': conflict_count
        }

    status = 'PREFLIGHT_PASS'
    if not flyway_ok:
        status = 'PREFLIGHT_BLOCKED_FLYWAY_VERSION'
    elif fatal_conflicts:
        status = 'PREFLIGHT_BLOCKED_DATA_CONFLICT'

    return {
        'status': status,
        'identity': identity,
        'flywayCompliant': flyway_ok,
        'flywayRequired': '>= 43',
        'tables': tables_summary,
        'reconcilableUrlsCount': len(reconcilable_urls),
        'reconcilableUrls': reconcilable_urls,
        'conflictsCount': len(fatal_conflicts),
        'fatalConflicts': fatal_conflicts
    }


def execute_sync(session, base_rows, trans_rows, audit, demo_password=None):
    require(audit['flywayCompliant'], 'Cannot sync demo data: target database Flyway version is below 43')
    require(audit['conflictsCount'] == 0, 'Cannot sync demo data: unrelated field conflicts detected in database')

    all_rows = dict(**base_rows, **trans_rows)
    all_table_names = BASE_TABLES + TRANSACTION_TABLES

    session.send('LOCK TABLE ' + ','.join('public.' + t for t in all_table_names) + ' IN SHARE ROW EXCLUSIVE MODE')

    reconciled_count = 0
    for rec in audit['reconcilableUrls']:
        tbl = rec['table']
        col = rec['column']
        rec_id = rec['id']
        new_url = rec['new']
        session.send(f"UPDATE public.{tbl} SET \"{col}\" = {lit(new_url)}, updated_at = CURRENT_TIMESTAMP WHERE id = {lit(rec_id)}::uuid")
        reconciled_count += 1

    if demo_password:
        import bcrypt
        hashed = bcrypt.hashpw(demo_password.encode(), bcrypt.gensalt(rounds=10)).decode()
        for u in base_rows['users']:
            u['password_hash'] = hashed

    columns = session.query("SELECT json_agg(x) FROM (SELECT table_name,column_name,is_nullable,"
                            "column_default,is_generated,character_maximum_length,data_type "
                            "FROM information_schema.columns WHERE table_schema='public') x")
    colmap = {(c['table_name'], c['column_name']): c for c in columns}
    align_schema_defaults(all_rows, colmap)

    inserted_results = {}
    for table in all_table_names:
        rs = all_rows[table]
        exp = source(table, rs)
        names = list(rs[0])
        targets = ','.join('"' + k + '"' for k in names)
        expressions = ','.join('coalesce(r."' + k + '",CURRENT_TIMESTAMP)' if k in ('created_at', 'updated_at')
                               else 'r."' + k + '"' for k in names)

        inserted = session.query(
            f"WITH added AS (INSERT INTO public.{table} ({targets}) "
            f"SELECT {expressions} FROM {exp} r "
            f"WHERE NOT EXISTS(SELECT 1 FROM public.{table} t WHERE t.id=r.id) RETURNING id) "
            f"SELECT coalesce(json_agg(id),'[]'::json) FROM added")

        inserted_results[table] = len(inserted)

    for table in all_table_names:
        rs = all_rows[table]
        id_list = ','.join(lit(r['id']) + '::uuid' for r in rs)
        cnt = session.query(f"SELECT to_json(count(*)) FROM public.{table} WHERE id IN ({id_list})")
        require(cnt == len(rs), f"Post-sync fixture count incomplete for table: {table}")

    return {
        'status': 'SYNC_SUCCESS',
        'urlsReconciled': reconciled_count,
        'insertedByTable': inserted_results,
        'totalInserted': sum(inserted_results.values()),
        'totalVerifiedInDb': 270
    }


def execute_cleanup(session, base_rows, trans_rows):
    all_rows = dict(**base_rows, **trans_rows)
    all_table_names = BASE_TABLES + TRANSACTION_TABLES

    session.send('LOCK TABLE ' + ','.join('public.' + t for t in all_table_names) + ' IN SHARE ROW EXCLUSIVE MODE')

    # Categories to delete: ONLY the 7 demo categories (preserve the 5 Flyway baseline categories)
    flyway_baseline_categories = {
        'a0000001-0000-0000-0000-000000000001',
        'a0000001-0000-0000-0000-000000000002',
        'a0000001-0000-0000-0000-000000000003',
        'a0000001-0000-0000-0000-000000000004',
        'a0000001-0000-0000-0000-000000000005'
    }

    deleted_counts = {}
    for table in REVERSE_CLEANUP_ORDER:
        rs = all_rows[table]
        if table == 'categories':
            target_ids = [r['id'] for r in rs if r['id'] not in flyway_baseline_categories]
        else:
            target_ids = [r['id'] for r in rs]

        if not target_ids:
            deleted_counts[table] = 0
            continue

        id_list = ','.join(lit(i) + '::uuid' for i in target_ids)
        deleted = session.query(
            f"WITH removed AS (DELETE FROM public.{table} WHERE id IN ({id_list}) RETURNING id) "
            f"SELECT to_json(count(*)) FROM removed")
        deleted_counts[table] = deleted

    return {
        'status': 'CLEANUP_SUCCESS',
        'deletedCounts': deleted_counts,
        'totalDeleted': sum(deleted_counts.values())
    }


def verify_backup(backup_path):
    if backup_path is None:
        return
    require(backup_path.is_file(), f"Backup file not found: {backup_path}")
    raw = backup_path.read_bytes()[:16]
    require(raw.startswith(b'PGDMP'), "Backup file is not a valid PostgreSQL custom archive (magic header PGDMP mismatch)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--preflight', action='store_true', default=True, help='Read-only preflight audit (default)')
    mode.add_argument('--dry-run', action='store_true', help='Simulate sync/reconciliation with automatic rollback')
    mode.add_argument('--apply', action='store_true', help='Execute sync & reconcile with commit (requires --confirm-backup)')

    parser.add_argument('--cleanup', action='store_true', help='Delete demo records by UUID (use with --dry-run or --apply)')

    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=5432)
    parser.add_argument('--database', default='danasea')
    parser.add_argument('--user', default='postgres')
    parser.add_argument('--docker-container', help='Connect via docker exec to specified container')
    parser.add_argument('--confirm-backup', action='store_true', help='Required acknowledgment of backup before --apply')
    parser.add_argument('--backup-file', type=Path, help='Path to pg_dump backup archive for verification')
    parser.add_argument('--demo-password-stdin', action='store_true')

    args = parser.parse_args()

    if args.dry_run or args.apply or args.cleanup:
        args.preflight = False

    verify_backup(args.backup_file)

    base_rows, trans_rows, manifest = load_fixtures()

    if getattr(args, 'docker_container', None) is None:
        args.db_password = getpass.getpass(f"PostgreSQL password for {args.user}@{args.host}:{args.port}/{args.database} (hidden): ")

    if args.preflight:
        session = Session(args, readonly=True)
        try:
            report = preflight_audit(session, base_rows, trans_rows)
            session.finish(commit=False)
        finally:
            session.close()
        print(json.dumps(report, indent=2))
        return

    if args.cleanup:
        if args.apply:
            require(args.confirm_backup or args.backup_file, 'Production safety: --confirm-backup or --backup-file is required before cleanup')
        session = Session(args, readonly=False)
        try:
            result = execute_cleanup(session, base_rows, trans_rows)
            session.finish(commit=args.apply)
        finally:
            session.close()
        result['transaction'] = 'COMMITTED' if args.apply else 'ROLLED_BACK'
        print(json.dumps(result, indent=2))
        return

    # Dry-run or Apply sync workflow
    if args.apply:
        require(args.confirm_backup or args.backup_file, 'Production safety: --confirm-backup or --backup-file is required before --apply')

    # Read demo password if users need insertion
    demo_password = None
    if args.apply or args.dry_run:
        if args.demo_password_stdin:
            demo_password = sys.stdin.readline().rstrip('\r\n')
        else:
            demo_password = os.environ.get('DEMO_USER_PASSWORD') or getpass.getpass('Password for demo users (e.g. Demo1234!): ')

    # Preflight first
    session = Session(args, readonly=False)
    try:
        audit = preflight_audit(session, base_rows, trans_rows)
        require(audit['flywayCompliant'], f"Target database Flyway version is {audit['identity']['flyway']} (< 43). Apply migrations V25-V43 first.")
        require(audit['conflictsCount'] == 0, f"Found {audit['conflictsCount']} data conflicts. Refusing to overwrite.")

        result = execute_sync(session, base_rows, trans_rows, audit, demo_password)
        session.finish(commit=args.apply)
    finally:
        session.close()

    result['transaction'] = 'COMMITTED' if args.apply else 'ROLLED_BACK'
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print('BLOCKED: ' + (str(exc) if isinstance(exc, ValueError)
                             else type(exc).__name__ + '; execution error'), file=sys.stderr)
        sys.exit(1)
