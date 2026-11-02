import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))


class RehearsalTest(unittest.TestCase):
    def test_rendering_does_not_inherit_production_configuration(self):
        spec = importlib.util.spec_from_file_location('rehearse', SCRIPTS / 'rehearse.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'docker-compose.prod.yml').write_text('password: ${POSTGRES_PASSWORD}\n')
            with patch.dict(os.environ, {'COMPOSE_PROJECT_NAME': 'danasea', 'POSTGRES_PASSWORD': 'production-value'}):
                with module.rehearsal_environment(root):
                    self.assertNotIn('COMPOSE_PROJECT_NAME', os.environ)
                    self.assertNotIn('POSTGRES_PASSWORD', os.environ)
                self.assertEqual(os.environ['COMPOSE_PROJECT_NAME'], 'danasea')

    def test_isolation_removes_production_names_subnets_and_host_bindings(self):
        spec = importlib.util.spec_from_file_location('rehearse', SCRIPTS / 'rehearse.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        config = {'services': {
            'postgres': {'container_name': 'danasea-postgres', 'ports': ['5433:5432'], 'networks': ['data']},
            'backend': {'container_name': 'danasea-backend', 'environment': {}, 'depends_on': {'cli-proxy-api': {}}, 'networks': {'app': {'ipv4_address': '172.30.10.3'}}},
            'frontend': {'container_name': 'danasea-frontend', 'ports': ['8080:8080'], 'networks': ['app']},
            'quyet': {'environment': {}, 'networks': ['ai']},
            'cli-proxy-api': {'container_name': 'cli-proxy-api'}},
            'networks': {'app': {'name': 'danasea_edge', 'ipam': {'config': [{'subnet': '172.30.10.0/28'}]}}, 'data': {}, 'ai': {}},
            'volumes': {'postgres_data': {'name': 'danasea_postgres_data', 'external': True}}}
        isolated = module.isolate(config)
        for service in isolated['services'].values():
            self.assertNotIn('container_name', service)
            self.assertNotIn('ipv4_address', str(service['networks']))
        self.assertEqual(isolated['networks'], {'app': {}, 'data': {}, 'ai': {}})
        self.assertEqual(isolated['volumes'], {'postgres_data': {}})
        self.assertNotIn('ports', isolated['services']['postgres'])
        self.assertEqual(isolated['services']['frontend']['ports'], ['127.0.0.1::8080'])
        self.assertNotIn('ports', isolated['services']['cli-proxy-api'])
        self.assertEqual(isolated['services']['backend']['environment']['APP_SCHEDULER_ENABLED'], 'false')
        self.assertEqual(isolated['services']['quyet']['environment']['QUYET_CPU_THREADS'], '2')

    def test_business_probe_rejects_missing_or_deviated_coordinates(self):
        spec = importlib.util.spec_from_file_location('rehearse', SCRIPTS / 'rehearse.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        with patch.object(module, 'execute') as mock_exec, \
             patch.object(module, 'http_json') as mock_http:
            mock_exec.return_value = b''
            # Case 1: missing coordinates
            mock_http.side_effect = [
                {}, # register
                {'accessToken': 'token123'}, # login
                {'id': 'svc1', 'name': 'REHEARSAL'} # service detail missing lat/lon
            ]
            with self.assertRaises(ValueError) as ctx:
                module.business_probe('http://127.0.0.1:8080', 'test-postgres')
            self.assertIn('missing coordinate fields', str(ctx.exception))

            # Case 2: coordinates deviated
            mock_http.side_effect = [
                {}, # register
                {'accessToken': 'token123'}, # login
                {'id': 'svc1', 'name': 'REHEARSAL', 'latitude': 10.0, 'longitude': 100.0} # deviated
            ]
            with self.assertRaises(ValueError) as ctx:
                module.business_probe('http://127.0.0.1:8080', 'test-postgres')
            self.assertIn('coordinate readback mismatch', str(ctx.exception))


if __name__ == '__main__':
    unittest.main()
