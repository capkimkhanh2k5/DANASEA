#!/usr/bin/env python3
"""Read-only release acceptance; response bodies and credentials never enter logs."""
import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from decimal import Decimal, InvalidOperation


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def validate_url(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        raise ValueError('Production acceptance needs an HTTPS origin without credentials')
    return url.rstrip('/')


def request(url, expected=(200,), json_required=False):
    try:
        response = urllib.request.build_opener(NoRedirect).open(url, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        if response.status not in expected:
            raise ValueError('Unexpected HTTP status')
        body = response.read(1_048_577)
        if len(body) > 1_048_576:
            raise ValueError('Acceptance response too large')
        if json_required:
            if 'application/json' not in response.headers.get('Content-Type', ''):
                raise ValueError('API returned a non-JSON response')
            return json.loads(body)
        return body


def validate_decision(body):
    if not isinstance(body, dict) or not body.get('answers') or body.get('warnings'):
        raise ValueError('Worker inference failed or returned warnings')


def validate_completion(body):
    try:
        content = body['choices'][0]['message']['content']
        if not isinstance(content, str) or not content.strip() or body.get('error'):
            raise ValueError('Empty provider response')
    except (KeyError, IndexError, TypeError) as error:
        raise ValueError('Provider inference contract failed') from error


def validate_commission(value):
    try:
        rate = Decimal(value)
    except InvalidOperation as error:
        raise ValueError('Booking commission policy is missing or invalid') from error
    if not rate.is_finite() or not Decimal(0) <= rate <= Decimal(100):
        raise ValueError('Booking commission policy is missing or invalid')


def check_booking_configuration():
    query = "SELECT value FROM system_configs WHERE key='PLATFORM_COMMISSION_RATE'"
    result = subprocess.run(['docker', 'exec', 'danasea-postgres', 'sh', '-ec',
        'psql -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"',
        'sh', query], check=True, capture_output=True, text=True, timeout=15)
    validate_commission(result.stdout.strip())


def backend_environment(container):
    result = subprocess.run(['docker', 'inspect', '--format', '{{json .Config.Env}}', container],
                            check=True, capture_output=True, text=True)
    return dict(value.split('=', 1) for value in json.loads(result.stdout) if '=' in value)


def internal_post(container, url, key, body):
    if not key or '\n' in key or '\r' in key:
        raise ValueError('Missing or invalid inference credential')
    settings = '\n'.join(['url = ' + json.dumps(url), 'header = "Content-Type: application/json"',
                          'header = ' + json.dumps('Authorization: Bearer ' + key),
                          'data = ' + json.dumps(json.dumps(body))])
    response = subprocess.run(['docker', 'exec', '-i', container, 'curl', '--fail', '--silent',
                               '--max-time', '90', '--config', '-'], input=settings,
                              capture_output=True, text=True, timeout=100, check=True)
    return json.loads(response.stdout)


def check(url, container='danasea-backend', providers=True):
    url = validate_url(url)
    if b'<html' not in request(url).lower():
        raise ValueError('Frontend HTML unavailable')
    catalog = request(url + '/api/services?page=0&size=1', json_required=True)
    if not isinstance(catalog, dict) or catalog.get('success') is False or not isinstance(catalog.get('content'), list):
        raise ValueError('Catalog response contract failed')
    request(url + '/api/orders', expected=(401, 403))
    request(url + '/actuator/health', expected=(404,))
    check_booking_configuration()
    values = backend_environment(container)
    worker_url = values.get('AI_QUYET_BASE_URL', 'http://quyet:8091').rstrip('/')
    validate_decision(internal_post(container, worker_url + '/v1/decisions', values.get('AI_QUYET_API_KEY'),
                                   {'task': 'intent', 'state': {'message': 'Tôi muốn tìm dịch vụ chèo SUP.'}}))
    if providers:
        proxy = values.get('AI_CLIPROXY_BASE_URL', '').rstrip('/')
        models = {values.get('AI_CLIPROXY_MODEL'), values.get('AI_CLIPROXY_FALLBACK_MODEL')}
        if not proxy or None in models or '' in models:
            raise ValueError('Both provider model routes must be configured')
        for model in sorted(models):
            validate_completion(internal_post(container, proxy + '/chat/completions', values.get('AI_CLIPROXY_API_KEY'),
                                              {'model': model, 'messages': [{'role': 'user', 'content': 'Reply OK.'}],
                                               'max_tokens': 2048, 'temperature': 0}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True)
    parser.add_argument('--container', default='danasea-backend')
    parser.add_argument('--stability-seconds', type=int, default=120)
    args = parser.parse_args()
    try:
        if not 0 <= args.stability_seconds <= 900:
            raise ValueError('Invalid stability window')
        check(args.url, args.container)
        deadline = time.monotonic() + args.stability_seconds
        while time.monotonic() < deadline:
            time.sleep(min(15, max(0, deadline - time.monotonic())))
            check(args.url, args.container, providers=False)
        print(json.dumps({'acceptance': 'passed', 'stability_seconds': args.stability_seconds,
                          'scope': 'public HTTPS, catalog, auth boundary, local inference, both provider routes'}))
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError):
        print('Release acceptance failed; response bodies and credentials are suppressed.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
