import unittest

from deploy_fixture import simulate


class ReleaseDeploymentTest(unittest.TestCase):
    def test_dependencies_are_ready_before_replacing_public_applications(self):
        result, calls, _state = simulate(deploy_fails=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        updates = [c for c in calls if c[0] == 'docker' and 'up' in c]
        self.assertEqual([c[-1] for c in updates], ['cli-proxy-api', 'quyet', 'backend', 'frontend'])
        self.assertTrue(all('--wait' in c for c in updates))
        self.assertTrue(all('--no-deps' in c for c in updates[1:]))
        migration = next(i for i, c in enumerate(calls) if c[0] == 'docker' and 'run' in c and c[-1] == 'migrate')
        backend = next(i for i, c in enumerate(calls) if c[0] == 'docker' and 'up' in c and c[-1] == 'backend')
        self.assertLess(migration, backend)

    def test_failed_rollout_retains_availability_evidence(self):
        result, _calls, _state = simulate(deploy_fails=False, availability_fails=True)
        self.assertEqual(result.returncode, 1)
        self.assertIn('Availability evidence retained:', result.stdout)

    def test_monitor_crash_still_attempts_compatible_rollback(self):
        result, calls, state = simulate(deploy_fails=False, availability_missing=True)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(state, 'b' * 40)
        updates = [c for c in calls if c[0] == 'docker' and 'up' in c]
        self.assertIn('Rolling back applications', result.stdout)
        self.assertEqual(updates[-1][-3:], ['quyet', 'backend', 'frontend'])

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
