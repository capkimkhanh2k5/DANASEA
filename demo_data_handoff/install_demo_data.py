from decimal import Decimal, ROUND_HALF_UP
from datetime import date, datetime, timedelta, timezone
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

ROOT = Path(__file__).resolve().parent
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
TABLES = BASE_TABLES

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

ALL_TABLES = BASE_TABLES + TRANSACTION_TABLES

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

FLYWAY_BASELINE_CATEGORIES = {
    'a0000001-0000-0000-0000-000000000001',
    'a0000001-0000-0000-0000-000000000002',
    'a0000001-0000-0000-0000-000000000003',
    'a0000001-0000-0000-0000-000000000004',
    'a0000001-0000-0000-0000-000000000005'
}

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
    require(table in ALL_TABLES, f'Unsupported table: {table}')
    return 'jsonb_populate_recordset(NULL::public.' + table + ',' + jlit(rows) + ')'


def manifest():
    manifest_path = ROOT / 'MANIFEST.sha256'
    require(manifest_path.is_file(), 'Missing MANIFEST.sha256')
    lines = manifest_path.read_text(encoding='utf8').splitlines()
    for line in lines:
        if not line.strip():
            continue
        parts = line.split(None, 1)
        require(len(parts) == 2, 'Invalid manifest line')
        digest, relative = parts
        relative = relative.strip()
        path = (ROOT / relative).resolve()
        require(path.is_relative_to(ROOT) and path.is_file() and
                hashlib.sha256(path.read_bytes()).hexdigest() == digest,
                'Manifest mismatch: ' + relative)
    required = {str(p.relative_to(ROOT)).replace('\\', '/') for p in ROOT.rglob('*')
                if p.is_file() and p.suffix in ('.json', '.py')}
    require(required == {line.split(None, 1)[1].strip() for line in lines if line.strip()},
            'Manifest coverage mismatch')


def validate_rows(rows):
    expected = dict(categories=12, users=12, vendors=9, services=24,
                    service_options=33, service_slots=24, vendor_documents=18, service_images=72)
    require(all(t in rows and len(rows[t]) == n for t, n in expected.items()), 'Unexpected package counts')
    require('service_safety_documents' in rows, 'Missing safety document dataset')
    ids = [r['id'] for rs in rows.values() for r in rs]
    require(all(str(uuid.UUID(i)) == i for i in ids), 'Invalid fixture UUID')
    require(len(ids) == len(set(ids)), 'Duplicate UUID within package')
    by_table = {t: {r['id'] for r in rs} for t, rs in rows.items()}
    for t, column, parent in [('vendors', 'user_id', 'users'), ('services', 'vendor_id', 'vendors'),
                              ('services', 'category_id', 'categories'), ('service_options', 'service_id', 'services'),
                              ('service_slots', 'service_id', 'services'), ('vendor_documents', 'vendor_id', 'vendors'),
                              ('service_images', 'service_id', 'services'), ('service_safety_documents', 'service_id', 'services')]:
        require(all(r[column] in by_table[parent] for r in rows[t]), 'Orphan fixture: ' + t)
    users = {u['id']: u for u in rows['users']}
    require(all(u['password_hash'] is None and u['email'].endswith('@danasea.test') and
                u['is_email_verified'] is True for u in users.values()), 'Unexpected credential/email activation fixture')
    for v in rows['vendors']:
        require(users[v['user_id']]['role'] == 'VENDOR', 'Vendor owner must be VENDOR')
        require(isinstance(v['bank_account_number'], str) and v['bank_account_number'].isdigit(), 'Bank number must be STRING')
        require(users[v['user_id']]['full_name'] == v['business_name'], 'User/Vendor name mismatch')
    require(all(d['status'] == 'PENDING' and d['reviewed_by'] is None and d['reviewed_at'] is None
                for d in rows['vendor_documents']), 'Document approval is forbidden in fixture')
    required_accounts = {'admin_demo@danasea.test': 'ADMIN', 'customer_1@danasea.test': 'CUSTOMER',
                         'customer_2@danasea.test': 'CUSTOMER',
                         **{'vender_' + str(i) + '@danasea.test': 'VENDOR' for i in range(1, 10)}}
    require({u['email']: u['role'] for u in rows['users']} == required_accounts, 'Account role/email mapping differs')
    categories = {c['id']: c for c in rows['categories']}
    needed = {s['id'] for s in rows['services'] if s.get('weather_sensitive') is True or
              categories[s['category_id']].get('requires_safety_cert') is True}
    safety = rows['service_safety_documents']
    require(len(safety) == len(needed) and {d['service_id'] for d in safety} == needed,
            'Safety document coverage differs from category/weather policy')
    require(all(d['status'] == 'PENDING' and d['reviewed_by'] is None and d['reviewed_at'] is None and
                d['rejection_reason'] is None and 'doc_type' not in d for d in safety),
            'Safety SAMPLE must be PENDING and unreviewed; no document-type enum exists')


