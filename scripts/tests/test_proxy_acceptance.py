import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


class ProxyAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        script_path = Path(__file__).resolve().parents[1] / "ci" / "run_proxy_acceptance.py"
        spec = importlib.util.spec_from_file_location("run_proxy_acceptance", script_path)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_key_file_missing_or_invalid_is_rejected(self):
        with self.assertRaises(ValueError) as ctx:
            self.module.validate_key_file(Path("/nonexistent/key"))
        self.assertIn("not found", str(ctx.exception))

        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            f.write("too_short")
            f.flush()
            with self.assertRaises(ValueError) as ctx:
                self.module.validate_key_file(Path(f.name))
            self.assertIn("at least 24 characters", str(ctx.exception))

        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            f.write("a" * 28 + "\ninvalid")
            f.flush()
            with self.assertRaises(ValueError) as ctx:
                self.module.validate_key_file(Path(f.name))
            self.assertIn("newline", str(ctx.exception))

    def test_base_url_validation_rejects_credentials_and_invalid_schemes(self):
        with self.assertRaises(ValueError):
            self.module.validate_base_url("ftp://proxy.internal")

        with self.assertRaises(ValueError):
            self.module.validate_base_url("http://user:pass@proxy.internal:8080")

        with patch.dict(os.environ, {"AI_PROXY_ALLOWED_HOSTS": "proxy.internal"}):
            sanitized = self.module.validate_base_url("https://proxy.internal:8080/")
        self.assertEqual(sanitized, "https://proxy.internal:8080")

    def exercise(self, omit=None):
        from scripts.tests.acceptance_fixtures import fake_maven
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {'AI_PROXY_ALLOWED_HOSTS': 'proxy.internal'}):
            root = Path(temp)
            key = root / 'key'
            key.write_text('a' * 32)
            with patch.object(self.module, 'get_backend_revision', return_value='1' * 40), \
                 patch('subprocess.run', side_effect=lambda command, **kw: fake_maven(command, root, 'proxy', omit)):
                self.module.run_proxy_acceptance('https://proxy.internal', key, root / 'out.json', root)
            return json.loads((root / 'out.json').read_text())

    def test_missing_exact_case_is_rejected_by_real_checker(self):
        identity = 'com.danasea.backend.modules.ai.infrastructure.cliproxy.ProxyLiveSmokeTest#realProxyCompactionKeepsOldNonSwimmingConstraintAndAnswersCurrentQuestion'
        with self.assertRaisesRegex(ValueError, 'Required testcase'):
            self.exercise(identity)

    def test_successful_run_uses_measured_cases_and_unknown_upstream(self):
        data = self.exercise()
        self.assertEqual(data['executed_cases'], 3)
        self.assertEqual(data['route'], 'unknown')
        self.assertIsNone(data['actual_provider'])
        self.assertIsNone(data['token_provenance']['cost_usd'])

    def test_allowlist_blocks_arbitrary_target(self):
        with patch.dict(os.environ, {'AI_PROXY_ALLOWED_HOSTS': 'proxy.internal'}):
            with self.assertRaisesRegex(ValueError, 'allowlist'):
                self.module.validate_base_url('https://attacker.invalid')


if __name__ == '__main__':
    unittest.main()
