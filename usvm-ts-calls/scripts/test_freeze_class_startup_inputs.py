import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

MODULE = Path(__file__).with_name('freeze-class-startup-inputs.py')
SPEC = importlib.util.spec_from_file_location('startup_freeze', MODULE)
freeze = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(freeze)
INSPECTOR = Path(__file__).parents[2] / 'usvm-ts-fast-check/fast-check-adapter/dist/src/source-inspector-cli.js'


def original_project(directory):
    root = directory / 'original'
    files = {'Entry.ts': "import { value } from 'example'; export class Entry { read() { return value; } }",
             'tsconfig.json': '{"compilerOptions":{"strict":true}}',
             'package.json': '{"name":"fixture","version":"1.0.0"}',
             'package-lock.json': '{"lockfileVersion":3}',
             'node_modules/example/package.json': '{"name":"example","main":"./runtime.js","types":"./types.d.ts"}',
             'node_modules/example/runtime.js': "const data = require('./data.json'); exports.value = data.answer;",
             'node_modules/example/data.json': '{"answer":42}',
             'node_modules/example/types.d.ts': 'export declare const value: 1;'}
    for name, text in files.items():
        file = root / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)
    subprocess.run(['git', 'init', '--quiet', str(root)], check=True)
    subprocess.run(['git', '-C', str(root), 'add', '--', 'Entry.ts', 'tsconfig.json', 'package.json', 'package-lock.json'], check=True)
    subprocess.run(['git', '-C', str(root), '-c', 'user.name=Startup Test', '-c', 'user.email=test@example.invalid',
                    '-c', 'core.hooksPath=/dev/null', '-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Freeze fixture'], check=True)
    revision = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    case = {'projectId': 'example--fixture', 'sourceFile': 'Entry.ts', 'className': 'Entry',
            'identity': f'example--fixture@{revision}:Entry.ts#Entry'}
    receipt = {'projectRevision': revision, 'sourceRoot': str(root), 'originalSourceRoot': str(root),
               'phase': 'FROZEN', 'dependenciesInstalled': True, 'classes': [case],
               'snapshot': {'inputSha256': {name: freeze.digest(text.encode()) for name, text in files.items()}}}
    receipt_path = directory / 'acquisition.json'
    freeze.write_json(receipt_path, receipt)
    return root, case, receipt_path, files