def validate_transactions(base, trans):
    rows = dict(base, **trans)
    ids = [r['id'] for rs in rows.values() for r in rs]
    require(len(ids) == len(set(ids)) and all(str(uuid.UUID(i)) == i for i in ids), 'Duplicate/invalid transaction UUID')
    indexes = {t: {r['id']: r for r in rs} for t, rs in rows.items()}
    relations = {
        'bookings': {'customer_id': 'users'},
        'booking_items': {'booking_id': 'bookings', 'service_id': 'services', 'vendor_id': 'vendors', 'slot_id': 'service_slots', 'option_id': 'service_options'},
        'master_orders': {'booking_id': 'bookings', 'customer_id': 'users'},
        'sub_orders': {'master_order_id': 'master_orders', 'booking_item_id': 'booking_items', 'vendor_id': 'vendors', 'service_id': 'services', 'slot_id': 'service_slots', 'waiver_accepted_by': 'users'},
        'payments': {'master_order_id': 'master_orders'},
        'refunds': {'sub_order_id': 'sub_orders', 'payment_id': 'payments', 'requested_by': 'users'},
        'invoices': {'master_order_id': 'master_orders'},
        'settlements': {'vendor_id': 'vendors'},
        'settlement_line_items': {'settlement_id': 'settlements', 'sub_order_id': 'sub_orders'},
        'payout_requests': {'vendor_id': 'vendors', 'settlement_id': 'settlements', 'processed_by': 'users'}}
    for table, cols in relations.items():
        for row in trans[table]:
            require(all(row.get(c) is None or row[c] in indexes[parent] for c, parent in cols.items()), 'Orphan transaction: ' + table)
    for b in trans['bookings']:
        items = [i for i in trans['booking_items'] if i['booking_id'] == b['id']]
        require(indexes['users'][b['customer_id']]['role'] == 'CUSTOMER' and
                sum(Decimal(i['price']) * i['quantity'] for i in items) == Decimal(b['total_amount']), 'Booking ownership/amount mismatch')
        for i in items:
            slot = indexes['service_slots'][i['slot_id']]
            require(slot['service_id'] == i['service_id'] and slot['date'] == i['booking_date'] and
                    slot['start_time'] == i['booking_time'] and indexes['services'][i['service_id']]['vendor_id'] == i['vendor_id'] and
                    indexes['service_options'][i['option_id']]['service_id'] == i['service_id'], 'Booking item mapping mismatch')
            require(datetime.fromisoformat(b['created_at']) < datetime.fromisoformat(i['booking_date']+'T'+i['booking_time']+'+07:00'), 'Booking created after slot')
    for o in trans['master_orders']:
        b = indexes['bookings'][o['booking_id']]
        subs = [s for s in trans['sub_orders'] if s['master_order_id'] == o['id']]
        require(o['customer_id'] == b['customer_id'] and Decimal(o['total_amount']) == Decimal(b['total_amount']) ==
                sum(Decimal(s['final_amount']) for s in subs), 'Order ownership/amount mismatch')
    for s in trans['sub_orders']:
        i = indexes['booking_items'][s['booking_item_id']]
        require(all(s[c] == i[c] for c in ('vendor_id', 'service_id', 'slot_id', 'quantity')) and
                indexes['master_orders'][s['master_order_id']]['booking_id'] == i['booking_id'] and
                Decimal(s['unit_price']) == Decimal(i['price']) and
                Decimal(s['subtotal_amount']) == Decimal(s['unit_price']) * s['quantity'], 'Sub-order snapshot mismatch')
        commission = (Decimal(s['commission_basis_amount']) * Decimal(s['commission_rate'])).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
        require(Decimal(s['commission_amount']) == commission and Decimal(s['vendor_payout_amount']) == Decimal(s['commission_basis_amount']) - commission, 'Commission snapshot mismatch')
    for p in trans['payments']:
        order = indexes['master_orders'][p['master_order_id']]
        booking = indexes['bookings'][order['booking_id']]
        require(Decimal(p['amount']) == Decimal(order['total_amount']), 'Payment amount mismatch')
        require(all(p.get(c) is None for c in ('provider_order_id', 'provider_transaction_id', 'raw_webhook_payload', 'webhook_event_id', 'payment_url', 'qr_code_url')) and p['status'] != 'PENDING', 'Synthetic gateway evidence/live intent forbidden')
        state = {'SUCCESS': ('PAID', 'CONFIRMED'), 'REFUNDED': ('REFUNDED', 'CANCELLED'), 'FAILED': ('UNPAID', 'CANCELLED')}
        require(p['status'] in state and (order['payment_status'], booking['status']) == state[p['status']] and
                bool(p['paid_at']) == (p['status'] in ('SUCCESS', 'REFUNDED')), 'Payment/order/booking status contradiction')
    for r in trans['refunds']:
        s = indexes['sub_orders'][r['sub_order_id']]
        p = indexes['payments'][r['payment_id']]
        require(p['master_order_id'] == s['master_order_id'] and p['provider'] == r['provider'] and
                Decimal(r['amount']) == Decimal(s['final_amount']) * Decimal(r['refund_percentage']) / 100, 'Refund payment/amount mismatch')
    for line in trans['settlement_line_items']:
        s = indexes['sub_orders'][line['sub_order_id']]
        require(s['status'] in ('COMPLETED', 'CHECKED_IN', 'PARTIALLY_REFUNDED') and line['excluded_reason'] is None,
                'Settlement contains an ineligible sub-order')
        refund = sum(Decimal(r['amount']) for r in trans['refunds'] if r['sub_order_id'] == s['id'] and r['status'] == 'PROCESSED')
        basis = Decimal(s['commission_basis_amount'])
        gross = basis - (basis * refund / Decimal(s['final_amount'])).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
        commission = (gross * Decimal(s['commission_rate'])).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
        require(indexes['settlements'][line['settlement_id']]['vendor_id'] == s['vendor_id'] and
                [Decimal(line[c]) for c in ('gross_amount', 'refund_amount', 'commission_amount', 'net_amount')] ==
                [gross, refund, commission, gross - commission], 'Settlement line calculation mismatch')
    for s in trans['settlements']:
        lines = [i for i in trans['settlement_line_items'] if i['settlement_id'] == s['id']]
        require(all(Decimal(s[col]) == sum(Decimal(i[item]) for i in lines) for col, item in
                    [('gross_amount', 'gross_amount'), ('commission_amount', 'commission_amount'), ('net_payable_amount', 'net_amount')]), 'Settlement totals mismatch')
        require(sum(Decimal(p['amount']) for p in trans['payout_requests'] if p['settlement_id'] == s['id'] and p['status'] != 'REJECTED') <= Decimal(s['net_payable_amount']), 'Payout exceeds eligible balance')
        require(all(p['vendor_id'] == s['vendor_id'] for p in trans['payout_requests'] if p['settlement_id'] == s['id']), 'Payout vendor mismatch')


