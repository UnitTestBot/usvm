import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('smoke', Path(__file__).with_name('run-class-start-smoke.py'))
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)


class StartupIntegrityTest(unittest.TestCase):
    def case(self, directory):
        source = directory / 'Target.ts'
        source.write_text('export class Target {}')
        return {'classId': 'target', 'identity': 'fixture@Target', 'sourceRoot': str(directory),
                'inputSha256': {'Target.ts': smoke.sha(source)}, 'acquisitionComplete': False}

    def test_synthetic_steps_and_incomplete_acquisition_cannot_qualify(self):
        case = {'classId': 'target', 'identity': 'fixture@Target', 'acquisitionComplete': False}
        result = smoke.failure_result(case, 'frozen', 'STOPPED', 'none')
        result.update(initialStates=1, targetClassSteps=2, syntheticOrUnmappedTargetClassSteps=2, started=True)

        with self.assertRaisesRegex(ValueError, 'original target-class interpreter entry'):
            smoke.validate_result(result, case, 'frozen')
        result.update(originalTargetStatementsEntered=1, targetStatementEntered=True, qualifyingStarted=True)
        with self.assertRaisesRegex(ValueError, 'Incomplete acquisition'):
            smoke.validate_result(result, case, 'frozen')

    def test_source_drift_is_checked_before_reuse(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            case = self.case(directory)
            smoke.verify_case(case)

            (directory / 'Target.ts').write_text('export class Target { value = 1; }')

            with self.assertRaisesRegex(ValueError, 'changed'):
                smoke.verify_case(case)

    def test_summary_retains_failed_and_pending_rows(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            cases = [{'classId': 'failed', 'identity': 'one'}, {'classId': 'pending', 'identity': 'two'}]
            smoke.save(directory / 'classes/failed/result.json', smoke.failure_result(cases[0], 'frozen', 'FRONTEND', 'unsupported'))

            result = smoke.summarize({'classes': cases, 'expectedClassCount': 2}, directory, 'frozen')

            self.assertEqual(2, result['classCount'])
            self.assertEqual(0, result['qualifyingStartedCount'])
            self.assertFalse(result['complete'])
            self.assertEqual(['FRONTEND', 'PENDING'], [row['stage'] for row in result['results']])

    def test_changed_manifest_cannot_replace_saved_attempt(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'identity.json'
            smoke.freeze(path, {'manifestSha256': 'one'})

            with self.assertRaisesRegex(ValueError, 'changed'):
                smoke.freeze(path, {'manifestSha256': 'two'})

    def test_first_residual_call_can_start_without_successful_transition(self):
        case = {'classId': 'target', 'identity': 'fixture@Target', 'acquisitionComplete': False}
        result = smoke.failure_result(case, 'frozen', 'STOPPED', 'unsupported')
        result.update(initialStates=1, originalTargetStatementsEntered=1, targetStatementEntered=True,
                      started=True, targetSuccessfulTransitions=0, startupStatus='STARTED_WITH_LIMITATION')

        smoke.validate_result(result, case, 'frozen')

    def test_acquisition_complete_needs_external_frozen_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            case = self.case(directory)
            case['acquisitionComplete'] = True

            with self.assertRaisesRegex(ValueError, 'frozen receipt'):
                smoke.verify_case(case, directory)

    def test_durable_entry_survives_child_timeout(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            case = {'classId': 'target', 'identity': 'fixture@Target', 'acquisitionComplete': False}
            progress = smoke.failure_result(case, 'frozen', 'SOURCE_CLASS_ENTERED', 'none')
            progress.update(initialStates=1, originalTargetStatementsEntered=1,
                            targetStatementEntered=True, started=True)
            smoke.save(directory / 'progress.json', progress)

            result = smoke.child_result(directory, {'timedOut': True}, case, 'frozen')

            self.assertTrue(result['started'])
            self.assertFalse(result['qualifyingStarted'])
            self.assertTrue(result['timedOut'])
            self.assertFalse(result['engineFailed'])
            self.assertEqual('STARTED_WITH_LIMITATION', result['startupStatus'])


if __name__ == '__main__':
    unittest.main()
