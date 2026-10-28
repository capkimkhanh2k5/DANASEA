"""DANASEA DEVELOPMENT transaction fixture installer. Default checks only; explicit apply required.

Installs mock transactions (Bookings, Orders, Payments, Refunds, Invoices, Settlements, Payouts)
conforming to backend JPA entities and Flyway V1-V43 schemas.
Requires base demo data (Users, Vendors, Services, Slots, Options) to be installed first.
Zero external payment gateway, email, webhook or financial API interaction.
"""
from pathlib import Path
from decimal import Decimal
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

BASE_TABLES = ['users', 'vendors', 'services', 'service_options', 'service_slots']

UUID_NORMALIZATION_MAP = {
    'p001': '0003',
    'r001': '0004',
    'i001': '0005',
    's001': '0006',
    'sli0': '0007',
    'pr01': '0008'
}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def validate_identity(identity, database):
    require(identity['database'] == database and identity['schema'] == 'public' and
            identity['failed'] == 0 and identity['flyway'] in ('24', '37', '41', '42', '43'),
            'Database/schema/Flyway mismatch; no migration fallback')


def load_base(name):
    return json.loads((BASE_DIR / name).read_text(encoding='utf8'))


def load_trans():
    return json.loads((TRANS_DIR / 'demo_transactions.json').read_text(encoding='utf8'))


def lit(value):
    require(isinstance(value, str) and '\0' not in value, 'Unsafe SQL literal')
    return "'" + value.replace("'", "''") + "'"


def jlit(value):
    return lit(json.dumps(value, ensure_ascii=False, separators=(',', ':'))) + '::jsonb'


def source(table, rows):
    require(table in TRANSACTION_TABLES, 'Unsupported transaction table')
    return 'jsonb_populate_recordset(NULL::public.' + table + ',' + jlit(rows) + ')'