def resolve_demo_password(args):
    password = sys.stdin.readline().rstrip('\r\n') if args.demo_password_stdin else os.environ.get('DEMO_USER_PASSWORD')
    if not password and sys.stdin.isatty():
        password = getpass.getpass('Demo account password (hidden; required): ')
    require(password is not None and len(password) >= 12 and len(password.encode('utf8')) <= 72,
            'Supply a demo password of 12+ characters and <=72 UTF-8 bytes through DEMO_USER_PASSWORD or --demo-password-stdin')
    return password


def prepare_slots(base, trans, ownership, today=None):
    # UTC+07 has no daylight-saving transitions; works without a Windows tzdata install.
    today = today or datetime.now(timezone(timedelta(hours=7), 'Asia/Ho_Chi_Minh')).date()
    historical = {i['slot_id'] for i in trans['booking_items']}
    min_slot_date = date(2026, 11, 20)
    default_date = max(today + timedelta(days=7), min_slot_date).isoformat()
    for slot in base['service_slots']:
        if slot['id'] not in historical:
            slot['date'] = ownership.get('slotDates', {}).get(slot['id'], default_date)


def validate_identity(identity, database):
    flyway_version = int(identity.get('flyway') or '0')
    require(identity.get('database') == database and identity.get('schema') == 'public' and
            identity.get('failed') == 0 and flyway_version >= 43,
            'Database/schema/Flyway mismatch; Flyway version >= 43 required')


def load_fixtures():
    base_records = {t: json.loads((BASE_DIR / f'{t}.json').read_text(encoding='utf8'))['records']
                    for t in BASE_TABLES}
    base_rows = {t: [copy.deepcopy(r['sqlRow']) for r in rs] for t, rs in base_records.items()}
    validate_rows(base_rows)

    manifest_path = BASE_DIR / 'storage_manifest.json'
    manifest_data = json.loads(manifest_path.read_text(encoding='utf8')) if manifest_path.is_file() else {}

    for table, col in MEDIA_TABLES.items():
        for rec, row in zip(base_records[table], base_rows[table]):
            sha = rec.get('mediaSha256')
            if sha and sha in manifest_data:
                row[col] = manifest_data[sha]['url']

    trans_data = json.loads((TRANS_DIR / 'demo_transactions.json').read_text(encoding='utf8'))
    trans_rows = {t: copy.deepcopy(trans_data[t]) for t in TRANSACTION_TABLES}
    require(trans_data['classification'] == 'DEVELOPMENT_SYNTHETIC', 'Synthetic classification required')
    validate_transactions(base_rows, trans_rows)

    return base_rows, trans_rows, manifest_data


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
                  "SET LOCAL standard_conforming_strings=on; SET LOCAL search_path=pg_catalog,public; SET LOCAL TIME ZONE 'UTC'")

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
        for stream in (self.p.stdin, self.p.stdout, self.p.stderr):
            try:
                stream.close()
            except (OSError, ValueError):
                pass


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
    require(all((t, key) in colmap for t, rs in all_rows.items() for r in rs for key in r),
            'Fixture column missing from target schema')

    for table in ALL_TABLES:
        rs = all_rows.get(table, [])
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
        'fatalConflicts': fatal_conflicts,
        'businessReadiness': {
            'publishedServices': sum(s['status'] == 'PUBLISHED' for s in base_rows['services']),
            'activeOptions': sum(o['status'] == 'ACTIVE' for o in base_rows['service_options']),
            'pendingSafetySamples': len(base_rows['service_safety_documents']),
            'futureSlots': sum(s['date'] > datetime.now(timezone(timedelta(hours=7))).date().isoformat() for s in base_rows['service_slots']),
            'notice': 'SQL preflight is not demo readiness. Compliance approval, PRIVATE units, APIs and Frontend require separate verification.'}
    }


