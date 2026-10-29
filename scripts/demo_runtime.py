"""Fixed Docker/PostgreSQL operations behind the demo lifecycle controller."""
import contextlib
import hashlib
import io
import ipaddress
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import backup
import release
from demo_data import atomic_json, trusted

REDIS_PREFIXES = ('usersById::', 'usersByEmail::', 'categorySafetyRules::',
                  'openMeteoWeather::', 'openMeteoMarine::', 'otp:verify-email:',
                  'otp-send:', 'forgot-pw:', 'reset-pw:', 'inventory:slot:',
                  'ai:confirmation:', 'ai:booking:retries:', 'weather:forecast:',
                  'marine:forecast:', 'ai:weather:forecast:', 'ai:weather:safety:',
                  'ai:ratelimit:ip:', 'ai:ratelimit:user:', 'ai:ratelimit:anon:')


def known_redis_key(key):
    if key.startswith(REDIS_PREFIXES):
        return True
    try:
        ipaddress.ip_address(key)
        return True
    except ValueError:
        return False


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_package(package, expected):
    package = Path(package).resolve()
    manifest = package / 'MANIFEST.sha256'
    if manifest.is_symlink() or digest(manifest) != expected:
        raise ValueError('Package manifest mismatch')
    names = set()
    for line in manifest.read_text().splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  (.+)', line)
        if not match:
            raise ValueError('Invalid package manifest')
        checksum, name = match.groups()
        path = package / name
        if (name in names or Path(name).is_absolute() or '..' in Path(name).parts
                or path.is_symlink() or not path.resolve().is_relative_to(package)
                or not path.is_file() or digest(path) != checksum):
            raise ValueError('Package payload mismatch')
        names.add(name)
    if not names:
        raise ValueError('Empty package manifest')


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r'[a-zA-Z_][a-zA-Z0-9_]{0,62}', value):
        raise ValueError('Unsupported database identity')
    return value


def validate_environment(expected, entries):
    actual = dict(value.split('=', 1) for value in entries if '=' in value)
    if any(actual.get(key) != value for key, value in expected.items()):
        raise ValueError('Container environment drift from reviewed Compose configuration')


