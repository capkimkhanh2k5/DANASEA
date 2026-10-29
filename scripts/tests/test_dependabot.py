import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_dependabot import load_config, validate


class DependabotConfigTest(unittest.TestCase):
    def setUp(self):
        self.config = {
            'version': 2,
            'multi-ecosystem-groups': {'weekly': {'schedule': {'interval': 'weekly'}, 'open-pull-requests-limit': 1}},
            'updates': [{'package-ecosystem': 'npm', 'directory': '/',
                         'multi-ecosystem-group': 'weekly', 'patterns': ['*']}],
        }

    def test_valid_grouped_config(self):
        validate(self.config)

    def test_per_ecosystem_limit_is_rejected_before_updater_runs(self):
        self.config['updates'][0]['open-pull-requests-limit'] = 1
        with self.assertRaisesRegex(ValueError, 'open-pull-requests-limit.*group'):
            validate(self.config)

    def test_other_group_only_settings_cannot_be_placed_on_update(self):
        for field in ('milestone', 'commit-message', 'target-branch', 'pull-request-branch-name'):
            config = copy.deepcopy(self.config)
            config['updates'][0][field] = 'misplaced'
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate(config)

    def test_unknown_group_is_rejected(self):
        self.config['updates'][0]['multi-ecosystem-group'] = 'missing'
        with self.assertRaisesRegex(ValueError, 'Unknown'):
            validate(self.config)

    def test_missing_patterns_is_rejected(self):
        self.config['updates'][0].pop('patterns')
        with self.assertRaisesRegex(ValueError, 'patterns'):
            validate(self.config)

    def test_duplicate_yaml_keys_are_rejected(self):
        with self.assertRaises(ValueError):
            load_config('version: 2\nversion: 1\nupdates: []\n')

    def test_schedule_time_is_quoted_for_github_yaml_parser(self):
        with self.assertRaisesRegex(ValueError, 'Quote.*time'):
            load_config('version: 2\nupdates:\n- schedule:\n    time: 08:00\n')
        config = load_config('version: 2\nupdates:\n- schedule:\n    time: "08:00"\n')
        self.assertEqual(config['updates'][0]['schedule']['time'], '08:00')

    def standalone(self):
        return {'version': 2, 'updates': [{
            'package-ecosystem': 'docker', 'directories': ['/backend', '/frontend'],
            'schedule': {'interval': 'weekly'}, 'open-pull-requests-limit': 1,
            'groups': {'docker-weekly': {'patterns': ['*']}},
        }]}

    def test_valid_standalone_groups_across_directories(self):
        validate(self.standalone())

    def test_standalone_rejects_multi_ecosystem_patterns(self):
        config = self.standalone()
        config['updates'][0]['patterns'] = ['*']
        with self.assertRaisesRegex(ValueError, 'patterns.*groups'):
            validate(config)

    def test_standalone_requires_schedule_and_unambiguous_directory(self):
        for change in ({'schedule': {'interval': 'invalid'}}, {'schedule': None},
                       {'directory': '/'}, {'directories': []}):
            config = self.standalone()
            config['updates'][0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(config)

    def test_standalone_rejects_invalid_limit_and_group_rules(self):
        for change in ({'open-pull-requests-limit': True},
                       {'groups': []}, {'groups': {'weekly': {'patterns': []}}},
                       {'groups': {'weekly': {'patterns': ['*'], 'applies-to': 'invalid'}}},
                       {'groups': {'weekly': {'patterns': ['*'], 'update-types': ['semver-major']}}}):
            config = self.standalone()
            config['updates'][0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(config)

    def test_invalid_limit_or_schedule_is_rejected(self):
        for change in ({'open-pull-requests-limit': True}, {'schedule': {'interval': 'invalid'}}):
            config = copy.deepcopy(self.config)
            config['multi-ecosystem-groups']['weekly'].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(config)

    def test_repository_config_and_ci_guard_are_wired(self):
        import yaml
        root = Path(__file__).resolve().parents[2]
        validate(load_config((root / '.github/dependabot.yml').read_text()))
        workflow = yaml.safe_load((root / '.github/workflows/ci.yml').read_text())
        quality = workflow['jobs']['quality']
        self.assertNotIn('needs', quality)
        self.assertTrue(any('scripts/check_dependabot.py' in step.get('run', '') for step in quality['steps']))
