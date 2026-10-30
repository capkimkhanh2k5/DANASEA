import importlib.util
import unittest
from pathlib import Path


class RepositoryControlsTest(unittest.TestCase):
    def test_unprotected_or_incomplete_required_checks_are_rejected(self):
        spec = importlib.util.spec_from_file_location('controls', Path(__file__).resolve().parents[1] / 'check_repository_controls.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        environment = {'deployment_branch_policy': {'protected_branches': True}}
        for branch in [{'protected': False}, {'protected': True, 'protection': {'required_status_checks': {'contexts': []}}}]:
            with self.assertRaises(ValueError):
                module.validate_controls(branch, environment)
        branch = {'protected': True, 'protection': {'required_status_checks': {'contexts': ['Required CI gate']}}}
        module.validate_controls(branch, environment)
        with self.assertRaises(ValueError):
            module.validate_controls(branch, {'deployment_branch_policy': None})


if __name__ == '__main__':
    unittest.main()
