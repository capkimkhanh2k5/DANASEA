import importlib.util
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class AcceptanceTest(unittest.TestCase):
    def module(self):
        spec = importlib.util.spec_from_file_location('acceptance', Path(__file__).resolve().parents[1] / 'acceptance.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_spa_html_cannot_satisfy_api_acceptance(self):
        module = self.module()
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.send_header('Content-Type', 'text/html')
                self.end_headers()
                self.wfile.write(b'<html>not an API</html>')
            def log_message(self, *args):
                pass
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with self.assertRaises(ValueError):
                module.request(f'http://127.0.0.1:{server.server_port}/api/services', json_required=True)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_missing_or_empty_provider_completion_is_rejected(self):
        module = self.module()
        for body in [{}, {'choices': []}, {'choices': [{'message': {'content': ''}}]}, {'error': {'message': 'private'}}]:
            with self.assertRaises(ValueError):
                module.validate_completion(body)
        module.validate_completion({'choices': [{'message': {'content': 'OK'}}]})

    def test_production_url_requires_https_and_disallows_credentials(self):
        module = self.module()
        for url in ['http://example.com', 'https://key@example.com', 'https://example.com?key=secret']:
            with self.assertRaises(ValueError):
                module.validate_url(url)
        self.assertEqual(module.validate_url('https://example.com/'), 'https://example.com')

    def test_worker_decision_requires_answers_and_no_warnings(self):
        module = self.module()
        for body in [{'answers': {}}, {'answers': {'intent': True}, 'warnings': ['fallback']}, {}]:
            with self.assertRaises(ValueError):
                module.validate_decision(body)
        module.validate_decision({'answers': {'intent': {'type': 'noul', 'value': True}}, 'warnings': []})

    def test_booking_readiness_rejects_missing_or_invalid_commission_policy(self):
        module = self.module()
        for value in ['', 'NaN', 'Infinity', '-1', '100.01', 'not-configured']:
            with self.assertRaises(ValueError):
                module.validate_commission(value)
        for value in ['0', '0.10', '10.0', '100']:
            module.validate_commission(value)


if __name__ == '__main__':
    unittest.main()
