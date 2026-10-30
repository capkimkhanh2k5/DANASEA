#!/usr/bin/env python3
"""Rehearse candidate images against owned, disposable production-like infrastructure."""
import argparse
import copy
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from contextlib import contextmanager
from pathlib import Path

import acceptance
import backup
import production
import release


def isolate(config):
    config = copy.deepcopy(config)
    for name, service in config['services'].items():
        service.pop('container_name', None)
        service.pop('ports', None)
        networks = service.get('networks', {})
        service['networks'] = {key: {} for key in networks}
        if name == 'backend':
            service['environment'].update({'APP_SCHEDULER_ENABLED': 'false',
                'MANAGEMENT_HEALTH_MAIL_ENABLED': 'false', 'JAVA_TOOL_OPTIONS': '-Xmx512m -XX:ActiveProcessorCount=2'})
        if name == 'frontend':
            service['ports'] = ['127.0.0.1::8080']
        if name == 'quyet':
            service['environment']['QUYET_CPU_THREADS'] = '2'
    config['networks'] = {key: {} for key in config.get('networks', {})}
    config['volumes'] = {key: {} for key in config.get('volumes', {})}
    return config


@contextmanager
def rehearsal_environment(root):
    original = dict(os.environ)
    names = set(re.findall(r'\$\{([A-Z][A-Z0-9_]*)', (root / 'docker-compose.prod.yml').read_text()))
    for file in (root / '.env.example',):
        if file.exists():
            names.update(re.findall(r'^([A-Z][A-Z0-9_]*)=', file.read_text(), re.MULTILINE))
    names.update(production.KEYS)
    try:
        for name in list(os.environ):
            if name in names or name.startswith('COMPOSE_'):
                os.environ.pop(name)
        yield
    finally:
        os.environ.clear()
        os.environ.update(original)


def execute(args, **kwargs):
    result = subprocess.run(args, capture_output=True, **kwargs, check=False)
    if result.returncode:
        raise ValueError('Rehearsal command failed: ' + ' '.join(str(x) for x in args[:3]) + ': ' + result.stderr.decode(errors='replace')[-500:])
    return result.stdout


def http_json(url, body=None, token=None, expected=(200,)):
    headers = {'Content-Type': 'application/json'}
    if body is not None and urllib.parse.urlsplit(url).path == '/api/orders':
        headers['Idempotency-Key'] = str(uuid.uuid4())
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        if response.status not in expected:
            raise ValueError('Rehearsal HTTP contract failed: ' + str(response.status) + ' ' + urllib.parse.urlsplit(url).path)
        data = response.read()
        return json.loads(data) if data else None


