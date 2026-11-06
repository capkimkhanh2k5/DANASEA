import importlib.util
import json
import socket
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


class JavaWorkerAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        script_path = Path(__file__).resolve().parents[1] / "ci" / "run_java_worker_acceptance.py"
        spec = importlib.util.spec_from_file_location("run_java_worker_acceptance", script_path)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_ensure_key_file_creates_valid_key_and_sets_mode_0600(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            key_file = Path(temp_dir) / "test.key"
            key = self.module.ensure_key_file(key_file)
            self.assertTrue(key_file.is_file())
            self.assertGreaterEqual(len(key), 24)
            # Check file permission 0o600
            self.assertEqual(key_file.stat().st_mode & 0o777, 0o600)

    def test_ensure_key_file_rejects_short_or_newline_keys(self):
        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            f.write("too_short_key")
            f.flush()
            with self.assertRaises(ValueError) as ctx:
                self.module.ensure_key_file(Path(f.name))
            self.assertIn("at least 24 characters", str(ctx.exception))

        with tempfile.NamedTemporaryFile("w+", encoding="utf-8") as f:
            f.write("a" * 30 + "\ninvalid_newline")
            f.flush()
            with self.assertRaises(ValueError) as ctx:
                self.module.ensure_key_file(Path(f.name))
            self.assertIn("newline", str(ctx.exception))

    def test_occupied_port_is_rejected_before_starting_child(self):
        # Bind a listening socket to simulate occupied port
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind(("127.0.0.1", 0))
            s.listen(10)
            port = s.getsockname()[1]
            self.assertTrue(self.module.is_port_in_use(port))

            with patch.object(self.module, "validate_quyet_model"):
                with tempfile.TemporaryDirectory() as temp_dir:
                    key_file = Path(temp_dir) / "service.key"
                    model_dir = Path(temp_dir) / "model"
                    output_file = Path(temp_dir) / "output.json"
                    with self.assertRaises(RuntimeError) as ctx:
                        self.module.run_acceptance(model_dir, key_file, port, output_file)
                    self.assertIn(f"Port {port} is already in use", str(ctx.exception))

    def test_worker_child_process_premature_exit_raises_runtime_error(self):
        mock_proc = MagicMock()
        mock_proc.poll.return_value = 1  # Process terminated
        mock_proc.communicate.return_value = (b"", b"Critical startup error in python worker")
        mock_proc.returncode = 1

        with self.assertRaises(RuntimeError) as ctx:
            self.module.wait_for_health(18091, mock_proc, timeout=5.0)
        self.assertIn("Worker exited prematurely with code 1", str(ctx.exception))

    def test_health_check_wrong_revision_fails(self):
        mock_proc = MagicMock()
        mock_proc.poll.return_value = None

        with patch("urllib.request.urlopen") as mock_urlopen:
            mock_resp = MagicMock()
            mock_resp.status = 200
            mock_resp.read.return_value = json.dumps({
                "status": "UP",
                "model": "chinhnc/Quyet-1.0-Small",
                "revision": "wrong_revision_hash_here"
            }).encode("utf-8")
            mock_urlopen.return_value.__enter__.return_value = mock_resp

            with self.assertRaises(ValueError) as ctx:
                self.module.wait_for_health(18091, mock_proc, timeout=2.0)
            self.assertIn("unexpected status/revision", str(ctx.exception))

    def test_acceptance_flow_uses_real_report_checker_and_observations(self):
        from scripts.tests.acceptance_fixtures import fake_maven
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            proc = MagicMock()
            proc.poll.return_value = None
            with patch.object(self.module, 'validate_quyet_model'), \
                 patch.object(self.module, 'is_port_in_use', return_value=False), \
                 patch.object(self.module, 'get_backend_revision', return_value='1' * 40), \
                 patch.object(self.module, 'wait_for_health', return_value={'model': 'chinhnc/Quyet-1.0-Small'}), \
                 patch('subprocess.Popen', return_value=proc), \
                 patch('subprocess.run', side_effect=lambda command, **kw: fake_maven(command, root, 'worker')):
                self.assertEqual(self.module.run_acceptance(root / 'model', root / 'key', 18091, root / 'out.json', root), 0)
            data = json.loads((root / 'out.json').read_text())
            self.assertEqual({case['case_name'] for case in data['case_outcomes']},
                             {'intent', 'service_category', 'review', 'moderation', 'relevance', 'complaint', 'risk'})
            self.assertIsNone(data['business_evaluation']['policy_boundary_respected'])
            proc.terminate.assert_called_once()

    def test_worker_key_cannot_be_created_inside_model_cache(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(self.module, 'validate_quyet_model'):
            root = Path(temp)
            with self.assertRaisesRegex(ValueError, 'outside'):
                self.module.run_acceptance(root, root / 'key', 18091, root.parent / 'unused.json')


if __name__ == '__main__':
    unittest.main()
