import unittest

from deploy_fixture import simulate


class ReleaseDeploymentTest(unittest.TestCase):
    def test_successful_acceptance_advances_state(self):
        result, calls, state = simulate(deploy_fails=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(state, 'a' * 40)
        backups = [index for index, call in enumerate(calls)
                   if call[0] == 'python3' and call[1].endswith('/backup.py')]
        self.assertEqual(len(backups), 1)
        mutation = next(index for index, call in enumerate(calls)
                        if call[0] == 'docker' and ('pull' in call or 'up' in call))
        self.assertLess(backups[0], mutation)

    def test_non_default_app_directory_is_resolved_from_host_policy(self):
        result, _calls, state = simulate(deploy_fails=False, app_dir_in_environment=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(state, 'a' * 40)

    def test_exceeded_outage_budget_does_not_advance_state(self):
        result, _calls, state = simulate(deploy_fails=False, availability_fails=True)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(state, 'b' * 40)

    def test_acceptance_failure_never_advances_successful_state(self):
        result, _calls, state = simulate(deploy_fails=False, acceptance_fails=True)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(state, 'b' * 40)

    def test_failed_backup_never_updates_services(self):
        result, calls, state = simulate(backup_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(state, 'b' * 40)
        self.assertFalse(any(c[0] == 'docker' and ('pull' in c or 'up' in c) for c in calls))

    def test_changed_migration_never_downgrades_applications(self):
        result, calls, state = simulate(migrations_changed=True)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(len([c for c in calls if c[0] == 'docker' and 'up' in c]), 1)
        self.assertEqual(state, 'b' * 40)


if __name__ == '__main__':
    unittest.main()
