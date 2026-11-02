import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import yaml

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'ai-decision/scripts/install_dependencies.py'


class WorkerDependenciesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('worker_dependencies', SCRIPT)
        cls.installer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.installer)

    def install(self, content, side_effect=None):
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory) / 'requirements.lock.txt'
            lock.write_text(content)
            with patch.object(self.installer.subprocess, 'check_call', side_effect=side_effect) as run:
                self.installer.install(lock)
                return [call.args[0] for call in run.call_args_list]

    def test_cpu_install_uses_the_lock_version_before_installing_all_requirements(self):
        for version in ('2.14.1', '2.15.0'):
            with self.subTest(version=version):
                calls = self.install(f'numpy==2.4.6\ntorch=={version}\ntransformers==5.22.0\n')
                self.assertEqual(calls[0], [sys.executable, '-m', 'pip', 'install', '--no-cache-dir',
                                           '--index-url', 'https://download.pytorch.org/whl/cpu', f'torch=={version}'])
                self.assertEqual(calls[1][:6], [sys.executable, '-m', 'pip', 'install', '--no-cache-dir', '-r'])
                self.assertEqual(Path(calls[1][6]).name, 'requirements.lock.txt')
                self.assertEqual(calls[2][:2], [sys.executable, '-c'])
                self.assertEqual(len(calls), 3)

    def test_missing_unpinned_or_duplicate_torch_is_rejected_before_pip(self):
        for content in ('numpy==2.4.6\n', 'torch>=2.15.0\n',
                        'torch==2.14.1\ntorch==2.15.0\n', 'torch==2.15.0 --extra-index-url https://example.invalid\n'):
            with self.subTest(content=content), tempfile.TemporaryDirectory() as directory:
                lock = Path(directory) / 'requirements.lock.txt'
                lock.write_text(content)
                with patch.object(self.installer.subprocess, 'check_call') as run:
                    with self.assertRaises(ValueError):
                        self.installer.install(lock)
                    run.assert_not_called()

    def test_cpu_index_failure_cannot_fall_back_to_pypi(self):
        failure = subprocess.CalledProcessError(1, 'pip')
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory) / 'requirements.lock.txt'
            lock.write_text('torch==2.15.0\n')
            with patch.object(self.installer.subprocess, 'check_call', side_effect=failure) as run:
                with self.assertRaises(subprocess.CalledProcessError):
                    self.installer.install(lock)
                self.assertEqual(run.call_count, 1)

    def test_post_install_guard_rejects_a_cuda_build_even_if_pip_succeeded(self):
        command = self.install('torch==2.15.0\n')[2]
        for cuda, expected in ((None, 0), ('12.8', 1)):
            with self.subTest(cuda=cuda), tempfile.TemporaryDirectory() as directory:
                (Path(directory) / 'torch.py').write_text(f'from types import SimpleNamespace\nversion = SimpleNamespace(cuda={cuda!r})\n')
                result = subprocess.run(command, cwd=directory, env={**os.environ, 'PYTHONPATH': directory},
                                        capture_output=True, text=True, check=False)
                self.assertEqual(result.returncode, expected, result.stderr)

    def test_docker_and_inference_job_share_the_lock_driven_installer(self):
        dockerfile = (ROOT / 'ai-decision/Dockerfile').read_text()
        self.assertIn('COPY scripts/install_dependencies.py ./install_dependencies.py', dockerfile)
        self.assertIn('RUN python install_dependencies.py config/requirements.lock.txt', dockerfile)
        self.assertNotIn('torch==', dockerfile)
        self.assertIn('!scripts/install_dependencies.py', (ROOT / 'ai-decision/.dockerignore').read_text())
        workflow = yaml.safe_load((ROOT / '.github/workflows/ci.yml').read_text())
        commands = '\n'.join(step.get('run', '') for step in workflow['jobs']['worker']['steps'])
        self.assertIn('python ai-decision/scripts/install_dependencies.py ai-decision/config/requirements.lock.txt', commands)
        self.assertNotIn('torch==', commands)


if __name__ == '__main__':
    unittest.main()