class StartupFreezeTest(unittest.TestCase):
    def test_actual_graph_materialization_preserves_original_bytes_and_narrow_roots(self):
        with tempfile.TemporaryDirectory(prefix='calls-startup-freeze-') as temporary:
            directory = Path(temporary).resolve()
            root, case, receipt, files = original_project(directory)
            output = directory / 'output'

            inspected = freeze.inspect_case(case, receipt, INSPECTOR, output, timeout=30)
            row = freeze.freeze_case(inspected, output)
            graph_receipt = json.loads((output / row['acquisitionReceipt']).read_bytes())
            config = json.loads(row['frontendConfig'])

            self.assertTrue(row['acquisitionComplete'])
            self.assertEqual(files['Entry.ts'].encode(), (Path(row['sourceRoot']) / 'Entry.ts').read_bytes())
            self.assertIn('.usvm-installed/example/runtime.js', config['files'])
            self.assertTrue(all('node_modules' not in file for file in config['files']))
            self.assertNotIn('.usvm-installed/example/types.d.ts', config['files'])
            self.assertEqual('json', graph_receipt['runtimeBoundaries'][0]['kind'])
            self.assertEqual(freeze.digest(receipt.read_bytes()), graph_receipt['originalAcquisitionSha256'])
            self.assertNotIn('strictBuiltinIteratorReturn', config['compilerOptions'])
            self.assertEqual(files['Entry.ts'], (root / 'Entry.ts').read_text())

    def test_changed_runtime_bytes_cannot_upgrade_frozen_installation_to_complete_graph(self):
        with tempfile.TemporaryDirectory(prefix='calls-startup-change-') as temporary:
            directory = Path(temporary).resolve()
            root, case, receipt, _ = original_project(directory)
            (root / 'node_modules/example/runtime.js').write_text('exports.value = 99;')

            inspected = freeze.inspect_case(case, receipt, INSPECTOR, directory / 'output', timeout=30)
            row = freeze.freeze_case(inspected, directory / 'output')

            self.assertFalse(row['acquisitionComplete'])
            self.assertIn('not covered', inspected['evidence']['diagnostic'])

    def test_irrelevant_failed_dev_dependency_install_does_not_veto_original_source_graph(self):
        with tempfile.TemporaryDirectory(prefix='calls-startup-local-') as temporary:
            directory = Path(temporary).resolve()
            root, case, receipt_path, _ = original_project(directory)
            (root / 'Entry.ts').write_text('export class Entry { read() { return 42; } }')
            subprocess.run(['git', '-C', str(root), 'add', '--', 'Entry.ts'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Startup Test', '-c', 'user.email=test@example.invalid',
                            '-c', 'core.hooksPath=/dev/null', '-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Use local graph'], check=True)
            revision = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
            case['identity'] = f'example--fixture@{revision}:Entry.ts#Entry'
            receipt = json.loads(receipt_path.read_bytes())
            receipt.update({'phase': 'FAILED', 'diagnostic': 'Unneeded devDependency quarantined', 'projectRevision': revision})
            freeze.write_json(receipt_path, receipt)

            inspected = freeze.inspect_case(case, receipt_path, INSPECTOR, directory / 'output', timeout=30)
            row = freeze.freeze_case(inspected, directory / 'output')

            self.assertTrue(row['acquisitionComplete'])
            self.assertEqual('original-revision', inspected['evidence']['inputProof'])
            self.assertEqual('Unneeded devDependency quarantined', inspected['evidence']['installationDiagnostic'])

    def test_type_environment_projection_is_explicit_and_keeps_semantic_options(self):
        original = {'strict': True, 'noUncheckedSideEffectImports': True, 'lib': ['es2024', 'dom'],
                    'useDefineForClassFields': False, 'newSemanticOption': True}
        environment = {'version': '5.4.5', 'options': ['strict', 'lib', 'useDefineForClassFields'],
                       'libs': ['es2023', 'esnext', 'dom']}

        projected, evidence = freeze.project_frontend_options(original, environment)

        self.assertEqual(['esnext', 'dom'], projected['lib'])
        self.assertNotIn('noUncheckedSideEffectImports', projected)
        self.assertFalse(projected['useDefineForClassFields'])
        self.assertTrue(projected['newSemanticOption'])
        self.assertEqual(['newSemanticOption'], evidence['unprojectedUnsupportedOptions'])
        self.assertEqual(2, len(evidence['boundaries']))
        self.assertTrue(original['noUncheckedSideEffectImports'])
        self.assertEqual(['es2024', 'dom'], original['lib'])

    def test_failed_installation_remains_explicit_incomplete_row(self):
        with tempfile.TemporaryDirectory(prefix='calls-startup-failure-') as temporary:
            directory = Path(temporary).resolve()
            _, case, receipt_path, _ = original_project(directory)
            receipt = json.loads(receipt_path.read_bytes())
            receipt.update({'phase': 'FAILED', 'diagnostic': 'Locked installation failed'})
            freeze.write_json(receipt_path, receipt)

            inspected = freeze.inspect_case(case, receipt_path, INSPECTOR, directory / 'output', timeout=30)
            row = freeze.freeze_case(inspected, directory / 'output')

            self.assertFalse(row['acquisitionComplete'])
            self.assertEqual(case['identity'], row['identity'])
            self.assertEqual('Locked installation failed', inspected['evidence']['installationDiagnostic'])


if __name__ == '__main__':
    unittest.main()