class Runtime:
    def __init__(self, policy, state):
        self.policy = policy
        self.state = Path(state)
        self.app = Path(policy['app_dir']).resolve()
        self.package = self.app / 'demo_data_handoff'
        self.environment = dict(os.environ)
        self.journal = None
        self.config_fingerprint = None

    def command(self, args, *, input=None, timeout=300, **kwargs):
        return subprocess.run(args, input=input, check=True, capture_output=True,
                              cwd=self.app, timeout=timeout, **kwargs).stdout

    def compose(self, *args):
        return self.command(['docker', 'compose', '-f', str(self.app / 'docker-compose.prod.yml'),
                             *args], env=self.environment)

    def sql(self, query, database=None):
        return self.command(['docker', 'exec', '-i', self.container, 'psql', '-X', '-q', '-A', '-t',
                             '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate',
                             '-U', self.admin, '-d', database or self.database],
                            input=(query + ';\n').encode()).decode().strip()

    def fingerprints(self):
        # Schema metadata and migration history are stable across data replacements.
        schema = self.sql("""SELECT json_build_object(
          'migrations', (SELECT json_agg(row_to_json(f) ORDER BY installed_rank) FROM flyway_schema_history f),
          'columns', (SELECT json_agg(row_to_json(c) ORDER BY table_name,ordinal_position)
                      FROM information_schema.columns c WHERE table_schema='public'),
          'constraints', (SELECT json_agg(json_build_array(c.relname,k.conname,pg_get_constraintdef(k.oid))
                           ORDER BY c.relname,k.conname) FROM pg_constraint k
                           JOIN pg_class c ON c.oid=k.conrelid
                           JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'))""")
        configuration = self.sql("""SELECT json_build_object(
          'system', (SELECT json_agg(row_to_json(x) ORDER BY key) FROM system_configs x),
          'safety', (SELECT json_agg(row_to_json(x) ORDER BY id) FROM category_safety_rules x),
          'commission', (SELECT json_agg(row_to_json(x) ORDER BY id) FROM vendor_commission_policies x))""")
        identity = self.sql("SELECT (SELECT system_identifier::text FROM pg_control_system()) || ':' || "
                            "(SELECT oid::text FROM pg_database WHERE datname=current_database()) || ':' || current_database()")
        return {'schema_sha256': hashlib.sha256(schema.encode()).hexdigest(),
                'configuration_sha256': hashlib.sha256(configuration.encode()).hexdigest(),
                'database_identity': identity}

    def preflight(self, body):
        self.body = body
        if (self.app / '.deploy-state').read_text().strip() != body['sha']:
            raise ValueError('Deployed SHA drift')
        if self.command(['git', 'rev-parse', 'HEAD']).decode().strip() != body['sha']:
            raise ValueError('Checkout SHA drift')
        self.command(['git', 'diff', '--quiet', 'HEAD', '--', 'demo_data_handoff', 'docker-compose.prod.yml'])
        # Bind the signed checksum to the accepted Git object as well as the working tree.
        committed = self.command(['git', 'show', body['sha'] + ':demo_data_handoff/MANIFEST.sha256'])
        if hashlib.sha256(committed).hexdigest() != body['package_sha256']:
            raise ValueError('Committed package manifest mismatch')
        verify_package(self.package, body['package_sha256'])
        accepted = release.read(self.app / '.releases' / (body['sha'] + '.json'),
                                sha=body['sha'], repository=self.policy['repository'])
        self.images = accepted['images']
        for service, image in self.images.items():
            self.environment[service.upper() + '_IMAGE'] = image
        self.environment['IMAGE_TAG'] = body['sha']
        self.config = json.loads(self.compose('config', '--format', 'json'))
        for service, image in self.images.items():
            config = self.config['services'][service]
            if config['image'] != image:
                raise ValueError('Compose image drift')
            container = config['container_name']
            actual = json.loads(self.command(['docker', 'inspect', container]))[0]
            if actual['Config']['Image'] != image:
                raise ValueError('Running image drift')
        pg = self.config['services']['postgres']
        self.container = pg['container_name']
        values = pg['environment']
        actual_pg = json.loads(self.command(['docker', 'inspect', self.container]))[0]
        validate_environment({key: values[key] for key in ('POSTGRES_DB', 'POSTGRES_USER')},
                             actual_pg['Config']['Env'])
        if actual_pg['Config']['Image'] != pg['image']:
            raise ValueError('PostgreSQL image drift')
        self.database = identifier(values['POSTGRES_DB'])
        self.admin = identifier(values['POSTGRES_USER'])
        be = self.config['services']['backend']['environment']
        backend_container = self.config['services']['backend']['container_name']
        actual_backend = json.loads(self.command(['docker', 'inspect', backend_container]))[0]
        validate_environment({key: be[key] for key in ('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME')},
                             actual_backend['Config']['Env'])
        self.runtime_role = identifier(be['SPRING_DATASOURCE_USERNAME'])
        if self.runtime_role == self.admin:
            raise ValueError('A separate unprivileged runtime role is required')
        role_ok = self.sql(f"SELECT count(*) FROM pg_roles WHERE rolname='{self.runtime_role}' "
                           "AND NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls) "
                           "AND NOT EXISTS (SELECT FROM pg_auth_members m WHERE m.member=pg_roles.oid)")
        if role_ok != '1':
            raise ValueError('Runtime role has unreviewed privileges')
        # Only the local maintenance administrator may bypass the database gate.
        if self.sql(f"SELECT count(*) FROM pg_roles WHERE rolsuper AND rolcanlogin AND rolname<>'{self.admin}'") != '0':
            raise ValueError('Additional superuser login can bypass maintenance')
        if self.sql(f"SELECT rolsuper FROM pg_roles WHERE rolname='{self.admin}'") != 't':
            raise ValueError('Maintenance administrator cannot enforce database access')
        configuration = self.policy['backup_config']
        self.backup_config = json.loads(trusted(Path(configuration)).read_text())
        if self.backup_config.get('container', 'danasea-postgres') != self.container:
            raise ValueError('Backup source does not match target')
        self.baseline = self.fingerprints()
        self.config_fingerprint = self.baseline['configuration_sha256']
        import bcrypt  # Installed host dependency; do not discover it after taking service down.
        if not callable(bcrypt.hashpw):
            raise ValueError('Importer dependency unavailable')
        # Ordinary preflight rejects business conflicts which replacement intentionally removes.
        # Inspect only the reviewed schema inventory in a READ ONLY transaction here.
        code = """import importlib.util,json,sys,types
spec=importlib.util.spec_from_file_location('demo_importer',sys.argv[1])
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.manifest();m.load_fixtures()
a=types.SimpleNamespace(docker_container=sys.argv[2],database=sys.argv[3],user=sys.argv[4])
s=m.Session(a,readonly=True)
try:
 actual=s.query(m.RESET_INVENTORY_SQL)
 m.require(actual==json.loads((m.ROOT/'reset_inventory.json').read_text())['schema'],'Unreviewed reset schema')
 s.finish(commit=False)
finally:s.close()
"""
        self.command([sys.executable, '-I', '-c', code, str(self.package / 'install_demo_data.py'),
                      self.container, self.database, self.admin])

    def enter_maintenance(self, journal):
        self.journal = journal
        # This server exposes the backend only through this frontend. Both processes stop.
        self.compose('stop', '--timeout', '60', 'frontend', 'backend')
        for service in ('frontend', 'backend'):
            container = self.config['services'][service]['container_name']
            if self.command(['docker', 'inspect', '--format', '{{.State.Running}}', container]).strip() != b'false':
                raise ValueError('Public application or writer is still running')

    def audit_queues(self):
        container = self.config['services']['rabbitmq']['container_name']
        vhosts = json.loads(self.command(['docker', 'exec', container, 'rabbitmqctl', '-q',
                                         'list_vhosts', 'name', '--formatter', 'json']))
        for vhost in vhosts:
            queues = json.loads(self.command(['docker', 'exec', container, 'rabbitmqctl', '-q',
                                             'list_queues', '-p', vhost['name'], 'name', 'messages_ready',
                                             'messages_unacknowledged', '--formatter', 'json']))
            if any(int(q['messages_ready']) or int(q['messages_unacknowledged']) for q in queues):
                raise ValueError('Pending queue work requires operator review')

    def redis(self, arguments, input=None):
        container = self.config['services']['redis']['container_name']
        return self.command(['docker', 'exec', '-i', container, 'sh', '-ec',
                             'export REDISCLI_AUTH="$REDIS_PASSWORD"; exec redis-cli --no-auth-warning "$@"',
                             'sh', *arguments], input=input)

    def redis_keys(self):
        # Audit every logical DB, because the shipped deployment owns this Redis instance.
        keyspace = self.redis(['--raw', 'INFO', 'keyspace']).decode()
        if any(not line.startswith('db0:') for line in keyspace.splitlines()
               if line.startswith('db')):
            raise ValueError('Unreviewed Redis database')
        raw = self.redis(['--raw', '--scan'])
        try:
            keys = raw.decode('utf-8').splitlines()
        except UnicodeDecodeError:
            raise ValueError('Unreviewed Redis key encoding') from None
        if len(keys) > 100000 or any(not known_redis_key(key) for key in keys):
            raise ValueError('Unknown shared Redis key layout')
        return keys

    def safe_state(self, journal):
        self.audit_queues()
        if any(key.startswith('inventory:slot:') for key in self.redis_keys()):
            # A DB backup cannot restore capacity reservations held only in Redis.
            raise ValueError('Active inventory holds require expiry before replacement')
        self.gate(journal)

    def gate(self, journal):
        unexpected = self.sql("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() "
                              "AND pid<>pg_backend_pid() AND backend_type='client backend' "
                              f"AND usename IS DISTINCT FROM '{self.runtime_role}'")
        if unexpected != '0':
            raise ValueError('Unexpected database writer requires operator review')
        if 'database_gate' not in journal:
            settings = json.loads(self.sql("SELECT json_build_object('limit', datconnlimit, 'login', "
                                           f"(SELECT rolcanlogin FROM pg_roles WHERE rolname='{self.runtime_role}')) "
                                           "FROM pg_database WHERE datname=current_database()"))
            journal['database_gate'] = settings
            journal.save()
        self.sql(f'ALTER ROLE "{self.runtime_role}" NOLOGIN; ALTER DATABASE "{self.database}" CONNECTION LIMIT 0')
        self.sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                 f"WHERE datname=current_database() AND pid<>pg_backend_pid() AND usename='{self.runtime_role}'")
        if self.sql("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid()") != '0':
            raise ValueError('Database writer remains active')

    def ungate(self, journal):
        settings = journal.get('database_gate')
        if settings is None:
            return
        if type(settings.get('limit')) is not int or not -1 <= settings['limit'] <= 2147483647 or type(settings.get('login')) is not bool:
            raise ValueError('Invalid recovery gate settings')
        login = 'LOGIN' if settings['login'] else 'NOLOGIN'
        self.sql(f'ALTER ROLE "{self.runtime_role}" {login}; ALTER DATABASE "{self.database}" CONNECTION LIMIT {settings["limit"]}')

    def snapshot(self, directory):
        capture = io.StringIO()
        with contextlib.redirect_stdout(capture):
            backup.backup(self.backup_config)
        report = json.loads(capture.getvalue())
        source = Path(self.backup_config['directory']).expanduser().resolve() / report['backup']
        metadata = json.loads(Path(str(source) + '.json').read_text())
        if metadata.get('restore_verified') is not True or digest(source) != metadata['sha256']:
            raise ValueError('Unverified backup')
        protected = self.state / 'backups'
        protected.mkdir(mode=0o700, exist_ok=True)
        destination = protected / source.name
        try:
            os.link(source, destination)
        except OSError:
            with source.open('rb') as stream, destination.open('xb') as output:
                shutil.copyfileobj(stream, output)
                output.flush()
                os.fsync(output.fileno())
        os.chmod(destination, 0o600)
        point = dict(self.fingerprints(), backup_id=source.name, sha256=metadata['sha256'],
                     release_sha=self.body['sha'], restore_verified=True)
        atomic_json(Path(str(destination) + '.json'), point)
        return point

    def select_backup(self, backup_id):
        if not isinstance(backup_id, str) or not re.fullmatch(r'verified-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}\.dump', backup_id):
            raise ValueError('Invalid named demo recovery point')
        archive = self.state / 'backups' / backup_id
        if archive.is_symlink() or not archive.is_file():
            raise ValueError('Missing named demo backup')
        point = json.loads(Path(str(archive) + '.json').read_text())
        if (point.get('backup_id') != backup_id or point.get('restore_verified') is not True
                or digest(archive) != point.get('sha256')
                or point.get('database_identity') != self.baseline['database_identity']
                or point.get('schema_sha256') != self.baseline['schema_sha256']):
            raise ValueError('Backup is corrupt or belongs to another database/schema')
        return point

    def importer(self, database, point, password, ownership, apply=True):
        if not password or '\n' in password or '\r' in password:
            raise ValueError('Missing runtime demo password')
        archive = self.state / 'backups' / point['backup_id']
        arguments = [sys.executable, '-I', str(self.package / 'install_demo_data.py'), '--replace',
                     '--apply' if apply else '--dry-run', '--docker-container', self.container,
                     '--database', database, '--user', self.admin, '--backup-file', str(archive),
                     '--ownership-file', str(ownership), '--demo-password-stdin']
        result = json.loads(self.command(arguments, input=(password + '\n').encode(), timeout=600))
        if apply and (result.get('transaction') != 'COMMITTED' or result.get('totalVerifiedInDb') != 270):
            raise ValueError('Replacement did not return a verified committed receipt')
        return result

    def rehearse(self, point, password):
        database = 'danasea_restore_' + secrets.token_hex(8)
        archive = self.state / 'backups' / point['backup_id']
        self.sql(f'CREATE DATABASE "{database}"', database='postgres')
        try:
            backup.restore(archive, self.container, database)
            with tempfile.TemporaryDirectory(prefix='.rehearse-', dir=self.state) as temporary:
                self.importer(database, point, password, Path(temporary) / 'ownership.json')
        finally:
            self.sql(f'DROP DATABASE "{database}" WITH (FORCE)', database='postgres')

    def replace(self, point, password, directory):
        # Re-audit immediately before the transaction rather than trusting an earlier check.
        self.audit_queues()
        self.redis_keys()
        return self.importer(self.database, point, password, directory / 'ownership.json')

    def restore(self, point):
        self.gate(self.journal)
        self.audit_queues()
        archive = self.state / 'backups' / point['backup_id']
        if archive.is_symlink() or digest(archive) != point['sha256']:
            raise ValueError('Recovery archive changed')
        current = self.fingerprints()
        if any(current[key] != point[key] for key in ('database_identity', 'schema_sha256')):
            raise ValueError('Incompatible recovery archive')
        # The archive excludes ACLs. Restore data into the identical verified schema,
        # preserving its exact ownership/grants, instead of dropping/recreating tables.
        tables = json.loads(self.sql("SELECT json_agg(format('%I.%I',n.nspname,c.relname) ORDER BY c.relname) "
                                     "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                                     "WHERE n.nspname='public' AND c.relkind='r'"))
        if not tables:
            raise ValueError('Missing recovery table inventory')
        with tempfile.TemporaryFile(dir=self.state) as script:
            os.fchmod(script.fileno(), 0o600)
            script.write(('TRUNCATE TABLE ' + ','.join(tables) + ' RESTART IDENTITY;\n').encode())
            script.flush()
            # pg_restore emits COPY data, trigger guards and sequence values as SQL.
            with archive.open('rb') as stream:
                subprocess.run(['docker', 'exec', '-i', self.container, 'pg_restore', '--data-only',
                                '--disable-triggers', '--no-owner', '--no-privileges', '--file', '-'],
                               stdin=stream, stdout=script, stderr=subprocess.PIPE, check=True, timeout=600)
            script.flush()
            script.seek(0)
            self.command(['docker', 'exec', '-i', self.container, 'psql', '-X', '-q',
                          '--single-transaction', '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate',
                          '-U', self.admin, '-d', self.database], stdin=script, timeout=600)
        restored = self.fingerprints()
        if any(restored[key] != point[key] for key in ('database_identity', 'schema_sha256', 'configuration_sha256')):
            raise ValueError('Restored configuration/schema verification failed')
        self.config_fingerprint = point['configuration_sha256']

    def invalidate(self):
        # Recheck the exact audited key set and delete atomically, with no broad FLUSH.
        keys = self.redis_keys()
        script = ("local seen={} for _,k in ipairs(KEYS) do seen[k]=true end "
                  "local actual=redis.call('KEYS','*') "
                  "if #actual~=#KEYS then return redis.error_reply('Concurrent Redis writer') end "
                  "for _,k in ipairs(actual) do if not seen[k] then return redis.error_reply('Unknown key') end end "
                  "for _,k in ipairs(actual) do redis.call('DEL',k) end return #actual")
        values = ['EVAL', script, str(len(keys)), *keys]
        command = b'*' + str(len(values)).encode() + b'\r\n'
        for value in values:
            payload = value.encode()
            command += b'$' + str(len(payload)).encode() + b'\r\n' + payload + b'\r\n'
        if b'errors: 0' not in self.redis(['--pipe'], input=command):
            raise ValueError('Cache invalidation failed')
        if self.redis_keys():
            raise ValueError('Redis has a concurrent writer')

    def internal(self, path, payload=None):
        # --config stdin keeps passwords and bearer credentials out of the process list.
        settings = 'url = ' + json.dumps('http://127.0.0.1:8080' + path) + '\n'
        if payload is not None:
            settings += 'header = "Content-Type: application/json"\ndata = ' + json.dumps(json.dumps(payload)) + '\n'
        container = self.config['services']['backend']['container_name']
        return json.loads(self.command(['docker', 'exec', '-i', container, 'curl', '--fail', '--silent',
                                        '--max-time', '20', '--config', '-'], input=settings.encode()))

    def verify(self, demo=False, password=None):
        if self.fingerprints()['configuration_sha256'] != self.config_fingerprint:
            raise ValueError('Preserved system configuration changed')
        self.ungate(self.journal)
        self.compose('up', '-d', '--no-deps', '--wait', '--wait-timeout', '300', 'backend')
        if self.internal('/actuator/health').get('status') != 'UP':
            raise ValueError('Backend health failed')
        catalog = self.internal('/api/services?page=0&size=1')
        if not isinstance(catalog.get('content'), list) or demo and not catalog['content']:
            raise ValueError('Catalog read failed')
        if demo:
            login = self.internal('/api/auth/login', {'email': 'admin_demo@danasea.test', 'password': password})
            if not login.get('accessToken') or login.get('email') != 'admin_demo@danasea.test':
                raise ValueError('Demo login failed')
        # Representative unauthenticated list read must work before opening public access.
        self.internal('/api/categories')

    def resume(self, journal):
        self.ungate(journal)
        self.compose('up', '-d', '--no-deps', '--wait', '--wait-timeout', '300', 'frontend')

    def freeze(self, journal):
        self.enter_maintenance(journal)
        self.gate(journal)