def execute_sync(session, base_rows, trans_rows, audit, demo_password=None):
    require(audit['flywayCompliant'], 'Cannot sync demo data: target database Flyway version is below 43')
    require(audit['conflictsCount'] == 0, 'Cannot sync demo data: unrelated field conflicts detected in database')

    all_rows = dict(**base_rows, **trans_rows)

    session.send('LOCK TABLE ' + ','.join('public.' + t for t in ALL_TABLES) + ' IN SHARE ROW EXCLUSIVE MODE')

    reconciled_count = 0
    for rec in audit['reconcilableUrls']:
        tbl = rec['table']
        col = rec['column']
        rec_id = rec['id']
        new_url = rec['new']
        session.send(f"UPDATE public.{tbl} SET \"{col}\" = {lit(new_url)}, updated_at = CURRENT_TIMESTAMP WHERE id = {lit(rec_id)}::uuid")
        reconciled_count += 1

    require(demo_password is not None, 'Explicit runtime demo credentials required')
    import bcrypt
    for u in base_rows['users']:
        u['password_hash'] = bcrypt.hashpw(demo_password.encode('utf8'), bcrypt.gensalt(rounds=10, prefix=b'2a')).decode()

    columns = session.query("SELECT json_agg(x) FROM (SELECT table_name,column_name,is_nullable,"
                            "column_default,is_generated,character_maximum_length,data_type "
                            "FROM information_schema.columns WHERE table_schema='public') x")
    colmap = {(c['table_name'], c['column_name']): c for c in columns}
    align_schema_defaults(all_rows, colmap)

    inserted_results = {}
    inserted_ids = {}
    for table in ALL_TABLES:
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
        inserted_ids[table] = inserted

    provisioned = 0
    for u in base_rows['users']:
        provisioned += session.query(
            f"WITH changed AS (UPDATE public.users SET password_hash={lit(u['password_hash'])} "
            f"WHERE id={lit(u['id'])}::uuid AND email={lit(u['email'])} AND role={lit(u['role'])} "
            "AND password_hash IS NULL RETURNING id) SELECT to_json(count(*)) FROM changed")

    for table in ALL_TABLES:
        rs = all_rows[table]
        id_list = ','.join(lit(r['id']) + '::uuid' for r in rs)
        cnt = session.query(f"SELECT to_json(count(*)) FROM public.{table} WHERE id IN ({id_list})")
        require(cnt == len(rs), f"Post-sync fixture count incomplete for table: {table}")

    return {
        'status': 'SYNC_SUCCESS',
        'urlsReconciled': reconciled_count,
        'insertedByTable': inserted_results,
        'insertedIds': inserted_ids,
        'existingNullCredentialsProvisioned': provisioned,
        'totalInserted': sum(inserted_results.values()),
        'totalVerifiedInDb': sum(len(rs) for rs in all_rows.values())
    }


# Replacement accepts only the exact reviewed V1-V43 table/column/FK/trigger inventory.
RESET_INVENTORY_SQL = """
SELECT json_build_object(
 'schemas',(SELECT json_agg(nspname ORDER BY nspname) FROM pg_namespace WHERE nspname !~ '^pg_' AND nspname <> 'information_schema'),
 'tables',(SELECT json_agg(x ORDER BY x.name) FROM (
   SELECT n.nspname||'.'||c.relname name,c.relkind kind,
     (SELECT json_agg(json_build_array(a.attname,format_type(a.atttypid,a.atttypmod),a.attnotnull,a.attgenerated) ORDER BY a.attnum)
      FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped) columns
   FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname !~ '^pg_' AND n.nspname <> 'information_schema' AND c.relkind IN ('r','p','f','v','m')) x),
 'foreignKeys',(SELECT coalesce(json_agg(x ORDER BY x.child,x.name),'[]'::json) FROM (
   SELECT c.conname name,cn.nspname||'.'||ch.relname child,pn.nspname||'.'||pa.relname parent,
     (SELECT json_agg(a.attname ORDER BY k.ord) FROM unnest(c.conkey) WITH ORDINALITY k(num,ord)
      JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num) childColumns,
     (SELECT json_agg(a.attname ORDER BY k.ord) FROM unnest(c.confkey) WITH ORDINALITY k(num,ord)
      JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num) parentColumns,
     pg_get_constraintdef(c.oid) definition,c.convalidated validated
   FROM pg_constraint c JOIN pg_class ch ON ch.oid=c.conrelid JOIN pg_namespace cn ON cn.oid=ch.relnamespace
   JOIN pg_class pa ON pa.oid=c.confrelid JOIN pg_namespace pn ON pn.oid=pa.relnamespace
   WHERE c.contype='f' AND (cn.nspname !~ '^pg_' OR pn.nspname !~ '^pg_')) x),
 'triggers',(SELECT coalesce(json_agg(x ORDER BY x.table_name,x.name),'[]'::json) FROM (
   SELECT n.nspname||'.'||c.relname table_name,t.tgname name,t.tgenabled enabled,
     pg_get_triggerdef(t.oid) definition,md5(pg_get_functiondef(t.tgfoid)) function_md5
   FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE NOT t.tgisinternal AND n.nspname !~ '^pg_') x))
"""

PROTECTED_TABLES = ('flyway_schema_history', 'system_configs', 'category_safety_rules', 'vendor_commission_policies')
# These source-defined relationships predate explicit FK constraints.
PROTECTED_LEGACY_REFERENCES = (
    ('system_configs', 'updated_by', 'users'),
    ('categories', 'parent_id', 'categories'),
    ('vendors', 'user_id', 'users'),
    ('vendors', 'verified_by', 'users'),
)


