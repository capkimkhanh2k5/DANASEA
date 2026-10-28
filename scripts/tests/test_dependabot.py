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
