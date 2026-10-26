#!/usr/bin/env python3
"""Prepare private production files, check deployment prerequisites, or initialize the runtime DB role."""
import argparse
import base64
import hashlib
import ipaddress
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
from pathlib import Path

EDGE = ipaddress.ip_network('172.30.10.0/28')
SECRETS = ['APP_JWT_SECRET', 'APP_CHECKIN_QR_SECRET', 'APP_PAYMENT_WEBHOOK_SECRET',
           'POSTGRES_APP_PASSWORD', 'SPRING_REDIS_PASSWORD', 'SPRING_RABBITMQ_PASSWORD',
           'RABBITMQ_ADMIN_PASSWORD', 'AI_QUYET_API_KEY', 'AI_CLIPROXY_API_KEY', 'CLIPROXY_MANAGEMENT_KEY']
DEFAULTS = {'POSTGRES_APP_USER': 'danasea_app', 'SPRING_RABBITMQ_USERNAME': 'danasea_app',
            'SPRING_RABBITMQ_VIRTUAL_HOST': '/danasea', 'RABBITMQ_ADMIN_USER': 'danasea_admin'}
KEYS = SECRETS + list(DEFAULTS) + ['POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'CLIPROXY_UID', 'CLIPROXY_GID', 'QUYET_MODEL_DIR']


def command(args, **kwargs):
    result = subprocess.run(args, capture_output=True, text=True, **kwargs)
    if result.returncode:
        raise ValueError('Command failed: ' + ' '.join(args[:3]) + '. Check configuration locally; credential output is suppressed.')
    return result.stdout


def settings(root):
    """Use Compose's parser so quoting, interpolation and exported variables match deployment."""
    with tempfile.TemporaryDirectory(prefix='danasea-env-') as directory:
        path = Path(directory) / 'compose.json'
        path.write_text(json.dumps({'services': {'settings': {'image': 'busybox',
                        'environment': {key: '${' + key + '-}' for key in KEYS}}}}))
        result = json.loads(command(['docker', 'compose', '--env-file', str(root / '.env'),
                                     '-f', str(path), 'config', '--format', 'json']))
    # Compose escapes literal dollar signs when rendering a reusable config.
    return {key: value.replace('$$', '$') for key, value in result['services']['settings']['environment'].items()}


def placeholder(value):
    return not value or any(s in value.lower() for s in ['your_', 'change-me', 'default-key',
               'local-development', 'sk-cliproxy-pbl6-local-key', 'postgres_password'])


def write_json(path, value, mode=0o600):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    path.write_text(json.dumps(value, indent=2) + '\n')
    path.chmod(mode)


def prepare(root):
    values = settings(root)
    updates = {key: default for key, default in DEFAULTS.items() if not values[key]}
    updates.update({key: secrets.token_hex(32) for key in SECRETS if placeholder(values[key])})
    for key, value in [('CLIPROXY_UID', os.getuid()), ('CLIPROXY_GID', os.getgid())]:
        if not values[key]:
            updates[key] = str(value)
    values.update(updates)
    if values['CLIPROXY_UID'] == '0':
        raise ValueError('Run preparation as the non-root deployment user.')
    remaining = dict(updates)
    lines = []
    for line in (root / '.env').read_text().splitlines():
        match = re.match(r'^\s*(?:export\s+)?([A-Z_][A-Z_0-9]*)\s*=', line)
        key = match[1] if match else None
        if key in updates:
            if key in remaining:
                lines.append(key + '=' + remaining.pop(key))
            continue
        lines.append(line)
    lines.extend(key + '=' + value for key, value in remaining.items())
    env = root / '.env'
    env.chmod(0o600)
    # Atomic replacement avoids leaving a partially written environment file.
    with tempfile.NamedTemporaryFile(mode='w', dir=root, prefix='.env.prepare-', delete=False) as temporary:
        temporary.write('\n'.join(lines) + '\n')
    Path(temporary.name).replace(env)
    private = root / 'private'
    private.mkdir(mode=0o700, exist_ok=True)
    private.chmod(0o700)
    config = private / 'cli-proxy/config.yaml'
    if not config.exists():
        write_json(config, {'config-version': 8, 'server': {'host': '', 'port': 8317},
            'management': {'allow-remote': True, 'secret-key': values['CLIPROXY_MANAGEMENT_KEY'],
                           'disable-control-panel': False, 'disable-auto-update-panel': True},
            'access': {'api-keys': [values['AI_CLIPROXY_API_KEY']]}, 'oauth': {'auth-dir': '/auths'},
            'observability': {'logs': {'debug': False, 'request-log': False}}})
    (config.parent / 'static').mkdir(mode=0o700, exist_ok=True)
    (root / 'cli-proxy/auths').mkdir(parents=True, mode=0o700, exist_ok=True)

    def user(name, password, tags):
        salt = secrets.token_bytes(4)
        encoded = base64.b64encode(salt + hashlib.sha256(salt + password.encode()).digest()).decode()
        return {'name': name, 'password_hash': encoded, 'hashing_algorithm': 'rabbit_password_hashing_sha256', 'tags': tags}

    vhost = values['SPRING_RABBITMQ_VIRTUAL_HOST']
    users = [user(values['RABBITMQ_ADMIN_USER'], values['RABBITMQ_ADMIN_PASSWORD'], ['administrator']),
             user(values['SPRING_RABBITMQ_USERNAME'], values['SPRING_RABBITMQ_PASSWORD'], [])]
    permissions = [{'user': u['name'], 'vhost': vhost, **{p: ('.*' if u['tags'] else r'^(danasea\..*|notification\..*)$')
                    for p in ['configure', 'write', 'read']}} for u in users]
    write_json(private / 'rabbitmq/definitions.json', {'users': users, 'vhosts': [{'name': vhost}], 'permissions': permissions}, 0o644)
    rabbit = private / 'rabbitmq/rabbitmq.conf'
    rabbit.write_text('definitions.import_backend = local_filesystem\ndefinitions.local.path = /etc/rabbitmq/definitions.json\n')
    rabbit.chmod(0o644)
    print('Prepared private configuration. Existing OAuth files and provider credentials were preserved; secrets are not printed.')


def validate_networks(networks, routes):
    own_devices = set()
    for network in networks:
        for config in (network.get('IPAM') or {}).get('Config') or []:
            subnet = config.get('Subnet')
            if not subnet or ':' in subnet:
                continue
            if EDGE.overlaps(ipaddress.ip_network(subnet)):
                if network['Name'] != 'danasea_edge' or ipaddress.ip_network(subnet) != EDGE:
                    raise ValueError('Production edge subnet overlaps an existing Docker network. Resolve the address plan before deployment.')
                own_devices.add('br-' + network.get('Id', '')[:12])
    for route in routes:
        destination = route.get('dst', 'default')
        if destination == 'default' or ':' in destination:
            continue
        if EDGE.overlaps(ipaddress.ip_network(destination)) and route.get('dev') not in own_devices:
            raise ValueError('Production edge subnet overlaps a host/VPN route. Do not widen trusted proxy ranges to work around this.')


def runtime_role_sql(values):
    names = [values[key] for key in ['POSTGRES_APP_USER', 'POSTGRES_USER', 'POSTGRES_DB']]
    if any(not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]{0,62}', name) for name in names) or names[0] == names[1]:
        raise ValueError('Runtime role must be a separate valid PostgreSQL identifier.')
    role, owner, database = ['"' + name + '"' for name in names]
    password = values['POSTGRES_APP_PASSWORD'].replace("'", "''")
    return f'''BEGIN;
DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '{names[0]}') THEN CREATE ROLE {role} LOGIN; END IF; END $$;
ALTER ROLE {role} LOGIN PASSWORD '{password}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT;
GRANT CONNECT ON DATABASE {database} TO {role};
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM {role};
GRANT USAGE ON SCHEMA public TO {role};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO {role};
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO {role};
ALTER DEFAULT PRIVILEGES FOR ROLE {owner} IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO {role};
ALTER DEFAULT PRIVILEGES FOR ROLE {owner} IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO {role};
DO $$ BEGIN IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM {role}; END IF; END $$;
COMMIT;
'''


def initialize_database(root, values, container):
    sql = runtime_role_sql(values)
    command(['docker', 'exec', '-i', container, 'psql', '-q', '-U', values['POSTGRES_USER'],
             '-d', values['POSTGRES_DB'], '-v', 'ON_ERROR_STOP=1'], input=sql)
    print('Runtime database role initialized; backend has no migration/DDL role.')


def validate_frontend_config(path):
    mode = path.stat().st_mode
    if not mode & 0o004 or mode & 0o022:
        raise ValueError('Public Nginx config must be readable by its container user and not group/other writable (use chmod 644).')


QUYET_MANIFEST_SHA256 = '9ef88d40e9fe14003195b15d181e3bd7bb2802ba5100998c198338b7a640a457'


def validate_quyet_model(directory):
    path = Path(directory)
    if not directory or not path.is_absolute() or not path.is_dir():
        raise ValueError('QUYET_MODEL_DIR must be an existing absolute model directory.')
    manifest = path / 'MANIFEST.sha256'
    if hashlib.sha256(manifest.read_bytes()).hexdigest() != QUYET_MANIFEST_SHA256:
        raise ValueError('Quyet manifest differs from the pinned Small release.')
    covered = set()
    for line in manifest.read_text().splitlines():
        if not line.strip():
            continue
        expected, name = line.split(maxsplit=1)
        name = name.strip().lstrip('*')
        candidate = (path / name).resolve()
        if not candidate.is_relative_to(path.resolve()) or not candidate.is_file():
            raise ValueError('Invalid Quyet model file path.')
        # The image runs UID 10001. Require public read/traverse bits within the mount;
        # parent host directories are traversed by Docker, not by the container user.
        if not candidate.stat().st_mode & 0o004:
            raise ValueError('Quyet model files must be readable by container UID 10001.')
        for parent in [path.resolve(), *candidate.relative_to(path.resolve()).parents]:
            parent = parent if parent.is_absolute() else path.resolve() / parent
            if not parent.stat().st_mode & 0o001:
                raise ValueError('Quyet model directories require container traversal permission.')
        digest = hashlib.sha256()
        with candidate.open('rb') as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(chunk)
        if digest.hexdigest() != expected:
            raise ValueError('Quyet model checksum mismatch.')
        covered.add(name)
    if 'model.safetensors' not in covered:
        raise ValueError('Quyet manifest must cover model.safetensors.')
    if not manifest.stat().st_mode & 0o004:
        raise ValueError('Quyet manifest must be readable by container UID 10001.')


def check(root, values):
    for key in SECRETS:
        if placeholder(values[key]) or len(values[key]) < 32:
            raise ValueError('Missing, weak or placeholder production secret: ' + key)
    if len({values[key] for key in SECRETS}) != len(SECRETS):
        raise ValueError('Production secrets must be distinct.')
    runtime_role_sql(values)
    if values['SPRING_RABBITMQ_USERNAME'] in ['guest', values['RABBITMQ_ADMIN_USER']]:
        raise ValueError('RabbitMQ application user must be separate from guest and the admin account.')
    if (root / '.env').stat().st_mode & 0o077:
        raise ValueError('.env must not be readable by group/others (use chmod 600).')
    config = root / 'private/cli-proxy/config.yaml'
    for path in [config, root / 'private/rabbitmq/definitions.json', root / 'private/rabbitmq/rabbitmq.conf', root / 'frontend/nginx.prod.conf']:
        if not path.is_file():
            raise ValueError('Required deployment file missing: ' + str(path.relative_to(root)))
    validate_frontend_config(root / 'frontend/nginx.prod.conf')
    if config.stat().st_mode & 0o077:
        raise ValueError('Private proxy configuration must have mode 600.')
    try:
        proxy = json.loads(config.read_text())
    except ValueError:
        try:
            import yaml
        except ImportError as error:
            raise ValueError('Install python3-yaml to validate a YAML config rewritten by the proxy management panel.') from error
        try:
            proxy = yaml.safe_load(config.read_text())
        except yaml.YAMLError:
            raise ValueError('Invalid proxy YAML; parser output is suppressed to protect secrets.') from None
    if not isinstance(proxy, dict):
        raise ValueError('Proxy config must be a mapping.')
    if proxy.get('access', {}).get('api-keys') != [values['AI_CLIPROXY_API_KEY']] or proxy.get('oauth', {}).get('auth-dir') != '/auths':
        raise ValueError('Proxy client key or auth directory differs from the prepared environment.')
    if proxy.get('api-keys'):
        raise ValueError('Upstream provider API keys are not part of this OAuth-only deployment.')
    management_key = proxy.get('management', {}).get('secret-key', '')
    # The proxy may persist a bcrypt hash after loading a plaintext management key.
    hashed = isinstance(management_key, str) and re.fullmatch(r'\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}', management_key)
    if not hashed and management_key != values['CLIPROXY_MANAGEMENT_KEY']:
        raise ValueError('Private proxy management key must match its separate strong environment secret.')
    identity = values['CLIPROXY_UID'] or '1000'
    if not identity.isdecimal():
        raise ValueError('CLIPROXY_UID must be numeric.')
    uid = int(identity)
    if uid == 0 or config.stat().st_uid != uid:
        raise ValueError('Proxy config must be owned by its configured non-root UID.')
    auth = root / 'cli-proxy/auths'
    if not auth.is_dir() or auth.stat().st_uid != uid or any(p.stat().st_uid != uid for p in auth.glob('*')):
        raise ValueError('OAuth directory/files must belong to the configured proxy UID. Preserve tokens when fixing ownership.')
    validate_quyet_model(values['QUYET_MODEL_DIR'])
    command(['docker', 'compose', '--env-file', str(root / '.env'), '-f', str(root / 'docker-compose.prod.yml'), 'config', '--quiet'])
    ids = command(['docker', 'network', 'ls', '-q']).split()
    networks = json.loads(command(['docker', 'network', 'inspect', *ids])) if ids else []
    routes = json.loads(command(['ip', '-j', 'route', 'show'])) if sys.platform.startswith('linux') else []
    validate_networks(networks, routes)
    role = values['POSTGRES_APP_USER']
    sql = f"""SELECT COUNT(*) FROM pg_roles r WHERE rolname = '{role}'
AND NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
AND NOT has_schema_privilege(r.oid, 'public', 'CREATE')
AND NOT has_database_privilege(r.oid, current_database(), 'CREATE')
AND NOT EXISTS (SELECT FROM pg_auth_members m WHERE m.member = r.oid)
AND NOT EXISTS (SELECT FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relowner = r.oid AND n.nspname = 'public')"""
    ready = command(['docker', 'exec', 'danasea-postgres', 'psql', '-At', '-U', values['POSTGRES_USER'], '-d', values['POSTGRES_DB'], '-c', sql]).strip()
    if ready != '1':
        raise ValueError('Runtime PostgreSQL role is missing or privileged. Run the explicit init-db step and inspect role memberships.')
    print('Production files, secret boundaries and Docker subnet checks passed. This does not replace post-deploy acceptance.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'check', 'init-db'])
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--postgres-container', default='danasea-postgres')
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        if args.action == 'prepare':
            prepare(root)
        elif args.action == 'init-db':
            initialize_database(root, settings(root), args.postgres_container)
        else:
            check(root, settings(root))
    except (ValueError, OSError, KeyError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
