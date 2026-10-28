"""DANASEA DEVELOPMENT fixture installer. Default checks only; explicit apply required.

No migration, UPDATE, DELETE, approval, fake OTP, banking or payment operation.
Uses native psql or an existing PostgreSQL Docker container. Secrets stay in memory.
"""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import copy
import getpass
import hashlib
import json
import mimetypes
import os
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error
import urllib.parse
import uuid

ROOT = Path(__file__).resolve().parents[1]
BASE_DIR = ROOT / 'base' if (ROOT / 'base').is_dir() else ROOT / 'data'
TABLES = ['categories', 'users', 'vendors', 'services', 'service_options',
          'service_slots', 'vendor_documents', 'service_images', 'service_safety_documents']


def require(ok, message):
    if not ok:
        raise ValueError(message)


def validate_identity(identity, database):
    require(identity['database'] == database and identity['schema'] == 'public' and
            identity['failed'] == 0 and identity['flyway'] in ('24', '37', '41', '42', '43'),
            'Database/schema/Flyway mismatch; no migration fallback')


def load(name):
    target = BASE_DIR / name
    if not target.is_file() and (ROOT / name).is_file():
        target = ROOT / name
    return json.loads(target.read_text(encoding='utf8'))


def lit(value):
    require(isinstance(value, str) and '\0' not in value, 'Unsafe SQL literal')
    return "'" + value.replace("'", "''") + "'"


def jlit(value):
    return lit(json.dumps(value, ensure_ascii=False, separators=(',', ':'))) + '::jsonb'


def source(table, rows):
    require(table in TABLES, 'Unsupported fixture table')
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


def manifest():
    manifest_path = ROOT / 'MANIFEST.sha256'
    if not manifest_path.is_file():
        return
    lines = manifest_path.read_text(encoding='utf8').splitlines()
    for line in lines:
        if not line.strip():
            continue
        digest, relative = line.split('  ', 1)
        path = (ROOT / relative).resolve()
        require(path.is_relative_to(ROOT) and path.is_file() and
                hashlib.sha256(path.read_bytes()).hexdigest() == digest, 'Manifest mismatch: ' + relative)


def validate_rows(rows):
    expected = dict(categories=12, users=12, vendors=9, services=24,
                    service_options=33, service_slots=24, vendor_documents=18, service_images=72)
    require(all(t in rows and len(rows[t]) == n for t, n in expected.items()), 'Unexpected package counts')
    require('service_safety_documents' in rows, 'Missing safety document dataset')
    ids = [r['id'] for rs in rows.values() for r in rs]
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


def payload(args=None):
    records = {t: load(t + '.json')['records'] for t in TABLES}
    rows = {t: [copy.deepcopy(r['sqlRow']) for r in rs] for t, rs in records.items()}
    validate_rows(rows)
    media = load('media_index.json')['records']
    for item in media:
        path = (ROOT / item['path']).resolve()
        if path.is_file():
            require(path.is_relative_to(ROOT) and hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'],
                    'Media checksum mismatch: ' + item['path'])
        else:
            require(args is None or getattr(args, 'reuse_storage', False) or getattr(args, 'validate_only', False),
                    'Local media binaries not bundled; use --reuse-storage to use verified Cloudinary assets')
    available = {m['sha256'] for m in media}
    for table in ('service_images', 'vendor_documents', 'service_safety_documents'):
        require(all(r.get('mediaSha256') in available for r in records[table]), 'Media mapping absent: ' + table)
    return records, rows, media


def target_guard(args):
    require(args.development, 'Explicit --development is required for any database operation')
    require(args.database and args.database.startswith('danasea_demo_') and
            args.docker_container != 'pbl6_httt-postgres-1', 'Only isolated DEVELOPMENT targets allowed; main/shared source forbidden')
    if args.docker_container:
        d = json.loads(subprocess.run(['docker', 'inspect', args.docker_container], capture_output=True, check=True).stdout)[0]
        require(all(m.get('Name') != 'pbl6_httt_postgres_data' for m in d['Mounts']), 'Main PostgreSQL volume is forbidden')
    if not args.docker_container:
        require(args.host in ('127.0.0.1', 'localhost', '::1'), 'Remote/shared database is not supported')


def verify_backup(args):
    if args.backup is None:
        return
    raw = args.backup.read_bytes()
    require(raw.startswith(b'PGDMP'), 'Backup is not a PostgreSQL custom archive')
    command = (['docker', 'exec', '-i', args.docker_container, 'pg_restore', '--list'] if args.docker_container
               else ['pg_restore', '--list'])
    p = subprocess.run(command, input=raw, capture_output=True, timeout=45)
    require(p.returncode == 0 and not p.stderr and b'TABLE DATA' in p.stdout, 'Backup archive validation failed')