class Session:
    def __init__(self, args, readonly=False):
        env = dict(os.environ)
        if args.docker_container:
            command = ['docker', 'exec', '-i', args.docker_container, 'psql', '-X', '-w', '-q', '-A', '-t',
                       '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate', '-U', args.user, '-d', args.database]
        else:
            command = ['psql', '-X', '-w', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate',
                       '-h', args.host, '-p', str(args.port), '-U', args.user, '-d', args.database]
            env['PGPASSWORD'] = getattr(args, 'db_password', '')
        self.p = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
        self.closed = False
        self.send('BEGIN ISOLATION LEVEL SERIALIZABLE' + (' READ ONLY' if readonly else ''))
        self.send("SET LOCAL statement_timeout='45s'; SET LOCAL lock_timeout='5s'; "
                  "SET LOCAL idle_in_transaction_session_timeout='120s'; SET LOCAL client_encoding='UTF8'; "
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
                raise ValueError('PostgreSQL failed, SQLSTATE=' + (code[-1] if code else 'UNAVAILABLE') + '; SQL/details withheld')
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
                self.finish()
            except Exception:
                self.p.terminate()
                self.p.wait(timeout=10)
            self.closed = True


def normalize_uuid_str(val):
    if not isinstance(val, str):
        return val
    for bad_prefix, good_prefix in UUID_NORMALIZATION_MAP.items():
        if val.startswith(bad_prefix):
            return good_prefix + val[len(bad_prefix):]
    return val


def check_uuid_syntax(val):
    try:
        uuid.UUID(str(val))
        return True
    except (ValueError, AttributeError):
        return False


def validate_transaction_payload(data, base_records):
    meta = data.get('metadata', {})
    require(meta.get('targetFlyway') in ('24', '37', '41', '42', '43'), 'Unsupported targetFlyway')
    require(meta.get('platformCommissionRate') == '0.1000', 'Commission policy rate mismatch')

    expected_counts = dict(bookings=6, booking_items=6, master_orders=6, sub_orders=6,
                           payments=6, refunds=2, invoices=3, settlements=2,
                           settlement_line_items=3, payout_requests=2)
    require(all(t in data and len(data[t]) == n for t, n in expected_counts.items()),
            'Unexpected transaction dataset counts')

    users = {r['id']: r for r in base_records['users']}
    vendors = {r['id']: r for r in base_records['vendors']}
    services = {r['id']: r for r in base_records['services']}
    slots = {r['id']: r for r in base_records['service_slots']}
    options = {r['id']: r for r in base_records['service_options']}

    for b in data['bookings']:
        require(b['customer_id'] in users and users[b['customer_id']]['role'] == 'CUSTOMER',
                'Booking customer must exist in users with CUSTOMER role')

    for bi in data['booking_items']:
        require(bi['service_id'] in services, 'Booking item service absent')
        require(bi['vendor_id'] in vendors, 'Booking item vendor absent')
        require(bi['slot_id'] in slots, 'Booking item slot absent')
        if bi.get('option_id'):
            require(bi['option_id'] in options and options[bi['option_id']]['service_id'] == bi['service_id'],
                    'Booking item option mismatch')

    for so in data['sub_orders']:
        require(so['vendor_id'] in vendors, 'Sub-order vendor absent')
        require(so['service_id'] in services, 'Sub-order service absent')
        require(services[so['service_id']]['vendor_id'] == so['vendor_id'],
                'Sub-order vendor/service relationship mismatch')

    for pr in data['payout_requests']:
        if pr.get('processed_by'):
            require(pr['processed_by'] in users and users[pr['processed_by']]['role'] == 'ADMIN',
                    'Payout processor must exist with ADMIN role')

    for so in data['sub_orders']:
        unit_price = Decimal(str(so['unit_price']))
        qty = Decimal(str(so['quantity']))
        subtotal = Decimal(str(so['subtotal_amount']))
        require(unit_price * qty == subtotal, 'Sub-order subtotal calculation mismatch')

        discount = Decimal(str(so['discount_amount']))
        final = Decimal(str(so['final_amount']))
        require(subtotal - discount == final, 'Sub-order final amount mismatch')

        rate = Decimal(str(so['commission_rate']))
        comm = Decimal(str(so['commission_amount']))
        expected_comm = (final * rate).quantize(Decimal('0.01'))
        require(expected_comm == comm, 'Sub-order commission calculation mismatch')

        payout = Decimal(str(so['vendor_payout_amount']))
        require(final - comm == payout, 'Sub-order vendor payout calculation mismatch')

    line_items_by_settlement = {}
    for sli in data['settlement_line_items']:
        gross = Decimal(str(sli['gross_amount']))
        ref = Decimal(str(sli['refund_amount']))
        comm = Decimal(str(sli['commission_amount']))
        net = Decimal(str(sli['net_amount']))
        require(gross - ref - comm == net, 'Settlement line item net mismatch')
        line_items_by_settlement.setdefault(sli['settlement_id'], []).append(sli)

    for s in data['settlements']:
        items = line_items_by_settlement.get(s['id'], [])
        require(items, 'Settlement without line items')
        expected_gross = sum(Decimal(str(i['gross_amount'])) for i in items)
        expected_comm = sum(Decimal(str(i['commission_amount'])) for i in items)
        expected_ref = sum(Decimal(str(i['refund_amount'])) for i in items)
        expected_net = expected_gross - expected_comm - expected_ref
        require(expected_gross == Decimal(str(s['gross_amount'])), 'Settlement gross mismatch')
        require(expected_comm == Decimal(str(s['commission_amount'])), 'Settlement commission mismatch')
        require(expected_net == Decimal(str(s['net_payable_amount'])), 'Settlement net mismatch')


def get_syntax_audit(data):
    uuid_columns = {
        'bookings': ['id', 'customer_id'],
        'booking_items': ['id', 'booking_id', 'service_id', 'vendor_id', 'slot_id', 'option_id'],
        'master_orders': ['id', 'booking_id', 'customer_id', 'discount_code_id'],
        'sub_orders': ['id', 'master_order_id', 'booking_item_id', 'vendor_id', 'service_id', 'slot_id',
                       'qr_secret', 'waiver_accepted_by', 'original_slot_id'],
        'payments': ['id', 'master_order_id'],
        'refunds': ['id', 'sub_order_id', 'requested_by'],
        'invoices': ['id', 'master_order_id'],
        'settlements': ['id', 'vendor_id'],
        'settlement_line_items': ['id', 'settlement_id', 'sub_order_id'],
        'payout_requests': ['id', 'vendor_id', 'settlement_id', 'processed_by'],
    }
    invalid = []
    for t in TRANSACTION_TABLES:
        for r in data[t]:
            for col in uuid_columns.get(t, []):
                val = r.get(col)
                if val is not None and not check_uuid_syntax(val):
                    invalid.append(dict(table=t, recordId=r['id'], column=col, value=val))
    return invalid


def apply_uuid_normalization(data):
    data = copy.deepcopy(data)
    uuid_columns = {
        'bookings': ['id', 'customer_id'],
        'booking_items': ['id', 'booking_id', 'service_id', 'vendor_id', 'slot_id', 'option_id'],
        'master_orders': ['id', 'booking_id', 'customer_id', 'discount_code_id'],
        'sub_orders': ['id', 'master_order_id', 'booking_item_id', 'vendor_id', 'service_id', 'slot_id',
                       'qr_secret', 'waiver_accepted_by', 'original_slot_id'],
        'payments': ['id', 'master_order_id'],
        'refunds': ['id', 'sub_order_id', 'requested_by'],
        'invoices': ['id', 'master_order_id'],
        'settlements': ['id', 'vendor_id'],
        'settlement_line_items': ['id', 'settlement_id', 'sub_order_id'],
        'payout_requests': ['id', 'vendor_id', 'settlement_id', 'processed_by'],
    }
    for t in TRANSACTION_TABLES:
        for r in data[t]:
            for col in uuid_columns.get(t, []):
                if r.get(col) is not None:
                    r[col] = normalize_uuid_str(r[col])
    return data


def align_defaults(rows, colmap):
    if ('refunds', 'retry_count') in colmap:
        for r in rows.get('refunds', []):
            r.setdefault('retry_count', 0)
    if ('refunds', 'verification_attempts') in colmap:
        for r in rows.get('refunds', []):
            r.setdefault('verification_attempts', 0)
    if ('settlements', 'version') in colmap:
        for s in rows.get('settlements', []):
            s.setdefault('version', 0)


def target_guard(args):
    require(args.development, 'Explicit --development is required for any database operation')
    require(args.database and args.database.startswith('danasea_demo_') and
            args.docker_container != 'pbl6_httt-postgres-1',
            'Only isolated DEVELOPMENT targets allowed; main/shared source forbidden')
    if args.docker_container:
        d = json.loads(subprocess.run(['docker', 'inspect', args.docker_container],
                                      capture_output=True, check=True).stdout)[0]
        require(all(m.get('Name') != 'pbl6_httt_postgres_data' for m in d['Mounts']),
                'Main PostgreSQL volume is forbidden')
    if not args.docker_container:
        require(args.host in ('127.0.0.1', 'localhost', '::1'),
                'Remote/shared database is not supported')


def verify_backup(args):
    if args.backup is None:
        return
    raw = args.backup.read_bytes()
    require(raw.startswith(b'PGDMP'), 'Backup is not a PostgreSQL custom archive')
    command = (['docker', 'exec', '-i', args.docker_container, 'pg_restore', '--list']
               if args.docker_container else ['pg_restore', '--list'])
    p = subprocess.run(command, input=raw, capture_output=True, timeout=45)
    require(p.returncode == 0 and not p.stderr and b'TABLE DATA' in p.stdout,
            'Backup archive validation failed')


def apply_transactions(args, rows):
    session = Session(args)
    results = {}
    try:
        all_tables = BASE_TABLES + TRANSACTION_TABLES
        session.send('LOCK TABLE ' + ','.join('public.' + t for t in all_tables) + ' IN SHARE ROW EXCLUSIVE MODE')

        identity = session.query(
            "SELECT json_build_object('database',current_database(),'schema',"
            "(SELECT table_schema FROM information_schema.tables WHERE table_schema='public' AND table_name='users'),"
            "'flyway',(SELECT version FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1),"
            "'failed',(SELECT count(*) FROM public.flyway_schema_history WHERE NOT success))")
        validate_identity(identity, args.database)

        base_checks = [
            ('bookings', 'customer_id', 'users'),
            ('booking_items', 'service_id', 'services'),
            ('booking_items', 'vendor_id', 'vendors'),
            ('booking_items', 'slot_id', 'service_slots'),
            ('sub_orders', 'vendor_id', 'vendors'),
            ('sub_orders', 'service_id', 'services'),
            ('sub_orders', 'slot_id', 'service_slots'),
            ('settlements', 'vendor_id', 'vendors'),
        ]
        for t, col, parent in base_checks:
            needed_ids = {r[col] for r in rows[t] if r.get(col) is not None}
            if needed_ids:
                sql_ids = ','.join(lit(i) + '::uuid' for i in needed_ids)
                count = session.query('SELECT to_json(count(*)) FROM public.' + parent + ' WHERE id IN (' + sql_ids + ')')
                require(count == len(needed_ids),
                        f'Missing prerequisite base records in {parent}; run install_demo_data.py first')

        columns = session.query("SELECT json_agg(x) FROM (SELECT table_name,column_name,is_nullable,"
                                "column_default,is_generated,character_maximum_length,data_type "
                                "FROM information_schema.columns WHERE table_schema='public') x")
        colmap = {(c['table_name'], c['column_name']): c for c in columns}
        align_defaults(rows, colmap)

        for table, rs in rows.items():
            for row in rs:
                for name, value in row.items():
                    c = colmap.get((table, name))
                    require(c is not None and c['is_generated'] == 'NEVER',
                            'Unsupported column ' + table + '.' + name)
                    if name not in ('created_at', 'updated_at'):
                        require(value is not None or c['is_nullable'] == 'YES',
                                'NULL violates ' + table + '.' + name)
                    limit = c['character_maximum_length']
                    require(not isinstance(value, str) or limit is None or len(value) <= limit,
                            'Value length violates ' + table + '.' + name)

        for table, field in [('master_orders', 'idempotency_key'), ('payments', 'idempotency_key'),
                             ('refunds', 'idempotency_key'), ('invoices', 'invoice_number')]:
            values = [r for r in rows[table] if r.get(field) is not None]
            if values:
                exp = source(table, values)
                n = session.query('SELECT to_json(count(*)) FROM public.' + table + ' t JOIN ' + exp +
                                  ' e ON t.' + field + '=e.' + field + ' WHERE t.id<>e.id')
                require(n == 0, 'Natural-key collision ' + table + '.' + field)

        for table in TRANSACTION_TABLES:
            rs = rows[table]
            exp = source(table, rs)
            remove = "-'created_at'-'updated_at'"

            mismatch = session.query(
                'SELECT coalesce(json_agg(json_build_object(\'id\',t.id,\'differentFields\','
                '(SELECT json_agg(k) FROM jsonb_object_keys(to_jsonb(e)' + remove + ') k WHERE '
                '(to_jsonb(t)->k) IS DISTINCT FROM (to_jsonb(e)->k)))),\'[]\'::json) FROM public.' + table +
                ' t JOIN ' + exp + ' e ON t.id=e.id WHERE (to_jsonb(t)' + remove + ')<>(to_jsonb(e)' + remove + ')')
            require(not mismatch, 'Existing transaction differs; no overwrite: ' + table + ' ' + json.dumps(mismatch))

            names = list(rs[0])
            targets = ','.join('"' + k + '"' for k in names)
            expressions = ','.join('coalesce(r."' + k + '",CURRENT_TIMESTAMP)' if k in ('created_at', 'updated_at')
                                   else 'r."' + k + '"' for k in names)
            inserted = session.query(
                'WITH added AS (INSERT INTO public.' + table + ' (' + targets + ') SELECT ' + expressions +
                ' FROM ' + exp + ' r WHERE NOT EXISTS(SELECT 1 FROM public.' + table + ' t WHERE t.id=r.id) RETURNING id) '
                'SELECT coalesce(json_agg(id),\'[]\'::json) FROM added')
            equal = session.query(
                'SELECT to_json(count(*)) FROM public.' + table + ' t JOIN ' + exp +
                ' e ON t.id=e.id WHERE (to_jsonb(t)' + remove + ')=(to_jsonb(e)' + remove + ')')
            require(equal == len(rs), 'Post-insert comparison failed: ' + table)
            results[table] = dict(expected=len(rs), inserted=len(inserted),
                                  existing=len(rs) - len(inserted), equal=equal)

        session.finish(commit=args.apply)
    finally:
        session.close()

    if args.apply:
        verify = Session(args, readonly=True)
        try:
            for table, rs in rows.items():
                remove = "-'created_at'-'updated_at'"
                n = verify.query('SELECT to_json(count(*)) FROM public.' + table + ' t JOIN ' + source(table, rs) +
                                 ' e ON t.id=e.id WHERE (to_jsonb(t)' + remove + ')=(to_jsonb(e)' + remove + ')')
                require(n == len(rs), 'Independent post-COMMIT comparison failed: ' + table)
            verify.finish()
        finally:
            verify.close()

    return dict(status='COMMIT_SELECT_PASS' if args.apply else 'DRY_RUN_ROLLBACK_PASS',
                tables=results, identity=identity)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--validate-only', action='store_true', default=True, help='Offline validation only (default)')
    mode.add_argument('--dry-run', action='store_true', help='Validate and test against DB with ROLLBACK')
    mode.add_argument('--apply', action='store_true', help='Apply fixtures to DB with COMMIT')
    parser.add_argument('--development', action='store_true', help='Required for any DB operation')
    parser.add_argument('--database', help='Target database (must start with danasea_demo_)')
    parser.add_argument('--docker-container', help='Docker container name')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=5432)
    parser.add_argument('--user', default='postgres')
    parser.add_argument('--backup', type=Path)
    parser.add_argument('--password-stdin', action='store_true')
    parser.add_argument('--normalize-uuids', action='store_true',
                        help='Canonicalize non-hex mock UUID prefixes to valid RFC-4122 hex')
    args = parser.parse_args()

    if args.dry_run or args.apply:
        args.validate_only = False

    trans_data = load_trans()
    base_records = {t: [r['sqlRow'] for r in load_base(t + '.json')['records']]
                    for t in BASE_TABLES}

    validate_transaction_payload(trans_data, base_records)
    syntax_issues = get_syntax_audit(trans_data)

    rows = {t: trans_data[t] for t in TRANSACTION_TABLES}
    if args.normalize_uuids:
        rows = {t: apply_uuid_normalization(trans_data)[t] for t in TRANSACTION_TABLES}

    if args.validate_only:
        report = dict(
            status='OFFLINE_VALIDATION_PASS',
            counts={t: len(rows[t]) for t in TRANSACTION_TABLES},
            totalRecords=sum(len(rows[t]) for t in TRANSACTION_TABLES),
            database='NOT_CONNECTED',
            uuid_syntax=dict(
                status='VALID' if not syntax_issues else 'HAS_NON_HEX_PREFIXES',
                nonHexOccurrences=len(syntax_issues),
                requiresNormalization=len(syntax_issues) > 0 and not args.normalize_uuids,
                normalized=args.normalize_uuids or len(syntax_issues) == 0
            )
        )
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return

    require(args.normalize_uuids or not syntax_issues,
            f'Found {len(syntax_issues)} non-hex UUIDs. Pass --normalize-uuids to import with RFC-compliant hex prefixes.')

    target_guard(args)
    verify_backup(args)
    if not args.docker_container:
        args.db_password = getpass.getpass('PostgreSQL password (hidden): ')

    result = apply_transactions(args, rows)
    if args.apply:
        (ROOT / 'runtime').mkdir(exist_ok=True)
        (ROOT / 'runtime/install_transactions_result.json').write_text(
            json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print('BLOCKED: ' + (str(exc) if isinstance(exc, ValueError)
                             else type(exc).__name__ + '; private details withheld'), file=sys.stderr)
        sys.exit(1)
