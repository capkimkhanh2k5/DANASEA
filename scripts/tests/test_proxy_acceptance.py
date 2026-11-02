import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


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

        sanitized = self.module.validate_base_url("https://proxy.internal:8080/")
        self.assertEqual(sanitized, "https://proxy.internal:8080")

    def test_fewer_than_three_executions_raises_runtime_error(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            key_file = temp_path / "proxy.key"
            key_file.write_text("a" * 32, encoding="utf-8")
            output_file = temp_path / "out.json"

            with patch("subprocess.run") as mock_run, \
                 patch.object(self.module, "check_test_reports", return_value={"executed": 2, "allowed_skipped": 0}):
                mock_run.return_value = MagicMock(returncode=0)

                with self.assertRaises(RuntimeError) as ctx:
                    self.module.run_proxy_acceptance(
                        base_url="https://proxy.internal",
                        key_file=key_file,
                        output_file=output_file,
                        backend_dir=temp_path
                    )
                self.assertIn("Expected at least 3 proxy test executions", str(ctx.exception))

    def test_successful_run_creates_evidence_without_key_leakage(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            key_file = temp_path / "proxy.key"
            secret_key = "my-secret-cliproxy-key-12345678"
            key_file.write_text(secret_key, encoding="utf-8")
            output_file = temp_path / "evidence.json"

            with patch("subprocess.run") as mock_run, \
                 patch.object(self.module, "check_test_reports", return_value={"executed": 3, "allowed_skipped": 0}):
                mock_run.return_value = MagicMock(returncode=0)

                exit_code = self.module.run_proxy_acceptance(
                    base_url="https://proxy.internal",
                    key_file=key_file,
                    output_file=output_file,
                    backend_dir=temp_path
                )
                self.assertEqual(exit_code, 0)
                self.assertTrue(output_file.is_file())
                content = output_file.read_text(encoding="utf-8")
                self.assertNotIn(secret_key, content)
                data = json.loads(content)
                self.assertEqual(data["total_executions"], 3)
                self.assertEqual(data["status"], "PASSED")


if __name__ == "__main__":
    unittest.main()