def upload_media(media, args):
    if getattr(args, 'dry_run', False) and getattr(args, 'upload_media', False):
        raise ValueError('Dry-run cannot upload media to Cloudinary')
    (ROOT / 'runtime').mkdir(exist_ok=True)
    cache_path = ROOT / 'runtime/storage.json'
    bundled = BASE_DIR / 'storage_manifest.json'
    cache = (json.loads(cache_path.read_text(encoding='utf8')) if cache_path.exists()
             else json.loads(bundled.read_text(encoding='utf8')) if bundled.exists() else {})
    cloud = os.environ.get('CLOUDINARY_CLOUD_NAME')
    key = os.environ.get('CLOUDINARY_API_KEY')
    secret = os.environ.get('CLOUDINARY_API_SECRET')
    if args.upload_media:
        cloud = cloud or input('Cloudinary cloud name: ').strip()
        key = key or getpass.getpass('Cloudinary API key (hidden): ')
        secret = secret or getpass.getpass('Cloudinary API secret (hidden): ')
    require(args.upload_media or args.reuse_storage, 'Use --upload-media (your Cloudinary) or explicitly --reuse-storage')
    urls = {}
    for i, item in enumerate(media, 1):
        old = cache.get(item['sha256'])
        if old and (args.reuse_storage or old.get('cloudName') == cloud):
            url = old['url']
        else:
            require(args.upload_media and all([cloud, key, secret]), 'Storage credentials unavailable; no fake URL fallback')
            path = ROOT / item['path']
            require(path.is_file(), 'Local media file required for upload: ' + item['path'])
            params = dict(timestamp=str(int(time.time())), folder='danasea_demo_data_package/' + item['kind'],
                          public_id=item['sha256'], overwrite='false')
            signature = hashlib.sha1(('&'.join(k + '=' + params[k] for k in sorted(params)) + secret).encode()).hexdigest()
            boundary = 'Danasea' + uuid.uuid4().hex
            chunks = []
            for name, value in dict(params, api_key=key, signature=signature).items():
                chunks.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="' + name + '"\r\n\r\n' + value + '\r\n').encode())
            chunks.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + path.name + '"\r\nContent-Type: ' + (mimetypes.guess_type(path.name)[0] or 'application/octet-stream') + '\r\n\r\n').encode())
            chunks += [path.read_bytes(), ('\r\n--' + boundary + '--\r\n').encode()]
            request = urllib.request.Request('https://api.cloudinary.com/v1_1/' + cloud + '/auto/upload',
                                             data=b''.join(chunks), headers={'Content-Type': 'multipart/form-data; boundary=' + boundary}, method='POST')
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    uploaded = json.load(response)
            except urllib.error.HTTPError as e:
                raise ValueError('STORAGE_BLOCKED: Cloudinary HTTP ' + str(e.code) + '; private response withheld')
            url = uploaded.get('secure_url')
            require(isinstance(url, str) and url.startswith('https://res.cloudinary.com/') and len(url) <= 255,
                    'Storage returned unsupported URL')
        skip_readback = (old and old.get('readback') == 'HTTP_200_SHA256_PASS' and old.get('sha256') == item['sha256'] and
                         (getattr(args, 'dry_run', False) or (getattr(args, 'reuse_storage', False) and '/image/authenticated/' in url and not (cloud and key and secret))))
        if not skip_readback:
            fetch_url = url
            if '/image/authenticated/' in url and cloud and key and secret:
                now = int(time.time())
                ext = Path(item['path']).suffix.lstrip('.') or 'png'
                params = dict(timestamp=str(now), expires_at=str(now + 300),
                              public_id='danasea_demo_data_package/' + item['kind'] + '/' + item['sha256'],
                              format=ext, type='authenticated', api_key=key)
                canonical = '&'.join(k + '=' + params[k] for k in sorted(params) if k != 'api_key')
                params['signature'] = hashlib.sha1((canonical + secret).encode()).hexdigest()
                fetch_url = 'https://api.cloudinary.com/v1_1/' + cloud + '/image/download?' + urllib.parse.urlencode(params)
            with urllib.request.urlopen(fetch_url, timeout=45) as response:
                raw = response.read()
                require(response.status == 200 and hashlib.sha256(raw).hexdigest() == item['sha256'],
                        'Storage readback/checksum differs; stop before database write')
        cache[item['sha256']] = dict(url=url, cloudName=cloud or (old.get('cloudName') if old else None),
                                     sha256=item['sha256'], readback='HTTP_200_SHA256_PASS', classification='DEVELOPMENT_SYNTHETIC')
        if not getattr(args, 'dry_run', False):
            cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
        urls[item['sha256']] = url
        if i % 10 == 0 or i == len(media):
            print(json.dumps(dict(storageVerified=i, total=len(media))), flush=True)
    return urls