def replacement_inventory(session):
    reviewed = json.loads((ROOT / 'reset_inventory.json').read_text(encoding='utf8'))
    actual = session.query(RESET_INVENTORY_SQL)
    require(actual == reviewed['schema'], 'Unreviewed schema/table/column/foreign-key/trigger inventory; replacement blocked')
    tables = reviewed['classification']
    require(set(tables) == {t['name'].removeprefix('public.') for t in actual['tables']} and
            all(v == ('preserve' if t in PROTECTED_TABLES else 'replace') for t, v in tables.items()),
            'Incomplete reviewed replacement inventory')
    # All table names come from this checked package, never from an operator argument.
    require(all(re.fullmatch('[a-z_]+', t) for t in tables), 'Unsafe reviewed table inventory')
    session.send('LOCK TABLE ' + ','.join('public.' + t for t in sorted(tables)) + ' IN ACCESS EXCLUSIVE MODE')
    # Recheck after locks; concurrent schema changes cannot silently broaden scope.
    require(session.query(RESET_INVENTORY_SQL) == actual, 'Schema inventory changed while acquiring locks')
    return reviewed


def protected_snapshot(session, tables):
    result = {}
    for table in tables:
        condition = '' if table in PROTECTED_TABLES else f" WHERE EXISTS(SELECT 1 FROM pg_temp.demo_reset_keep k WHERE k.table_name={lit(table)} AND k.id=t.id)"
        rows = session.query(f"SELECT coalesce(json_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::json) FROM public.{table} t" + condition)
        result[table] = {'count': len(rows), 'sha256': hashlib.sha256(json.dumps(rows, sort_keys=True, separators=(',', ':')).encode()).hexdigest()}
    return result


def execute_replace(session, base_rows, trans_rows, demo_password):
    reviewed = replacement_inventory(session)
    tables = list(reviewed['classification'])
    identity = session.query("SELECT json_build_object('database',current_database(),'schema','public','flyway',"
                             "(SELECT coalesce(max(version::int),0)::text FROM public.flyway_schema_history WHERE success),"
                             "'failed',(SELECT count(*) FROM public.flyway_schema_history WHERE NOT success))")
    validate_identity(identity, identity['database'])
    session.send('CREATE TEMP TABLE demo_reset_keep(table_name text NOT NULL,id uuid NOT NULL,PRIMARY KEY(table_name,id)) ON COMMIT DROP')
    for table in PROTECTED_TABLES:
        if table != 'flyway_schema_history':
            session.send(f"INSERT INTO pg_temp.demo_reset_keep SELECT {lit(table)},id FROM public.{table}")
    session.send("INSERT INTO pg_temp.demo_reset_keep SELECT 'categories',c.id FROM public.categories c JOIN public.category_safety_rules r ON r.category_slug=c.slug ON CONFLICT DO NOTHING")
    refs = []
    for fk in reviewed['schema']['foreignKeys']:
        require(len(fk['childcolumns']) == 1 and fk['parentcolumns'] == ['id'], 'Unsupported reviewed foreign key')
        refs.append((fk['child'].removeprefix('public.'), fk['childcolumns'][0], fk['parent'].removeprefix('public.')))
    refs.extend(PROTECTED_LEGACY_REFERENCES)
    # Follow only parents of protected rows. Unrelated business children remain replaceable.
    while True:
        added = 0
        for child, column, parent in refs:
            missing = session.query(f"SELECT to_json(count(*)) FROM public.{child} c JOIN pg_temp.demo_reset_keep k ON k.table_name={lit(child)} AND k.id=c.id "
                                    f'WHERE c."{column}" IS NOT NULL AND NOT EXISTS(SELECT 1 FROM public.{parent} p WHERE p.id=c."{column}")') if child != 'flash_sale_hold_requests' and child != 'ai_confirmation_outcomes' else 0
            require(missing == 0, 'Protected configuration contains a dangling reference in ' + child)
            # Tables with composite primary keys cannot become protected in this reviewed graph.
            if child in ('flash_sale_hold_requests', 'ai_confirmation_outcomes'):
                continue
            added += session.query(f"WITH added AS (INSERT INTO pg_temp.demo_reset_keep SELECT DISTINCT {lit(parent)},p.id FROM public.{child} c "
                                   f"JOIN pg_temp.demo_reset_keep k ON k.table_name={lit(child)} AND k.id=c.id JOIN public.{parent} p ON p.id=c.\"{column}\" "
                                   'ON CONFLICT DO NOTHING RETURNING id) SELECT to_json(count(*)) FROM added')
        if not added:
            break
    protected_tables = session.query("SELECT coalesce(json_agg(DISTINCT table_name),'[]'::json) FROM pg_temp.demo_reset_keep")
    snapshot_tables = sorted(set(PROTECTED_TABLES) | set(protected_tables))
    before = protected_snapshot(session, snapshot_tables)
    # Preflight permits replaceable old data to differ, but every protected fixture must match.
    audit = preflight_audit(session, base_rows, trans_rows)
    keep = session.query("SELECT coalesce(json_agg(json_build_object('table',table_name,'id',id)),'[]'::json) FROM pg_temp.demo_reset_keep")
    protected_ids = {(r['table'], r['id']) for r in keep}
    require(not any((c['table'], c['id']) in protected_ids for c in audit['fatalConflicts']),
            'Protected fixture conflict; configuration will not be overwritten')
    require(not any(t == 'users' and i in {u['id'] for u in base_rows['users']} for t, i in protected_ids),
            'Protected fixture account conflict; credentials will not be overwritten')
    for table, column in (('users', 'email'), ('categories', 'slug'), ('vendors', 'user_id')):
        fixtures = dict(base_rows, **trans_rows)[table]
        collision = session.query(f"SELECT to_json(count(*)) FROM public.{table} t JOIN pg_temp.demo_reset_keep k ON k.table_name={lit(table)} AND k.id=t.id "
                                  f"JOIN {source(table, fixtures)} e ON t.\"{column}\"=e.\"{column}\" WHERE t.id<>e.id")
        require(collision == 0, 'Protected fixture natural-key conflict in ' + table)
    # This reviewed business trigger forbids deletion of enabled/cancelled promotions.
    # Disable only this named trigger transactionally, with its full definition already checked.
    session.send('ALTER TABLE public.flash_sales DISABLE TRIGGER flash_sale_terms_immutable')
    remaining = set(tables) - set(PROTECTED_TABLES)
    dependencies = {(fk['child'].removeprefix('public.'), fk['parent'].removeprefix('public.')) for fk in reviewed['schema']['foreignKeys']}
    deleted = {}
    while remaining:
        ready = sorted(t for t in remaining if not any(child in remaining and child != t and parent == t for child, parent in dependencies))
        require(bool(ready), 'Unreviewed cyclic replacement dependencies')
        for table in ready:
            condition = f" WHERE NOT EXISTS(SELECT 1 FROM pg_temp.demo_reset_keep k WHERE k.table_name={lit(table)} AND k.id=t.id)" if table in protected_tables else ''
            deleted[table] = session.query(f'WITH removed AS (DELETE FROM public.{table} t' + condition + ' RETURNING 1) SELECT to_json(count(*)) FROM removed')
            remaining.remove(table)
    session.send('ALTER TABLE public.flash_sales ENABLE TRIGGER flash_sale_terms_immutable')
    clean_audit = preflight_audit(session, base_rows, trans_rows)
    result = execute_sync(session, base_rows, trans_rows, clean_audit, demo_password)
    # Re-read all fixtures, relationships, amounts and statuses before the caller can commit.
    validated = preflight_audit(session, base_rows, trans_rows)
    require(validated['status'] == 'PREFLIGHT_PASS' and all(v['presentInDb'] == v['expected'] and v['conflicts'] == 0 for v in validated['tables'].values()),
            'Replacement canonical fixture verification failed')
    require(result['totalVerifiedInDb'] == 270, 'Replacement requires 270 canonical rows')
    historical = {i['slot_id'] for i in trans_rows['booking_items']}
    today = datetime.now(timezone(timedelta(hours=7))).date().isoformat()
    require(all(s['date'] > today for s in base_rows['service_slots'] if s['id'] not in historical), 'Replacement requires future demo slots')
    after = protected_snapshot(session, snapshot_tables)
    require(before == after, 'Protected configuration fingerprint changed; replacement rolled back')
    session.send('SET CONSTRAINTS ALL IMMEDIATE')
    result.update(status='REPLACE_SUCCESS', deletedByTable=deleted,
                  preservedByTable={t: v['count'] for t, v in before.items()},
                  protectedFingerprint=hashlib.sha256(json.dumps(before, sort_keys=True).encode()).hexdigest())
    result['_protectedIds'] = protected_ids
    return result


