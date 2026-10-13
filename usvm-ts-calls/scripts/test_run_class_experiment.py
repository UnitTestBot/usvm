import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('campaign', Path(__file__).with_name('run-class-experiment.py'))
campaign = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(campaign)


class CampaignIntegrityTest(unittest.TestCase):
    def test_reserve_replacement_cannot_cross_project_or_stratum(self):
        selected = {'origin': 'selected', 'order': 0, 'identity': 'selected', 'projectId': 'p',
                    'stratum': 'complex', 'classId': 'original'}
        reserves = [dict(selected, origin='reserve', order=0, classId='foreign', projectId='q'),
                    dict(selected, origin='reserve', order=1, classId='simple', stratum='moderate'),
                    dict(selected, origin='reserve', order=2, classId='second'),
                    dict(selected, origin='reserve', order=3, classId='third')]

        chosen, missing = campaign.select_classes({'decisions': [selected, *reserves]},
                                                  {'foreign', 'simple', 'second', 'third'})

        self.assertEqual(['second'], chosen)
        self.assertEqual([], missing)
        chosen, missing = campaign.select_classes({'decisions': [selected, *reserves]}, {'foreign', 'simple'})
        self.assertEqual([], chosen)
        self.assertEqual('selected', missing[0]['identity'])

    def test_same_reserve_cannot_fill_two_selected_slots(self):
        selected = {'origin': 'selected', 'order': 0, 'identity': 'one', 'projectId': 'p', 'stratum': 'complex'}
        other = dict(selected, order=1, identity='two')
        reserve = dict(selected, origin='reserve', classId='reserve')

        chosen, missing = campaign.select_classes({'decisions': [selected, other, reserve]}, {'reserve'})

        self.assertEqual(['reserve'], chosen)
        self.assertEqual('two', missing[0]['identity'])

    def test_saved_plan_rejects_changed_runtime_or_eligibility(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'plan.json'
            campaign.freeze(path, {'classIds': ['first'], 'revision': 'one'})

            with self.assertRaisesRegex(ValueError, 'identity changed'):
                campaign.freeze(path, {'classIds': ['second'], 'revision': 'one'})

    def test_coverage_cannot_admit_a_foreign_statement(self):
        with tempfile.TemporaryDirectory() as temporary:
            prepared = Path(temporary)
            directory = prepared / 'class'
            directory.mkdir()
            (directory / 'class.json').write_text('{}')
            (directory / 'preflight.json').write_text('{}')
            identity = {'classId': 'class', 'budgetMillis': 30_000, 'universeSha256': 'universe',
                        'preparedSha256': campaign.sha(directory / 'class.json'),
                        'preflightSha256': campaign.sha(directory / 'preflight.json')}
            result = {'identity': identity, 'checkpoints': [{'searchBudgetMillis': 30_000,
                      'totalStatements': 1, 'coveredStatementIds': ['foreign'], 'uncoveredStatementIds': []}]}
            universe = {'universeSha256': 'universe', 'statements': [{'id': 'original'}]}

            with self.assertRaisesRegex(ValueError, 'numerator/denominator'):
                campaign.validate_result(result, {'classId': 'class'}, prepared, universe)


if __name__ == '__main__':
    unittest.main()
