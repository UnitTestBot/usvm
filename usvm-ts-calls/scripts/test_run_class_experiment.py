import importlib.util
import json
import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('campaign', Path(__file__).with_name('run-class-experiment.py'))
campaign = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(campaign)


class CampaignIntegrityTest(unittest.TestCase):
    def write_process_tree(self, directory):
        heartbeat = directory / 'heartbeat'
        child = directory / 'grandchild.py'
        child.write_text('import sys,time\nfrom pathlib import Path\n'
                         'path=Path(sys.argv[1])\nwhile True:\n'
                         '    with path.open("a") as stream: stream.write("alive\\n")\n'
                         '    time.sleep(0.02)\n')
        parent = directory / 'parent.py'
        parent.write_text('import subprocess,sys,time\n'
                          'subprocess.Popen([sys.executable,sys.argv[1],sys.argv[2]])\n'
                          'time.sleep(60)\n')
        return parent, child, heartbeat

    def assert_heartbeat_stopped(self, heartbeat):
        self.assertTrue(heartbeat.exists())
        after_cleanup = heartbeat.read_bytes()

        time.sleep(0.15)

        self.assertEqual(after_cleanup, heartbeat.read_bytes())

    @unittest.skipUnless(os.name == 'posix', 'Owned process groups are a POSIX contract')
    def test_cell_timeout_stops_grandchildren_and_records_log_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            parent, child, heartbeat = self.write_process_tree(directory)

            outcome = campaign.invoke(Path(sys.executable), [parent, child, heartbeat],
                                      directory / 'cell.log', timeout=0.5)

            self.assertIsNone(outcome['exitCode'])
            self.assertTrue(outcome['processGroupKilled'])
            self.assertEqual(outcome['pid'], outcome['processGroupId'])
            self.assertEqual(campaign.sha(directory / 'cell.log'), outcome['logSha256'])
            self.assert_heartbeat_stopped(heartbeat)

    @unittest.skipUnless(os.name == 'posix', 'Owned process groups are a POSIX contract')
    def test_terminating_campaign_stops_active_cell_process_group(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            parent, child, heartbeat = self.write_process_tree(directory)
            wrapper = directory / 'campaign.py'
            wrapper.write_text('import importlib.util,sys\nfrom pathlib import Path\n'
                               'spec=importlib.util.spec_from_file_location("campaign",sys.argv[1])\n'
                               'module=importlib.util.module_from_spec(spec)\nspec.loader.exec_module(module)\n'
                               'module.invoke(Path(sys.executable),sys.argv[2:5],Path(sys.argv[5]),timeout=60)\n')
            process = subprocess.Popen([sys.executable, str(wrapper), str(Path(campaign.__file__).resolve()),
                                        str(parent), str(child), str(heartbeat), str(directory / 'cell.log')])
            try:
                deadline = time.monotonic() + 5
                while not heartbeat.exists() and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue(heartbeat.exists())

                process.send_signal(signal.SIGTERM)
                code = process.wait(timeout=5)

                self.assertEqual(128 + signal.SIGTERM, code)
                self.assert_heartbeat_stopped(heartbeat)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()

    def make_phase_fixture(self, output):
        prepared = output / 'prepared'
        directory = prepared / 'class'
        directory.mkdir(parents=True)
        manifest = {'experimentId': 'phase-test', 'toolRevision': 'revision',
                    'schemaVersion': 2, 'coveragePolicy': campaign.COVERAGE_POLICY,
                    'suspendedCoveragePolicy': campaign.SUSPENDED_COVERAGE_POLICY,
                    'nativeFrontendRevision': 'frontend', 'budgetsMillis': list(campaign.BUDGETS),
                    'seeds': [17], 'classes': [{'classId': 'class'}]}
        universe = {'universeSha256': 'universe', 'statements': [{'id': 'original'}]}
        campaign.freeze(prepared / 'manifest.json', manifest)
        campaign.freeze(directory / 'class.json', {'instrumentation': {'universe': universe}})
        campaign.freeze(directory / 'preflight.json', {
            'admissionVersion': 1, 'classId': 'class', 'eligible': True,
            'primaryInferenceEligible': True, 'diagnosticReady': False,
            'nativeSmoke': {'status': 'VERIFIED'}, 'sourceAdmissionFailures': [],
            'nativeInfrastructureFailures': [], 'missingRequiredScenarios': ['slow'],
            'reasons': ['Symbolic pilot reached the time budget'],
        })
        plan = {'classIds': ['class'], 'seeds': [17], 'budgetsMillis': list(campaign.BUDGETS),
                'missingStrata': []}

        return prepared, manifest, plan

    def write_phase_result(self, output, prepared, manifest, budget, profile):
        directory = campaign.cell_directory(output, 'class', 17, budget, profile)
        identity = {'experimentId': manifest['experimentId'], 'toolRevision': manifest['toolRevision'],
                    'schemaVersion': 2, 'coveragePolicy': campaign.COVERAGE_POLICY,
                    'suspendedCoveragePolicy': campaign.SUSPENDED_COVERAGE_POLICY,
                    'nativeFrontendRevision': manifest['nativeFrontendRevision'],
                    'manifestSha256': campaign.sha(prepared / 'manifest.json'),
                    'preparedSha256': campaign.sha(prepared / 'class' / 'class.json'),
                    'preflightSha256': campaign.sha(prepared / 'class' / 'preflight.json'),
                    'universeSha256': 'universe', 'classId': 'class', 'seed': 17,
                    'budgetMillis': budget, 'profile': profile}
        result = {'identity': identity, 'nativeInfrastructureFailures': [],
                  'search': {'search': {'status': 'TIMEOUT', 'searchElapsedMillis': budget,
                             'candidates': [], 'suspendedExtractionElapsedMillis': 0, 'frontierFlushElapsedMillis': 0,
                             'preparationElapsedMillis': 0, 'machineSetupElapsedMillis': 0,
                             'runtimeLimited': False, 'engineFailed': False, 'candidateCapReached': False}},
                  'checkpoints': [{'searchBudgetMillis': budget, 'totalStatements': 1,
                                   'coveredStatementIds': [], 'uncoveredStatementIds': ['original'],
                                   'strictCoveredStatementIds': [], 'strictUncoveredStatementIds': ['original'],
                                   'strictAcceptedCandidates': 0, 'strictRejectedCandidates': 0,
                                   'strictOracleNotApplicable': 0, 'emittedCandidates': 0,
                                   'suspendedCandidates': 0, 'postStopSuspendedCandidates': 0,
                                   'includesSuspendedExtraction': True,
                                   'acceptedCandidates': 0, 'rejectedCandidates': 0}],
                  'replayElapsedMillis': 0, 'replayErrors': 0, 'oracleRejections': 0, 'nativeCoverageRejections': 0}

        campaign.freeze(directory / 'result.json', result)

    def test_phase_schedule_preserves_full_protocol_and_profile_rotation(self):
        plan = {'classIds': ['first', 'second'], 'seeds': [17, 29],
                'budgetsMillis': list(campaign.BUDGETS)}
        frozen = json.dumps(plan, sort_keys=True)

        full = list(campaign.scheduled_cells(plan))
        short = list(campaign.scheduled_cells(plan, budgets=(30_000,)))
        long = list(campaign.scheduled_cells(plan, budgets=(120_000,)))

        self.assertEqual(full, short + long)
        self.assertEqual(16, len(short))
        self.assertEqual(16, len(long))
        self.assertEqual(30_000, short[0][0])
        self.assertEqual(campaign.PROFILES[1], short[0][3])
        self.assertEqual(frozen, json.dumps(plan, sort_keys=True))
        with self.assertRaisesRegex(ValueError, 'full frozen'):
            list(campaign.scheduled_cells(plan, budgets=(60_000,)))

    def test_short_phase_retains_timeouts_and_reports_pending_long_phase(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, manifest, plan = self.make_phase_fixture(output)
            for profile in campaign.PROFILES:
                self.write_phase_result(output, prepared, manifest, budget=30_000, profile=profile)

            summary = campaign.summarize(output, prepared, plan, scheduled_budgets=(30_000,))

            self.assertEqual(4, len(summary['rows']))
            self.assertTrue(all(row['status'] == 'TIMEOUT' for row in summary['rows']))
            self.assertEqual([], summary['incompleteOrInvalidCells'])
            self.assertEqual(4, len(summary['pendingOtherPhaseCells']))
            self.assertEqual({120_000}, {cell['budgetMillis'] for cell in summary['pendingOtherPhaseCells']})
            self.assertFalse(summary['complete'])

            for profile in campaign.PROFILES:
                self.write_phase_result(output, prepared, manifest, budget=120_000, profile=profile)
            complete = campaign.summarize(output, prepared, plan)

            self.assertEqual(8, len(complete['rows']))
            self.assertEqual(2, complete['completePairedGroups'])
            self.assertEqual([], complete['pendingOtherPhaseCells'])
            self.assertTrue(complete['complete'])

    def test_scheduled_missing_cell_is_a_failure_not_a_pending_other_phase(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, manifest, plan = self.make_phase_fixture(output)
            for profile in campaign.PROFILES[:-1]:
                self.write_phase_result(output, prepared, manifest, budget=30_000, profile=profile)

            summary = campaign.summarize(output, prepared, plan, scheduled_budgets=(30_000,))

            self.assertEqual(1, len(summary['incompleteOrInvalidCells']))
            self.assertEqual(30_000, summary['incompleteOrInvalidCells'][0]['budgetMillis'])
            self.assertEqual(4, len(summary['pendingOtherPhaseCells']))
            self.assertFalse(summary['complete'])

    def test_phase_resume_keeps_full_frozen_identity_and_launches_only_requested_budget(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, manifest, _ = self.make_phase_fixture(output)
            manifest_path = output / 'input-manifest.json'
            campaign.freeze(manifest_path, manifest)

            def fake_invoke(calls, arguments, log, timeout):
                self.assertEqual('class-cell', arguments[0])
                self.write_phase_result(output, prepared, manifest, budget=arguments[5], profile=arguments[3])
                return {'exitCode': 0}

            base_args = ['campaign', '--calls', str(output / 'calls'), '--manifest', str(manifest_path),
                         '--output', str(output)]
            with patch('sys.argv', [*base_args, '--budget-ms', '30000']), patch.object(
                    campaign, 'invoke', side_effect=fake_invoke) as short_calls, patch('builtins.print'):
                campaign.main()
            frozen_input = (output / 'campaign-input.json').read_bytes()
            frozen_plan = (output / 'run-plan.json').read_bytes()

            with patch('sys.argv', [*base_args, '--budget-ms', '120000']), patch.object(
                    campaign, 'invoke', side_effect=fake_invoke) as long_calls, patch('builtins.print'):
                campaign.main()

            self.assertEqual(4, short_calls.call_count)
            self.assertEqual({30_000}, {call.args[1][5] for call in short_calls.call_args_list})
            self.assertEqual(4, long_calls.call_count)
            self.assertEqual({120_000}, {call.args[1][5] for call in long_calls.call_args_list})
            self.assertEqual(frozen_input, (output / 'campaign-input.json').read_bytes())
            self.assertEqual(frozen_plan, (output / 'run-plan.json').read_bytes())
            self.assertEqual(list(campaign.BUDGETS), campaign.read(output / 'run-plan.json')['budgetsMillis'])
            self.assertTrue(campaign.read(output / 'summary.json')['complete'])

    def test_source_rejected_preflight_may_omit_null_native_smoke(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, manifest, _ = self.make_phase_fixture(output)
            manifest_path = output / 'input-manifest.json'
            campaign.freeze(manifest_path, manifest)
            preflight_path = prepared / 'class' / 'preflight.json'
            preflight = campaign.read(preflight_path)
            preflight.pop('nativeSmoke')
            preflight.update(eligible=False, primaryInferenceEligible=False,
                             sourceAdmissionFailures=['TOP_LEVEL_INITIALIZATION_UNSUPPORTED: offset 0'])
            preflight_path.write_text(json.dumps(preflight) + '\n')
            args = ['campaign', '--calls', str(output / 'calls'), '--manifest', str(manifest_path),
                    '--output', str(output), '--preflight-only']

            with patch('sys.argv', args), patch.object(campaign, 'invoke') as invoke, patch('builtins.print'):
                campaign.main()

            invoke.assert_not_called()
            eligibility = campaign.read(output / 'eligibility.json')
            self.assertIsNone(eligibility[0]['nativeSmoke'])
            self.assertFalse(eligibility[0]['primaryInferenceEligible'])
            self.assertEqual([], campaign.read(output / 'run-plan.json')['classIds'])
            self.assertFalse((output / 'summary.json').exists())

    def test_saved_corruption_in_later_unselected_class_blocks_any_resume_launch(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            _, manifest, _ = self.make_phase_fixture(output)
            manifest_path = output / 'input-manifest.json'
            campaign.freeze(manifest_path, manifest)
            later = output / 'cells' / 'unselected' / '29' / '120000' / 'FROZEN_FRESH' / 'result.json'
            campaign.freeze(later, {'identity': {'classId': 'unselected'},
                                   'nativeInfrastructureFailures': ['Native completion changed']})
            args = ['campaign', '--calls', str(output / 'calls'), '--manifest', str(manifest_path),
                    '--output', str(output), '--budget-ms', '30000']

            with patch('sys.argv', args), patch.object(campaign, 'invoke') as invoke:
                with self.assertRaisesRegex(RuntimeError, 'entire acquisition attempt'):
                    campaign.main()

            invoke.assert_not_called()
            self.assertFalse((output / 'run-plan.json').exists())
            self.assertEqual('unselected', campaign.read(output / 'acquisition-health-failure.json')['failures'][0]['classId'])

    def test_summarize_refuses_corruption_outside_plan_before_writing_results(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, _, plan = self.make_phase_fixture(output)
            campaign.freeze(prepared / 'excluded' / 'preflight.json', {
                'classId': 'excluded', 'nativeInfrastructureFailures': ['Native receiver changed'],
            })

            with self.assertRaisesRegex(RuntimeError, 'entire acquisition attempt'):
                campaign.summarize(output, prepared, plan, scheduled_budgets=(30_000,))

            self.assertFalse((output / 'summary.json').exists())
            self.assertFalse((output / 'RESULTS.md').exists())

    def test_sticky_health_receipt_blocks_summarize_only_before_any_launch(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            campaign.freeze(output / 'acquisition-health-failure.json', {'stage': 'previous', 'failures': []})
            args = ['campaign', '--calls', str(output / 'calls'), '--manifest', str(output / 'missing.json'),
                    '--output', str(output), '--summarize-only']

            with patch('sys.argv', args), patch.object(campaign, 'invoke') as invoke:
                with self.assertRaisesRegex(RuntimeError, 'Saved native acquisition failure'):
                    campaign.main()

            invoke.assert_not_called()
            self.assertFalse((output / 'summary.json').exists())
            self.assertFalse((output / 'campaign-input.json').exists())

    def test_interrupted_native_replay_mismatch_blocks_resume_before_result(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, manifest, _ = self.make_phase_fixture(output)
            manifest_path = output / 'input-manifest.json'
            campaign.freeze(manifest_path, manifest)
            journal = output / 'cells' / 'class' / '17' / '120000' / 'FROZEN_FRESH' / 'replays.jsonl'
            journal.parent.mkdir(parents=True)
            original = {'inputs': [], 'observation': {'completion': 'RETURNED', 'representable': True,
                        'values': {'state': {'kind': 'boolean', 'value': True}}}}
            instrumented = {'inputs': [], 'observation': {'completion': 'RETURNED', 'representable': True,
                            'values': {'state': {'kind': 'boolean', 'value': False}}},
                            'coveredStatementKeys': ['original']}
            event = {'index': 0, 'original': original, 'instrumented': instrumented,
                     'oracle': {'accepted': False}}
            journal.write_text(json.dumps(event) + '\n')
            args = ['campaign', '--calls', str(output / 'calls'), '--manifest', str(manifest_path),
                    '--output', str(output), '--budget-ms', '30000']

            with patch('sys.argv', args), patch.object(campaign, 'invoke') as invoke:
                with self.assertRaisesRegex(RuntimeError, 'entire acquisition attempt'):
                    campaign.main()

            invoke.assert_not_called()
            self.assertFalse((journal.parent / 'result.json').exists())
            self.assertEqual('class', campaign.read(output / 'acquisition-health-failure.json')['failures'][0]['classId'])
            with self.assertRaisesRegex(RuntimeError, 'Saved native acquisition failure'):
                campaign.summarize(output, prepared, {'classIds': []})

    def test_interrupted_foreign_statement_is_corruption_even_without_original_replay(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, _, plan = self.make_phase_fixture(output)
            journal = prepared / 'class' / 'preflight' / 'replays.jsonl'
            journal.parent.mkdir()
            event = {'index': 0, 'instrumented': {'coveredStatementKeys': ['foreign']},
                     'oracle': {'accepted': False}, 'error': 'original timed out'}
            journal.write_text(json.dumps(event) + '\n')

            with self.assertRaisesRegex(RuntimeError, 'entire acquisition attempt'):
                campaign.summarize(output, prepared, plan)

            self.assertFalse((output / 'summary.json').exists())
            receipt = campaign.read(output / 'acquisition-health-failure.json')
            self.assertIn('foreign statement IDs', receipt['failures'][0]['nativeInfrastructureFailures'][0])

    def test_lawful_incomplete_or_missing_native_replay_does_not_prove_corruption(self):
        original = {'inputs': [], 'observation': {'completion': 'THREW', 'representable': False,
                    'values': {}, 'diagnostic': 'original exception'}}
        instrumented = {'inputs': [], 'observation': {'completion': 'THREW', 'representable': False,
                        'values': {}, 'diagnostic': 'instrumented exception'}, 'coveredStatementKeys': ['original']}

        self.assertIsNone(campaign.native_replay_failure(
            {'original': original, 'instrumented': instrumented}, statement_ids={'original'}))
        self.assertIsNone(campaign.native_replay_failure(
            {'instrumented': instrumented, 'error': 'original timed out'}, statement_ids={'original'}))

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

    def test_slow_or_incomplete_pilot_does_not_select_the_primary_population(self):
        slow = {'admissionVersion': 1, 'eligible': True, 'primaryInferenceEligible': True,
                'diagnosticReady': False, 'missingRequiredScenarios': ['one', 'two'],
                'search': {'status': 'TIMEOUT'}}
        broken = dict(slow, primaryInferenceEligible=False,
                      correctnessFrontierFailures=['Known lowering defect'])
        selected = {'origin': 'selected', 'order': 0, 'identity': 'slow', 'projectId': 'p',
                    'stratum': 'complex', 'classId': 'slow'}

        admitted = {class_id for class_id, preflight in [('slow', slow), ('broken', broken)]
                    if campaign.admits_primary_comparison(preflight)}
        chosen, missing = campaign.select_classes({'decisions': [selected]}, admitted)

        self.assertEqual({'slow'}, admitted)
        self.assertEqual(['slow'], chosen)
        self.assertEqual([], missing)

    def test_native_corruption_stops_the_whole_attempt_instead_of_dropping_one_class(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            slow = {'classId': 'slow', 'missingRequiredScenarios': ['method'],
                    'nativeInfrastructureFailures': [], 'replayErrors': 1}
            broken = {'classId': 'broken', 'nativeInfrastructureFailures': ['Receiver state changed']}

            campaign.require_native_acquisition_health(output, 'preflight', [slow])
            self.assertFalse((output / 'acquisition-health-failure.json').exists())

            with self.assertRaisesRegex(RuntimeError, 'entire acquisition attempt'):
                campaign.require_native_acquisition_health(output, 'preflight', [slow, broken])

            receipt = campaign.read(output / 'acquisition-health-failure.json')
            self.assertEqual('preflight', receipt['stage'])
            self.assertEqual('broken', receipt['failures'][0]['classId'])
            self.assertFalse((output / 'run-plan.json').exists())

    def test_legacy_pilot_success_cannot_silently_change_the_new_admission_protocol(self):
        with self.assertRaisesRegex(ValueError, 'profile-dependent'):
            campaign.admits_primary_comparison({'eligible': True, 'missingRequiredScenarios': []})

    def test_same_reserve_cannot_fill_two_selected_slots(self):
        selected = {'origin': 'selected', 'order': 0, 'identity': 'one', 'projectId': 'p', 'stratum': 'complex'}
        other = dict(selected, order=1, identity='two')
        reserve = dict(selected, origin='reserve', classId='reserve')

        chosen, missing = campaign.select_classes({'decisions': [selected, other, reserve]}, {'reserve'})

        self.assertEqual(['reserve'], chosen)
        self.assertEqual('two', missing[0]['identity'])

    def test_serializer_defaults_preserve_all_explicit_frozen_values(self):
        declared = {'classes': [{'inputs': [{'domain': {'kind': 'string', 'maxLength': 8}}]}], 'seeds': [17]}
        prepared = {'classes': [{'inputs': [{'domain': {'kind': 'string', 'minLength': 0, 'maxLength': 8}}]}],
                    'seeds': [17], 'schemaVersion': 1}

        self.assertTrue(campaign.preserves_input_values(prepared, declared))
        prepared['classes'][0]['inputs'][0]['domain']['maxLength'] = 9
        self.assertFalse(campaign.preserves_input_values(prepared, declared))
        self.assertFalse(campaign.preserves_input_values({'seeds': [18]}, declared))

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
                        'schemaVersion': 2, 'coveragePolicy': campaign.COVERAGE_POLICY,
                        'suspendedCoveragePolicy': campaign.SUSPENDED_COVERAGE_POLICY,
                        'preparedSha256': campaign.sha(directory / 'class.json'),
                        'preflightSha256': campaign.sha(directory / 'preflight.json')}
            result = {'identity': identity, 'search': {'search': {'candidates': []}},
                      'checkpoints': [{'searchBudgetMillis': 30_000,
                      'totalStatements': 1, 'coveredStatementIds': ['foreign'], 'uncoveredStatementIds': []}]}
            universe = {'universeSha256': 'universe', 'statements': [{'id': 'original'}]}

            with self.assertRaisesRegex(ValueError, 'numerator/denominator'):
                campaign.validate_result(result, {'classId': 'class'}, prepared, universe)

    def test_new_measurement_cannot_reinterpret_sealed_legacy_results(self):
        with self.assertRaisesRegex(ValueError, 'schema-2'):
            campaign.validate_measurement_protocol({'schemaVersion': 1})

    def test_native_primary_and_strict_secondary_are_reported_separately(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, manifest, plan = self.make_phase_fixture(output)
            for profile in campaign.PROFILES:
                self.write_phase_result(output, prepared, manifest, budget=30_000, profile=profile)
                path = campaign.cell_directory(output, 'class', 17, 30_000, profile) / 'result.json'
                result = campaign.read(path)
                result['search']['search']['candidates'] = [
                    {'origin': 'TERMINATED', 'completion': 'RETURNED', 'emittedAtMillis': 5},
                    {'origin': 'SUSPENDED', 'suspendedAtMachineStop': True, 'emittedAtMillis': 30_050},
                    {'origin': 'SUSPENDED', 'suspendedAtMachineStop': False, 'emittedAtMillis': 30_051},
                ]
                final = result['checkpoints'][-1]
                final.update(coveredStatementIds=['original'], uncoveredStatementIds=[],
                             acceptedCandidates=2, emittedCandidates=2, strictRejectedCandidates=1,
                             strictOracleNotApplicable=1, suspendedCandidates=1, postStopSuspendedCandidates=1)
                result['oracleRejections'] = 1
                path.write_text(json.dumps(result) + '\n')

            summary = campaign.summarize(output, prepared, plan, scheduled_budgets=(30_000,))

            self.assertEqual([], summary['incompleteOrInvalidCells'])
            self.assertEqual({1}, {row['covered'] for row in summary['rows']})
            self.assertEqual({0}, {row['strictCovered'] for row in summary['rows']})
            self.assertFalse(summary['pairs'][0]['measurementFailures'])
            self.assertTrue(summary['pairs'][0]['symbolicOracleDisagreement'])
            self.assertEqual({1}, {row['postStopSuspendedCandidates'] for row in summary['rows']})

    def test_post_stop_extraction_cannot_be_backdated_into_intermediate_checkpoint(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            prepared, manifest, _ = self.make_phase_fixture(output)
            profile = campaign.PROFILES[0]
            self.write_phase_result(output, prepared, manifest, budget=30_000, profile=profile)
            path = campaign.cell_directory(output, 'class', 17, 30_000, profile) / 'result.json'
            result = campaign.read(path)
            result['search']['search']['candidates'] = [
                {'origin': 'SUSPENDED', 'suspendedAtMachineStop': True, 'emittedAtMillis': 10},
            ]
            intermediate = dict(result['checkpoints'][0], searchBudgetMillis=1_000,
                                includesSuspendedExtraction=False, emittedCandidates=1,
                                acceptedCandidates=1, suspendedCandidates=1, postStopSuspendedCandidates=1,
                                strictOracleNotApplicable=1)
            result['checkpoints'].insert(0, intermediate)
            universe = campaign.read(prepared / 'class' / 'class.json')['instrumentation']['universe']

            with self.assertRaisesRegex(ValueError, 'checkpoint attribution'):
                campaign.validate_result(result, {}, prepared, universe)


if __name__ == '__main__':
    unittest.main()