def fsync_directory(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def write_pending_receipt(path, receipt):
    require(not path.is_symlink(), 'Pending receipt must not be a symlink')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf8') as stream:
        json.dump(receipt, stream, indent=2)
        stream.flush()
        os.fsync(stream.fileno())
    fsync_directory(path.parent)


def validate_modes(args):
    require(not args.replace or ((args.dry_run or args.apply) and not args.cleanup and not args.validate_only),
            '--replace requires --dry-run or --apply and cannot be combined with cleanup/preflight')
    require(not args.cleanup or args.dry_run or args.apply, 'Cleanup requires --dry-run or --apply')


def row_fingerprints(session, table, ids):
    if not ids:
        return {}
    id_list = ','.join(lit(i) + '::uuid' for i in ids)
    return session.query(f"SELECT coalesce(json_object_agg(id,md5(to_jsonb(t)::text)), '{{}}'::json) FROM public.{table} t WHERE id IN ({id_list})")


def ownership_target(args):
    return {'database': args.database, 'endpoint': args.docker_container or f'{args.host}:{args.port}'}


def load_ownership(args, base, trans):
    path = args.ownership_file
    if path is None:
        require(not args.cleanup and not args.apply, '--ownership-file outside the package is required for apply/cleanup')
        return {}
    require(not path.resolve().is_relative_to(ROOT), 'Keep ownership receipt outside the handoff package')
    if not path.exists():
        require(not args.cleanup, 'Missing import ownership receipt; cleanup is forbidden')
        return {}
    data = json.loads(path.read_text(encoding='utf8'))
    require(data.get('target') == ownership_target(args) and data.get('version') == 1, 'Ownership receipt target/version mismatch')
    rows = dict(base, **trans)
    require(set(data.get('owned', {})) == set(ALL_TABLES), 'Incomplete ownership receipt')
    for table, records in data['owned'].items():
        allowed = {r['id'] for r in rows[table]} - (FLYWAY_BASELINE_CATEGORIES if table == 'categories' else set())
        require(set(records).issubset(allowed) and all(re.fullmatch('[0-9a-f]{32}', v) for v in records.values()), 'Invalid ownership receipt IDs/fingerprints')
    allowed_dates = {s['id'] for s in base['service_slots']} - {i['slot_id'] for i in trans['booking_items']}
    require(set(data.get('slotDates', {})).issubset(allowed_dates), 'Invalid ownership slot dates')
    return data


def execute_cleanup(session, base_rows, trans_rows, ownership=None):
    require(ownership and ownership.get('owned'), 'Cleanup requires the original import ownership receipt')

    session.send('LOCK TABLE ' + ','.join('public.' + t for t in ALL_TABLES) + ' IN SHARE ROW EXCLUSIVE MODE')

    for table in ALL_TABLES:
        current = row_fingerprints(session, table, ownership['owned'][table])
        require(all(ownership['owned'][table][i] == digest for i, digest in current.items()),
                'Cleanup blocked: imported row changed in ' + table)
    # Explicitly inspect cascading references, including tables outside this package.
    fks = session.query("SELECT coalesce(json_agg(x),'[]'::json) FROM (SELECT c.conrelid::regclass::text child, "
                        "c.confrelid::regclass::text parent, a.attname col, cardinality(c.conkey) n "
                        "FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1] "
                        "WHERE c.contype='f' AND c.confrelid IN (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace)) x")
    # V1 leaves several application relationships without FK constraints (including refresh_tokens).
    columns = session.query("SELECT json_agg(json_build_object('child',c.table_name,'col',c.column_name)) "
                            "FROM information_schema.columns c JOIN information_schema.tables t "
                            "ON t.table_schema=c.table_schema AND t.table_name=c.table_name "
                            "WHERE c.table_schema='public' AND t.table_type='BASE TABLE' AND c.data_type='uuid'")
    known = {(f['child'].removeprefix('public.'), f['col']) for f in fks}
    for col in columns:
        # Package UUIDs are globally unique. Also cover legacy and polymorphic UUID references.
        if col['col'] != 'id' and (col['child'], col['col']) not in known:
            fks.append(dict(col, parent='__owned__', n=1))
    for fk in fks:
        parent = fk['parent'].removeprefix('public.')
        child = fk['child'].removeprefix('public.')
        ids = {i for records in ownership['owned'].values() for i in records} if parent == '__owned__' else ownership['owned'].get(parent, {})
        if not ids:
            continue
        require(fk['n'] == 1 and all(re.fullmatch('[a-z_]+', x) for x in (parent, child, fk['col'])), 'Unsupported cleanup FK; manual review required')
        parent_ids = ','.join(lit(i) + '::uuid' for i in ids)
        owned_children = ownership['owned'].get(child, {})
        exclude = ' AND id NOT IN (' + ','.join(lit(i) + '::uuid' for i in owned_children) + ')' if owned_children else ''
        external = session.query(f'SELECT to_json(count(*)) FROM public.{child} WHERE "{fk["col"]}" IN ({parent_ids}){exclude}')
        require(external == 0, 'Cleanup blocked: non-owned references in ' + child)

    deleted_counts = {}
    for table in REVERSE_CLEANUP_ORDER:
        target_ids = [i for i in ownership['owned'][table]
                      if table != 'categories' or i not in FLYWAY_BASELINE_CATEGORIES]

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


def verify_backup(backup_path, docker_container=None):
    if backup_path is None:
        return
    require(backup_path.is_file(), f"Backup file not found: {backup_path}")
    with backup_path.open('rb') as stream:
        raw = stream.read(16)
    require(raw.startswith(b'PGDMP'), "Backup file is not a valid PostgreSQL custom archive (magic header PGDMP mismatch)")
    command = ['docker', 'exec', '-i', docker_container, 'pg_restore', '--list'] if docker_container else ['pg_restore', '--list']
    try:
        with backup_path.open('rb') as stream:
            result = subprocess.run(command, stdin=stream, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        require(result.returncode == 0 and b'TOC Entries:' in result.stdout, 'Cannot verify backup archive with pg_restore')
    except OSError:
        raise ValueError('pg_restore is required to verify backup archive') from None


def build_parser():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--validate-only', '--preflight', dest='validate_only', action='store_true', default=False,
                      help='Read-only preflight audit of schema, Flyway V43, and existing records (default)')
    mode.add_argument('--dry-run', action='store_true',
                      help='Simulate full import & URL reconciliation in a transaction, then automatically ROLLBACK')
    mode.add_argument('--apply', action='store_true',
                      help='Commit demo data and URL reconciliation to database (requires --confirm-backup or --backup-file)')

    parser.add_argument('--replace', action='store_true', help='Atomically replace reviewed application data, preserving configuration; requires --dry-run or --apply')

    parser.add_argument('--cleanup', action='store_true',
                        help='Delete demo records by UUID in reverse FK order (use with --dry-run or --apply)')

    parser.add_argument('--host', default='127.0.0.1', help='PostgreSQL host (default: 127.0.0.1)')
    parser.add_argument('--port', type=int, default=5432, help='PostgreSQL port (default: 5432)')
    parser.add_argument('--database', default='danasea', help='Target database name (default: danasea)')
    parser.add_argument('--user', default='postgres', help='PostgreSQL user (default: postgres)')
    parser.add_argument('--docker-container', help='Connect via docker exec to specified PostgreSQL container')

    parser.add_argument('--confirm-backup', action='store_true',
                        help='Explicit acknowledgment that a verified database backup exists before applying changes')
    parser.add_argument('--backup-file', type=Path,
                        help='Path to a pg_dump custom archive file for verification before applying changes')

    parser.add_argument('--demo-password-stdin', action='store_true', help='Read demo user password from stdin')
    parser.add_argument('--db-password-stdin', action='store_true', help='Read database password from stdin')
    parser.add_argument('--ownership-file', type=Path, help='Private import ownership receipt outside this package; required for apply/cleanup; reuse on re-import')

    return parser


def main():
    parser = build_parser()
    args = parser.parse_args()

    validate_modes(args)
    if not args.dry_run and not args.apply and not args.cleanup:
        args.validate_only = True

    require(not args.cleanup or args.dry_run or args.apply, 'Cleanup requires --dry-run or --apply')
    manifest()
    verify_backup(args.backup_file, args.docker_container)

    base_rows, trans_rows, manifest_data = load_fixtures()
    ownership = load_ownership(args, base_rows, trans_rows)
    prepare_slots(base_rows, trans_rows, {} if args.replace else ownership)

    if getattr(args, 'docker_container', None) is None:
        if os.environ.get('PGPASSWORD'):
            args.db_password = os.environ.get('PGPASSWORD')
        elif args.db_password_stdin:
            args.db_password = sys.stdin.readline().rstrip('\r\n')
        elif sys.stdin.isatty():
            args.db_password = getpass.getpass(f"PostgreSQL password for {args.user}@{args.host}:{args.port}/{args.database} (hidden): ")
        else:
            args.db_password = None

    if args.validate_only:
        session = Session(args, readonly=True)
        try:
            report = preflight_audit(session, base_rows, trans_rows)
            validate_identity(report['identity'], args.database)
            session.finish(commit=False)
        finally:
            session.close()
        print(json.dumps(report, indent=2))
        require(report['status'] == 'PREFLIGHT_PASS', 'Preflight blocked; inspect conflicts/schema in report')
        return

    if args.cleanup:
        if args.apply:
            require(args.confirm_backup or args.backup_file,
                    'Production safety: --confirm-backup or --backup-file is required before cleanup --apply')
        session = Session(args, readonly=False)
        try:
            audit = preflight_audit(session, base_rows, trans_rows)
            validate_identity(audit['identity'], args.database)
            result = execute_cleanup(session, base_rows, trans_rows, ownership)
            session.finish(commit=args.apply)
        finally:
            session.close()
        result['transaction'] = 'COMMITTED' if args.apply else 'ROLLED_BACK'
        print(json.dumps(result, indent=2))
        return

    # Dry-run or Apply sync workflow
    if args.apply:
        require(args.confirm_backup or args.backup_file,
                'Production safety: --confirm-backup or --backup-file is required before --apply')

    demo_password = resolve_demo_password(args)

    # Validate receipt writability before committing; a lost receipt must never enable cleanup.
    if args.apply:
        require(args.ownership_file.parent.is_dir(), 'Ownership receipt parent directory must exist')
        receipt_temp = args.ownership_file.with_suffix(args.ownership_file.suffix + '.pending')
    if args.apply:
        require(not receipt_temp.exists(), 'Existing pending receipt; inspect transaction/recovery state before retry')
    commit_attempted = False
    pending_created = False

    session = Session(args, readonly=False)
    try:
        audit = preflight_audit(session, base_rows, trans_rows)
        validate_identity(audit['identity'], args.database)
        require(audit['flywayCompliant'],
                f"Target database Flyway version is {audit['identity']['flyway']} (< 43). Apply migrations V25-V43 first.")
        require(args.replace or audit['conflictsCount'] == 0,
                f"Found {audit['conflictsCount']} data conflicts. Refusing to overwrite.")
        if args.apply:
            write_pending_receipt(receipt_temp, {'version': 1, 'target': ownership_target(args), 'state': 'PREPARING',
                                                  'operationId': uuid.uuid4().hex, 'replacement': args.replace})
            pending_created = True

        result = execute_replace(session, base_rows, trans_rows, demo_password) if args.replace else execute_sync(session, base_rows, trans_rows, audit, demo_password)
        protected_ids = result.pop('_protectedIds', set())
        owned = {t: {} if args.replace else dict(ownership.get('owned', {}).get(t, {})) for t in ALL_TABLES}
        for t, ids in result.pop('insertedIds').items():
            if t == 'categories':
                ids = [i for i in ids if i not in FLYWAY_BASELINE_CATEGORIES]
            ids = [i for i in ids if (t, i) not in protected_ids]
            owned[t].update(row_fingerprints(session, t, ids))
        receipt = {'version': 1, 'target': ownership_target(args), 'owned': owned,
                   'slotDates': {s['id']: s['date'] for s in base_rows['service_slots']
                                 if s['id'] not in {i['slot_id'] for i in trans_rows['booking_items']}}}
        if args.apply:
            receipt['state'] = 'COMMIT_PENDING'
            receipt['replacement'] = args.replace
            receipt['operationId'] = json.loads(receipt_temp.read_text(encoding='utf8'))['operationId']
            receipt_writing = receipt_temp.with_suffix(receipt_temp.suffix + '.writing')
            write_pending_receipt(receipt_writing, receipt)
            os.replace(receipt_writing, receipt_temp)
            fsync_directory(receipt_temp.parent)
        commit_attempted = args.apply
        session.finish(commit=args.apply)
    finally:
        session.close()
        if pending_created and not commit_attempted:
            receipt_temp.unlink()
            fsync_directory(receipt_temp.parent)

    if args.apply:
        os.replace(receipt_temp, args.ownership_file)
        fsync_directory(args.ownership_file.parent)

    result['transaction'] = 'COMMITTED' if args.apply else 'ROLLED_BACK'
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print('BLOCKED: ' + (str(exc) if isinstance(exc, ValueError)
                             else type(exc).__name__ + '; execution error'), file=sys.stderr)
        sys.exit(1)
