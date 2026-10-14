import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

MODULE_PATH = Path(__file__).with_name('acquire-class-projects.py')
SPEC = importlib.util.spec_from_file_location('acquisition', MODULE_PATH)
acquisition = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(acquisition)


class AcquisitionTest(unittest.TestCase):
    def test_original_git_source_clone_and_snapshot_are_independent(self):
        with tempfile.TemporaryDirectory(prefix='calls-acquire-') as temporary:
            directory = Path(temporary)
            original = directory / 'original'
            original.mkdir()
            (original / 'Entry.ts').write_text('export class Entry { read() { return 1; } }\n')
            (original / 'package.json').write_text('{"name":"source","version":"1.0.0"}')
            (original / 'package-lock.json').write_text('{"lockfileVersion":3}')
            subprocess.run(['git', 'init', '--quiet', str(original)], check=True)
            acquisition.git(original, 'add', '--', 'Entry.ts', 'package.json', 'package-lock.json')
            acquisition.git(original, '-c', 'user.name=Acquisition Test', '-c', 'user.email=test@example.invalid',
                            '-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Freeze source')
            revision = acquisition.git(original, 'rev-parse', 'HEAD').decode().strip()
            project = {'projectId': 'example', 'sourceRoot': str(original), 'projectRevision': revision,
                       'classes': [{'identity': f'example@{revision}:Entry.ts#Entry',
                                    'sourceFile': 'Entry.ts', 'className': 'Entry'}]}
            output = directory / 'acquired'
            output.mkdir()

            result = acquisition.acquire_project(project, output, install=False, overrides={}, scripts=[], timeout=30)
            receipt = json.loads(Path(result['receiptPath']).read_text())
            clone = Path(result['sourceRoot'])

            self.assertEqual('FROZEN', result['phase'])
            self.assertEqual((original / 'Entry.ts').read_bytes(), (clone / 'Entry.ts').read_bytes())
            self.assertFalse(receipt['dependencyLifecycleScriptsExecuted'])
            self.assertFalse(receipt['acquisitionComplete'])
            acquisition.verify_snapshot(clone, receipt['snapshot'])
            (clone / 'Entry.ts').write_text('export class Entry { read() { return 2; } }\n')
            with self.assertRaisesRegex(ValueError, 'snapshot changed'):
                acquisition.verify_snapshot(clone, receipt['snapshot'])
            self.assertEqual('export class Entry { read() { return 1; } }\n', (original / 'Entry.ts').read_text())

    def test_each_installation_uses_the_lock_and_disables_dependency_scripts(self):
        for manager, version, expected in [('npm', '11.17.0', '--ignore-scripts'),
                                           ('pnpm', '10.17.1', '--ignore-scripts'),
                                           ('bun', '1.3.14', '--ignore-scripts'),
                                           ('yarn', '1.22.22', '--ignore-scripts'),
                                           ('yarn', '4.9.0', '--mode=skip-build')]:
            with self.subTest(manager=manager, version=version):
                arguments = acquisition.installation_arguments(manager, version)
                self.assertIn(expected, arguments)
                self.assertTrue(arguments[0] == 'ci' or '--frozen-lockfile' in arguments or '--immutable' in arguments)

    def test_snapshot_captures_in_root_workspace_links_and_rejects_escaping_links(self):
        with tempfile.TemporaryDirectory(prefix='calls-snapshot-') as temporary:
            root = Path(temporary)
            (root / 'packages').mkdir()
            (root / 'packages/helper.ts').write_text('export const value = 3;')
            (root / 'helper.ts').symlink_to(root / 'packages/helper.ts')

            snapshot = acquisition.snapshot(root)

            self.assertEqual('packages/helper.ts', snapshot['symlinks']['helper.ts'])
            self.assertEqual(snapshot['inputSha256']['packages/helper.ts'], snapshot['inputSha256']['helper.ts'])
            (root / 'outside').symlink_to(root.parent)
            with self.assertRaisesRegex(ValueError, 'escapes frozen root'):
                acquisition.snapshot(root)

    def test_exact_engine_version_is_used_for_the_inferred_manager(self):
        with tempfile.TemporaryDirectory(prefix='calls-engine-') as temporary:
            root = Path(temporary)
            (root / 'package.json').write_text('{"engines":{"bun":"1.4.2"}}')
            (root / 'bun.lock').write_text('{"lockfileVersion":2}')

            self.assertEqual(('bun', '1.4.2', 'package.json#engines.bun'),
                             acquisition.manager_for(root, overrides={}))
            self.assertEqual(('bun', '1.3.14', 'explicit override'),
                             acquisition.manager_for(root, overrides={'bun': '1.3.14'}))

    def test_failed_commands_keep_the_command_log_and_failure_outcome(self):
        with tempfile.TemporaryDirectory(prefix='calls-command-') as temporary:
            root = Path(temporary)
            log = root / 'failure.log'

            record = acquisition.run_recorded(['node', '-e', 'console.log("failure evidence"); process.exit(3)'],
                                             root, log, timeout=30)

            self.assertEqual(3, record['exitCode'])
            self.assertFalse(record['timedOut'])
            self.assertEqual(acquisition.sha256(log.read_bytes()), record['logSha256'])
            self.assertIn('failure evidence', log.read_text())
            with self.assertRaisesRegex(RuntimeError, 'exited 3'):
                acquisition.check_command(record)

    def test_declared_package_manager_wins_over_inferred_lock_and_is_pinned(self):
        with tempfile.TemporaryDirectory(prefix='calls-manager-') as temporary:
            root = Path(temporary)
            (root / 'package.json').write_text('{"packageManager":"pnpm@10.12.1"}')
            (root / 'pnpm-lock.yaml').write_text('lockfileVersion: 9.0\n')

            self.assertEqual(('pnpm', '10.12.1', 'package.json#packageManager'),
                             acquisition.manager_for(root, overrides={'pnpm': '10.17.1'}))
            (root / 'package.json').write_text('{"packageManager":"pnpm@latest"}')
            with self.assertRaisesRegex(ValueError, 'Unsupported declared'):
                acquisition.manager_for(root, overrides={})


if __name__ == '__main__':
    unittest.main()
