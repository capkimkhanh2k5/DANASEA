import os
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

WORKFLOW = Path(__file__).resolve().parents[2] / '.github/workflows/ci.yml'


class BackendWorkflowTest(unittest.TestCase):
    def run_build(self, exit_code):
        job = yaml.safe_load(WORKFLOW.read_text())['jobs']['backend']
        step = next(step for step in job['steps'] if step.get('name') == 'Build and run all mandatory integration tests')
        shell = step.get('shell', job.get('defaults', {}).get('run', {}).get('shell'))
        # GitHub adds pipefail for an explicit Bash shell, but not its implicit Linux shell.
        flags = ['--noprofile', '--norc', '-e', '-o', 'pipefail'] if shell == 'bash' else ['-e']
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wrapper = root / 'mvnw'
            wrapper.write_text(f'#!/bin/sh\nprintf "build output\\n"\nexit {exit_code}\n')
            wrapper.chmod(0o755)
            result = subprocess.run(['bash', *flags, '-c', step['run']], cwd=root,
                                    env={'PATH': os.defpath, 'RUNNER_TEMP': directory},
                                    capture_output=True, text=True, check=False)
            return result, (root / 'backend-verify.log').read_text()

    def test_maven_failure_is_preserved_while_its_log_is_saved(self):
        result, log = self.run_build(23)
        self.assertEqual(result.returncode, 23, result.stdout + result.stderr)
        self.assertEqual(log, 'build output\n')

    def test_successful_build_also_saves_its_log(self):
        result, log = self.run_build(0)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(log, 'build output\n')


if __name__ == '__main__':
    unittest.main()
