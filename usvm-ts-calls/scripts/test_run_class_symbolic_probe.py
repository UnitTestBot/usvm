import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('probe', Path(__file__).with_name('run-class-symbolic-probe.py'))
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class SymbolicProbeIntegrityTest(unittest.TestCase):
    def gate_fixture(self, qualifying):
        classes = [{'classId': f'c{i}', 'identity': f'original-{i}', 'acquisitionComplete': i < qualifying}
                   for i in range(200)]
        rows = []
        for case in classes:
            row = probe.startup.failure_result(case, 'frozen', 'STOPPED', 'none')
            if case['acquisitionComplete']:
                row.update(initialStates=1, targetStatementEntered=True, originalTargetStatementsEntered=1,
                           started=True, qualifyingStarted=True)
            rows.append(row)
        return {'expectedClassCount': 200, 'classes': classes}, {'manifestSha256': 'frozen', 'complete': True,
                                                              'qualifyingStartedCount': qualifying, 'results': rows}

    def test_gate_rejects_179_and_preserves_200_denominator_at_180(self):
        manifest, results = self.gate_fixture(179)
        with self.assertRaisesRegex(ValueError, '180/200'):
            probe.verify_startup_gate(manifest, results, 'frozen')

        manifest, results = self.gate_fixture(180)
        self.assertEqual(180, probe.verify_startup_gate(manifest, results, 'frozen'))
        self.assertEqual(200, len(manifest['classes']))

    def test_gate_rejects_reordered_or_incomplete_screen(self):
        manifest, results = self.gate_fixture(200)
        results['results'].reverse()
        with self.assertRaisesRegex(ValueError, 'ordering'):
            probe.verify_startup_gate(manifest, results, 'frozen')

        manifest, results = self.gate_fixture(200)
        results['complete'] = False
        with self.assertRaisesRegex(ValueError, 'completed results'):
            probe.verify_startup_gate(manifest, results, 'frozen')

    def test_four_modes_keep_original_inputs_and_disable_terminal_cap(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = {'classes': [{'classId': 'original', 'identity': 'unchanged', 'inputSha256': {'Target.ts': 'frozen'}}]}
            catalog = {'toolRevision': 'new-build', 'nativeFrontendRevision': 'pinned'}

            manifests = [probe.profile_manifest(source, profile, catalog, directory, directory) for profile in probe.PROFILES]

            self.assertEqual(list(probe.PROFILES), [manifest['profile'] for manifest in manifests])
            self.assertTrue(all(manifest['classes'] == source['classes'] for manifest in manifests))
            self.assertTrue(all(manifest['collectedStatesLimit'] is None for manifest in manifests))
            self.assertTrue(all(manifest['budgetMillis'] == 30000 and manifest['seed'] == 17 for manifest in manifests))

    def test_missing_metrics_are_unavailable_and_pending_cells_remain(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            manifests = {profile: {'classes': [{'classId': 'target', 'identity': 'original'}]} for profile in probe.PROFILES}
            for profile, manifest in manifests.items():
                probe.startup.save(directory / f'{profile}.json', manifest)

            summary = probe.summarize(directory, manifests)

            self.assertEqual(4, len(summary['results']))
            self.assertEqual(800, summary['expectedCellCount'])
            self.assertFalse(summary['complete'])
            self.assertTrue(all(row['returnedStates'] is None for row in summary['results']))
            self.assertTrue(all(row['nativeClassCoverage'] is None for row in summary['results']))


if __name__ == '__main__':
    unittest.main()