def apply(args, records, rows, password):
    import bcrypt
    urls = upload_media(load('media_index.json')['records'], args)
    for table, column in [('vendor_documents', 'file_url'), ('service_images', 'url'), ('service_safety_documents', 'file_url')]:
        for record, row in zip(records[table], rows[table]):
            row[column] = urls[record['mediaSha256']]
    for user in rows['users']:
        user['password_hash'] = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10)).decode()
    session = Session(args)
    results = {}
    try:
        session.send('LOCK TABLE ' + ','.join('public.' + t for t in TABLES) + ' IN SHARE ROW EXCLUSIVE MODE')
        identity = session.query(
            "SELECT json_build_object('database',current_database(),'schema',"
            "(SELECT table_schema FROM information_schema.tables WHERE table_schema='public' AND table_name='users'),"
            "'flyway',(SELECT version FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1),"
            "'failed',(SELECT count(*) FROM public.flyway_schema_history WHERE NOT success))")
        validate_identity(identity, args.database)
        columns = session.query("SELECT json_agg(x) FROM (SELECT table_name,column_name,is_nullable,"
                                "column_default,is_generated,character_maximum_length,data_type FROM information_schema.columns "
                                "WHERE table_schema='public') x")
        colmap = {(c['table_name'], c['column_name']): c for c in columns}
        if ('services', 'waiver_required') in colmap:
            for s in rows['services']:
                s.update(waiver_required=False, waiver_version=1)
        for table, rs in rows.items():
            for row in rs:
                for name, value in row.items():
                    c = colmap.get((table, name))
                    require(c is not None and c['is_generated'] == 'NEVER', 'Unsupported column ' + table + '.' + name)
                    if name not in ('created_at', 'updated_at'):
                        require(value is not None or c['is_nullable'] == 'YES', 'NULL violates ' + table + '.' + name)
                    limit = c['character_maximum_length']
                    require(not isinstance(value, str) or limit is None or len(value) <= limit, 'Value length violates ' + table + '.' + name)
        all_ids = [r['id'] for rs in rows.values() for r in rs]
        wanted = ','.join(lit(i) + '::uuid' for i in all_ids)
        for table, rs in rows.items():
            own = ','.join(lit(r['id']) + '::uuid' for r in rs)
            n = session.query('SELECT to_json(count(*)) FROM public.' + table +
                              ' WHERE id IN (' + wanted + ') AND id NOT IN (' + own + ')')
            require(n == 0, 'UUID belongs to a different table: ' + table)
        # Refuse same natural identity with another UUID. Do not silently skip collisions.
        for table, field in [('users', 'email'), ('users', 'phone'), ('vendors', 'tax_code'),
                             ('vendors', 'user_id'), ('categories', 'slug'), ('services', 'slug')]:
            values = [r for r in rows[table] if r[field] is not None]
            expected = source(table, values)
            compare = ('lower(btrim(t.email))=lower(btrim(e.email))' if field == 'email' else 't.' + field + '=e.' + field)
            n = session.query('SELECT to_json(count(*)) FROM public.' + table + ' t JOIN ' + expected +
                              ' e ON ' + compare + ' WHERE t.id<>e.id')
            require(n == 0, 'Natural-key collision ' + table + '.' + field)
        for table, fields in [('vendor_documents', ['vendor_id', 'doc_type']), ('service_images', ['service_id', 'sort_order']),
                              ('service_safety_documents', ['service_id', 'file_url'])]:
            n = session.query('SELECT to_json(count(*)) FROM public.' + table + ' t JOIN ' + source(table, rows[table]) +
                              ' e ON ' + ' AND '.join('t.' + f + '=e.' + f for f in fields) + ' WHERE t.id<>e.id')
            require(n == 0, 'Duplicate logical media/document mapping in ' + table)
        for table in TABLES:
            rs = rows[table]
            expected = source(table, rs)
            remove = "-'created_at'-'updated_at'" + ("-'password_hash'" if table == 'users' else "-'search_vector'" if table == 'services' else '')
            mismatch = session.query('SELECT coalesce(json_agg(json_build_object(\'id\',t.id,\'differentFields\','
                                     '(SELECT json_agg(k) FROM jsonb_object_keys(to_jsonb(e)' + remove + ') k WHERE '
                                     '(to_jsonb(t)->k) IS DISTINCT FROM (to_jsonb(e)->k)))),\'[]\'::json) FROM public.' + table +
                                     ' t JOIN ' + expected + ' e ON t.id=e.id WHERE (to_jsonb(t)' + remove + ')<>(to_jsonb(e)' + remove + ')')
            require(not mismatch, 'Existing fixture differs; no overwrite: ' + table + ' ' + json.dumps(mismatch))
            if table == 'users':
                hashes = session.query("SELECT coalesce(json_agg(json_build_object('id',t.id,'hash',t.password_hash)),'[]'::json) FROM public.users t JOIN " + expected + ' e ON t.id=e.id')
                require(all(h['hash'] and bcrypt.checkpw(password.encode(), h['hash'].encode()) for h in hashes),
                        'Existing fixture password differs; not replaced')
            names = list(rs[0])
            targets = ','.join('"' + k + '"' for k in names)
            expressions = ','.join('coalesce(r."' + k + '",CURRENT_TIMESTAMP)' if k in ('created_at', 'updated_at')
                                   else 'r."' + k + '"' for k in names)
            inserted = session.query('WITH added AS (INSERT INTO public.' + table + ' (' + targets + ') SELECT ' + expressions +
                                     ' FROM ' + expected + ' r WHERE NOT EXISTS(SELECT 1 FROM public.' + table + ' t WHERE t.id=r.id) RETURNING id) '
                                     'SELECT coalesce(json_agg(id),\'[]\'::json) FROM added')
            equal = session.query('SELECT to_json(count(*)) FROM public.' + table + ' t JOIN ' + expected +
                                  ' e ON t.id=e.id WHERE (to_jsonb(t)' + remove + ')=(to_jsonb(e)' + remove + ')')
            require(equal == len(rs), 'Post-insert comparison failed: ' + table)
            results[table] = dict(expected=len(rs), inserted=len(inserted), existing=len(rs) - len(inserted), equal=equal)
        session.finish(commit=args.apply)
    finally:
        session.close()
    if args.apply:
        verify = Session(args, readonly=True)
        try:
            for table, rs in rows.items():
                remove = "-'created_at'-'updated_at'" + ("-'password_hash'" if table == 'users' else "-'search_vector'" if table == 'services' else '')
                n = verify.query('SELECT to_json(count(*)) FROM public.' + table + ' t JOIN ' + source(table, rs) +
                                 ' e ON t.id=e.id WHERE (to_jsonb(t)' + remove + ')=(to_jsonb(e)' + remove + ')')
                require(n == len(rs), 'Independent post-COMMIT comparison failed; inspect before retry: ' + table)
            verify.finish()
        finally:
            verify.close()
    return dict(status='COMMIT_SELECT_PASS' if args.apply else 'DRY_RUN_ROLLBACK_PASS',
                tables=results, storageVerified=len(urls), identity=identity)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--validate-only', action='store_true', default=True, help='Offline validation only (default)')
    mode.add_argument('--dry-run', action='store_true')
    mode.add_argument('--apply', action='store_true')
    parser.add_argument('--development', action='store_true')
    parser.add_argument('--database')
    parser.add_argument('--docker-container')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=5432)
    parser.add_argument('--user', default='postgres')
    parser.add_argument('--backup', type=Path)
    parser.add_argument('--upload-media', action='store_true')
    parser.add_argument('--reuse-storage', action='store_true')
    parser.add_argument('--password-stdin', action='store_true', help='Automation only: secret line on stdin, never a file/argv')
    args = parser.parse_args()

    if args.dry_run or args.apply:
        args.validate_only = False

    manifest()
    records, rows, media = payload(args)
    if args.validate_only:
        print(json.dumps(dict(status='OFFLINE_VALIDATION_PASS', counts={t: len(rs) for t, rs in rows.items()},
                              mediaFiles=len(media), database='NOT_CONNECTED')))
        return
    target_guard(args)
    verify_backup(args)
    if not args.docker_container:
        args.db_password = getpass.getpass('PostgreSQL password (hidden): ')
    password = sys.stdin.readline().rstrip('\r\n') if args.password_stdin else getpass.getpass('DEMO password for new accounts (hidden): ')
    require(8 <= len(password) and len(password.encode()) <= 72, 'Invalid password length')
    result = apply(args, records, rows, password)
    if not args.dry_run:
        (ROOT / 'runtime/install_result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print('BLOCKED: ' + (str(exc) if isinstance(exc, ValueError) else type(exc).__name__ + '; private details withheld'), file=sys.stderr)
        sys.exit(1)
