"""Run pinned CPU inference through the HTTP handler; no provider requests or mutations.

Run from ai-decision with its Python environment. This checks transport and
response contracts, not classification accuracy or production server capacity.
"""
import json
import secrets
import time

from app.quyet_service import create_app
from fastapi.testclient import TestClient

CASES = {
    'intent': {'message': 'Tôi muốn tìm dịch vụ chèo SUP.'},
    'service_category': {'content': 'Dịch vụ chèo SUP có hướng dẫn viên.'},
    'relevance': {'context': {'query': 'chèo SUP', 'interests': ['SUP']},
                  'service': {'name': 'Chèo SUP', 'description': 'Trải nghiệm chèo SUP có hướng dẫn.'}},
    'review': {'review': 'Hướng dẫn viên nhiệt tình, hỗ trợ rất tốt.'},
    'moderation': {'content': 'Dịch vụ chèo SUP có hướng dẫn viên.'},
    'complaint': {'complaint': 'Tôi đã thanh toán nhưng đơn chưa xác nhận.'},
    'risk': {'risk_signals': [], 'complaint': '', 'payment_status': 'PAID'},
}


def main():
    key = secrets.token_urlsafe(32)
    started = time.perf_counter()
    results = []
    with TestClient(create_app(api_key=key)) as client:
        startup = time.perf_counter() - started
        health = client.get('/health')
        health.raise_for_status()
        for task, state in CASES.items():
            response = client.post('/v1/decisions', headers={'Authorization': 'Bearer ' + key},
                                   json={'task': task, 'state': state})
            body = response.json()
            results.append({'task': task, 'status': response.status_code,
                            'latencyMs': body.get('latencyMs'), 'warnings': body.get('warnings'),
                            'answers': body.get('answers'), 'detail': body.get('detail')})
    print(json.dumps({'startupSeconds': round(startup, 3), 'health': health.json(),
                      'results': results, 'scope': 'local CPU HTTP handler; no semantic accuracy grading'},
                     ensure_ascii=False, indent=2))
    return 0 if all(row['status'] == 200 and not row['warnings'] for row in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
