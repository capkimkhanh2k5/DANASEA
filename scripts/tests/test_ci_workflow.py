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


class SecurityScanMetadataWorkflowTest(unittest.TestCase):
    def run_scan(self, resolve_exit=0):
        steps = yaml.safe_load(WORKFLOW.read_text())['jobs']['quality']['steps']
        selected = [step for step in steps if step.get('name') in (
            'Resolve backend metadata before dependency scanning', 'Scan dependencies and committed secrets')]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'backend').mkdir()
            (root / 'ai-decision/config').mkdir(parents=True)
            (root / 'ai-decision/config/requirements.lock.txt').write_text('torch==2.14.1\n')
            binary = root / 'bin'
            binary.mkdir()
            wrapper = root / 'backend/mvnw'
            wrapper.write_text('#!/bin/sh\ncase "$*" in *dependency:resolve*) ;; *) exit 19;; esac\n'
                               f'exit_code={resolve_exit}\n'
                               'test "$exit_code" -eq 0 || exit "$exit_code"\n'
                               'touch "$METADATA_READY_FILE"\n')
            wrapper.chmod(0o755)
            trivy = binary / 'trivy'
            trivy.write_text('#!/bin/sh\ntest -f "$METADATA_READY_FILE" || exit 42\n'
                             'printf "%s\\n" "$*" >> "$TRIVY_CALLS_FILE"\n')
            trivy.chmod(0o755)
            env = {**os.environ, 'PATH': str(binary) + os.pathsep + os.defpath,
                   'RUNNER_TEMP': directory, 'METADATA_READY_FILE': str(root / 'metadata-ready'),
                   'TRIVY_CALLS_FILE': str(root / 'trivy-calls')}
            for step in selected:
                result = subprocess.run(['bash', '-e', '-c', step['run']],
                                        cwd=root / step.get('working-directory', ''), env=env,
                                        capture_output=True, text=True, check=False)
                if result.returncode:
                    break
            calls = (root / 'trivy-calls').read_text().splitlines() if (root / 'trivy-calls').exists() else []
            return result, calls

    def test_maven_metadata_is_resolved_before_both_security_scans(self):
        result, calls = self.run_scan()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(calls), 2)
        self.assertIn('--scanners vuln,secret', calls[1])
        for command in calls:
            self.assertIn('--severity HIGH,CRITICAL', command)
            self.assertIn('--exit-code 1', command)

    def test_metadata_resolution_failure_blocks_scanning_without_hiding_failure(self):
        result, calls = self.run_scan(resolve_exit=23)
        self.assertEqual(result.returncode, 23, result.stderr)
        self.assertEqual(calls, [])


if __name__ == '__main__':
    unittest.main()
