import importlib.util
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


class DemoWorkflowTest(unittest.TestCase):
    def test_manual_production_operation_shares_deployment_lock(self):
        body = yaml.load((ROOT / '.github/workflows/demo-data.yml').read_text(), Loader=yaml.BaseLoader)
        self.assertEqual({'workflow_dispatch'}, set(body['on']))
        self.assertEqual('cd-production', body['concurrency']['group'])
        self.assertEqual('false', body['concurrency']['cancel-in-progress'])
        job = body['jobs']['demo']
        self.assertEqual('production', job['environment'])
        commands = '\n'.join(step.get('run', '') for step in job['steps'])
        self.assertIn('refs/heads/main', commands)
        self.assertIn('[[ "$DEPLOYED_SHA" == "$GITHUB_SHA" ]]', commands)
        self.assertIn('StrictHostKeyChecking=yes', commands)
        self.assertNotIn('${{ inputs.', commands)
        self.assertNotIn('upload-artifact', str(job))

    def test_summary_omits_secrets_and_rejects_markdown_injection(self):
        spec = importlib.util.spec_from_file_location('demo_report', ROOT / 'scripts/demo_report.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        text = module.render({'status': 'REPLACED', 'password': 'secret',
                              'backup_id': 'bad\n<script>', 'counts': {'rows': 270, 'x\n': 1}})
        self.assertIn('REPLACED', text)
        self.assertIn('270', text)
        for bad in ('secret', '<script>', 'backup_id', 'x\n'):
            self.assertNotIn(bad, text)