def business_probe(origin, postgres):
    email = 'release-' + secrets.token_hex(6) + '@example.invalid'
    password = 'Release!' + secrets.token_hex(20) + 'A1'
    http_json(origin + '/api/auth/register', {'email': email, 'password': password}, expected=(200, 201, 204))
    vendor_user, vendor, category, service, option, slot = [str(uuid.uuid4()) for _ in range(6)]
    sql = f"""
UPDATE users SET is_email_verified=true, full_name='REHEARSAL' WHERE email='{email}';
DELETE FROM system_configs WHERE key='PLATFORM_COMMISSION_RATE';
INSERT INTO system_configs(id,key,value,description,created_at,updated_at) VALUES (gen_random_uuid(),'PLATFORM_COMMISSION_RATE','10.0','Synthetic rehearsal policy',now(),now());
INSERT INTO users(id,email,password_hash,full_name,role,is_email_verified,is_locked,session_version,created_at,updated_at)
VALUES ('{vendor_user}','{vendor_user}@example.invalid','unused','REHEARSAL','VENDOR',true,false,0,now(),now());
INSERT INTO vendors(id,user_id,business_name,verification_status,created_at,updated_at) VALUES ('{vendor}','{vendor_user}','REHEARSAL','APPROVED',now(),now());
INSERT INTO categories(id,name,slug,is_active,requires_safety_cert,created_at,updated_at) VALUES ('{category}','REHEARSAL','rehearsal',true,false,now(),now());
INSERT INTO services(id,vendor_id,category_id,name,status,price,weather_sensitive,view_count,waiver_required,created_at,updated_at) VALUES ('{service}','{vendor}','{category}','REHEARSAL','PUBLISHED',500000,false,0,false,now(),now());
INSERT INTO service_options(id,service_id,name,option_type,pricing_unit,price,status,created_at,updated_at) VALUES ('{option}','{service}','REHEARSAL','SHARED','PER_PERSON',500000,'ACTIVE',now(),now());
INSERT INTO service_slots(id,service_id,date,start_time,end_time,capacity,booked_count,status,inventory_type,created_at,updated_at) VALUES ('{slot}','{service}',CURRENT_DATE+5,'09:00','10:00',10,0,'OPEN','PERSON_LIMIT',now(),now());
"""
    execute(['docker', 'exec', '-i', postgres, 'sh', '-ec', 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'], input=sql.encode())
    token = http_json(origin + '/api/auth/login', {'email': email, 'password': password})['accessToken']
    hold = http_json(origin + '/api/bookings/hold', {'items': [{'slotId': slot, 'optionId': option, 'quantity': 1}]}, token, (201,))
    if float(hold['totalAmount']) != 500000:
        raise ValueError('Booking price changed during rehearsal')
    order = http_json(origin + '/api/orders', {'bookingId': hold['bookingId']}, token, (201,))
    http_json(origin + '/api/orders/' + order['id'], token=token)


def rehearse(root, model_dir, images, baseline, output):
    production.validate_quyet_model(str(model_dir.resolve()))
    project = 'danasea_rehearse_' + secrets.token_hex(6)
    started = time.monotonic()
    with rehearsal_environment(root), tempfile.TemporaryDirectory(prefix='danasea-rehearse-') as directory:
        temporary = Path(directory)
        values = {name: secrets.token_hex(32) for name in production.SECRETS}
        values.update(production.DEFAULTS)
        values.update({'POSTGRES_DB': 'danasea_rehearse', 'POSTGRES_USER': 'owner',
            'POSTGRES_PASSWORD': secrets.token_hex(32), 'POSTGRES_APP_USER': 'runtime',
            'QUYET_MODEL_DIR': str(model_dir.resolve()), 'AI_QUYET_ENABLED': 'true',
            'AI_QUYET_BASE_URL': 'http://quyet:8091', 'GOOGLE_CLIENT_ID': '123-rehearsal.apps.googleusercontent.com',
            'CLIPROXY_UID': str(os.getuid()), 'CLIPROXY_GID': str(os.getgid()), 'GOOGLE_CLIENT_SECRET': secrets.token_hex(32), 'APP_PAYPAL_CLIENT_ID': 'rehearsal',
            'APP_PAYPAL_CLIENT_SECRET': secrets.token_hex(32), 'SPRING_MAIL_USERNAME': 'rehearsal',
            'SPRING_MAIL_PASSWORD': secrets.token_hex(32), 'CLOUDINARY_CLOUD_NAME': 'rehearsal',
            'CLOUDINARY_API_KEY': 'rehearsal', 'CLOUDINARY_API_SECRET': secrets.token_hex(32),
            'VNPAY_TMN_CODE': 'REHEARSE', 'VNPAY_HASH_SECRET': secrets.token_hex(32),
            'OPEN_METEO_BASE_URL': 'https://api.open-meteo.com', 'OPEN_METEO_MARINE_BASE_URL': 'https://marine-api.open-meteo.com', 'APP_CORS_ALLOWED_ORIGINS': 'https://example.invalid'})
        envfile = temporary / '.env'
        envfile.write_text((root / '.env.example').read_text() + '\n' + '\n'.join(f'{k}={v}' for k, v in values.items()) + '\n')
        envfile.chmod(0o600)
        production.prepare(temporary)
        (temporary / 'frontend').mkdir(exist_ok=True)
        shutil.copyfile(root / 'frontend/nginx.prod.conf', temporary / 'frontend/nginx.prod.conf')
        (temporary / 'frontend/nginx.prod.conf').chmod(0o644)
        # Bind source paths must resolve under the temporary project, never the live checkout.
        config = json.loads(execute(['docker', 'compose', '-p', project, '--project-directory', str(temporary), '--env-file', str(envfile),
                                     '-f', str(root / 'docker-compose.prod.yml'), 'config', '--format', 'json']))
        config = isolate(config)
        for name, image in images.items():
            config['services'][name]['image'] = image
        config['services']['migrate']['image'] = images['backend']
        config.pop('name', None)
        compose_file = temporary / 'compose.json'
        compose_file.write_text(json.dumps(config))
        def compose(*args):
            return execute(['docker', 'compose', '-p', project, '-f', str(compose_file), *args])
        def container(name):
            return compose('ps', '-q', name).decode().strip()
        try:
            compose('up', '-d', '--wait', '--wait-timeout', '300', 'postgres', 'redis', 'rabbitmq', 'quyet', 'cli-proxy-api')
            postgres = container('postgres')
            production.initialize_database(temporary, production.settings(temporary), postgres)
            if baseline:
                config['services']['migrate']['image'] = baseline['images']['backend']
                compose_file.write_text(json.dumps(config))
                compose('run', '--rm', 'migrate')
            else:
                compose('run', '--rm', 'migrate')
            execute(['docker', 'exec', '-i', postgres, 'sh', '-ec',
                     'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'],
                    input=b"CREATE TABLE danasea_release_probe(id integer PRIMARY KEY, marker text); INSERT INTO danasea_release_probe VALUES(1,'preserve');")
            archive = temporary / 'rehearsal.dump'
            with archive.open('wb') as target:
                subprocess.run(['docker', 'exec', postgres, 'sh', '-ec',
                                'pg_dump -Fc --no-owner --no-privileges -U "$POSTGRES_USER" "$POSTGRES_DB"'], stdout=target, stderr=subprocess.PIPE, check=True)
            backup.verify_restore(archive, postgres)
            config['services']['migrate']['image'] = images['backend']
            compose_file.write_text(json.dumps(config))
            compose('run', '--rm', 'migrate')
            marker = execute(['docker', 'exec', postgres, 'sh', '-ec',
                '''psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT marker FROM danasea_release_probe WHERE id=1"'''])
            if marker.strip() != b'preserve':
                raise ValueError('Upgrade lost existing database data')
            compose('up', '-d', '--wait', '--wait-timeout', '300', 'backend', 'frontend')
            address = compose('port', 'frontend', '8080').decode().strip()
            origin = 'http://' + address
            catalog = http_json(origin + '/api/services?size=1')
            if not isinstance(catalog.get('content'), list):
                raise TypeError('Invalid catalog contract')
            acceptance.request(origin + '/api/orders', expected=(401, 403))
            values = acceptance.backend_environment(container('backend'))
            acceptance.validate_decision(acceptance.internal_post(container('backend'), 'http://quyet:8091/v1/decisions',
                values['AI_QUYET_API_KEY'], {'task': 'intent', 'state': {'message': 'Tìm dịch vụ chèo SUP'}}))
            business_probe(origin, postgres)
            result = {'passed': True, 'images': images, 'baseline_sha': baseline['sha'] if baseline else '',
                      'restore_verified': True, 'existing_data_preserved': True, 'booking_order_verified': True,
                      'worker_inference_verified': True, 'provider_scope': 'real provider acceptance runs after production deployment',
                      'seconds': round(time.monotonic() - started, 2)}
            release.atomic_write(output, json.dumps(result, sort_keys=True) + '\n')
            print(json.dumps(result))
        except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError):
            diagnostic = subprocess.run(['docker', 'compose', '-p', project, '-f', str(compose_file), 'logs', '--no-color', '--tail', '150'], capture_output=True, check=False)
            failure_log = Path(str(output) + '.failure.log')
            failure_log.write_bytes(diagnostic.stdout)
            failure_log.chmod(0o600)
            raise
        finally:
            # This project owns every removed resource; no fixed container/network names are used.
            compose('down', '--volumes', '--remove-orphans')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--model-dir', required=True, type=Path)
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--backend-image')
    parser.add_argument('--frontend-image')
    parser.add_argument('--quyet-image')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    try:
        images = release.read(args.manifest)['images'] if args.manifest else {name: getattr(args, name + '_image') for name in release.SERVICES}
        if any(not value for value in images.values()):
            raise ValueError('All application images are required')
        baseline = release.read(args.baseline) if args.baseline else None
        rehearse(args.root.resolve(), args.model_dir, images, baseline, args.output)
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError):
        print('Disposable release rehearsal failed; application data and credentials are suppressed.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
