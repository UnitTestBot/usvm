#!/usr/bin/env python3
"""Acquire original frozen class projects before measurement; never run from the machine loop."""
import argparse
import concurrent.futures
import hashlib
import json
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess
import time
import tarfile
import threading


DEFAULT_MANAGER_VERSIONS = {'npm': '11.17.0', 'pnpm': '10.17.1', 'yarn': '1.22.22', 'bun': '1.3.14'}
TOOL_LOCK = threading.Lock()

LOCKFILES = {'npm': ['npm-shrinkwrap.json', 'package-lock.json'], 'pnpm': ['pnpm-lock.yaml'],
             'yarn': ['yarn.lock'], 'bun': ['bun.lock', 'bun.lockb']}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    os.replace(temporary, path)


def git(root, *arguments):
    return subprocess.check_output(['git', '-C', str(root), *arguments], stderr=subprocess.STDOUT)


def project_groups(ledger):
    groups = {}
    for case in ledger['decisions']:
        revision = re.search(r'@([0-9a-f]{40}):', case['identity']).group(1)
        key = case['projectId']
        group = groups.setdefault(key, {'projectId': key, 'sourceRoot': case['sourceRoot'],
                                        'projectRevision': revision, 'classes': []})
        if (group['sourceRoot'], group['projectRevision']) != (case['sourceRoot'], revision):
            raise ValueError(f'Conflicting original source identity for {key}')
        group['classes'].append({field: case[field] for field in ['identity', 'sourceFile', 'className']})
    return list(groups.values())


def manager_for(root, overrides):
    package = json.loads((root / 'package.json').read_text(encoding='utf-8'))
    declared = package.get('packageManager')
    if declared:
        match = re.fullmatch(r'(npm|pnpm|yarn|bun)@(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)(?:\+.*)?', declared)
        if not match:
            raise ValueError(f'Unsupported declared package manager: {declared}')
        manager, version = match.groups()
        version_source = 'package.json#packageManager'
    else:
        managers = [name for name, locks in LOCKFILES.items() if any((root / lock).is_file() for lock in locks)]
        if len(managers) != 1:
            raise ValueError(f'Expected one declared lockfile manager, found {managers}')
        manager = managers[0]
        version = overrides.get(manager, DEFAULT_MANAGER_VERSIONS[manager])
        version_source = 'explicit override' if manager in overrides else 'pinned acquisition default'
        engine = package.get('engines', {}).get(manager)
        exact_engine = isinstance(engine, str) and re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', engine)
        if manager not in overrides and exact_engine:
            version = engine
            version_source = f'package.json#engines.{manager}'
        if manager == 'yarn' and not exact_engine and '__metadata:' in (root / 'yarn.lock').read_text(encoding='utf-8'):
            version = overrides.get('yarn', '4.9.0')
    if not any((root / lock).is_file() for lock in LOCKFILES[manager]):
        raise ValueError(f'No lockfile for declared manager {manager}')
    return manager, version, version_source


def manager_command(manager, version, tools_root):
    executable = shutil.which(manager)
    if executable:
        result = subprocess.run([executable, '--version'], capture_output=True, text=True, timeout=60)
        if result.returncode == 0 and result.stdout.strip() == version:
            return [executable]
    if manager == 'bun':
        return [str(acquire_bun_binary(version, tools_root))]
    package = f'@yarnpkg/cli-dist@{version}' if manager == 'yarn' and not version.startswith('1.') else f'{manager}@{version}'
    return ['npm', 'exec', '--yes', '--ignore-scripts', f'--package={package}', '--', manager]


def acquire_bun_binary(version, tools_root):
    systems = {'Darwin': 'darwin', 'Linux': 'linux'}
    architectures = {'arm64': 'aarch64', 'aarch64': 'aarch64', 'x86_64': 'x64', 'AMD64': 'x64'}
    system, architecture = systems.get(platform.system()), architectures.get(platform.machine())
    if not system or not architecture:
        raise ValueError('Unsupported platform for offline Bun binary acquisition')
    directory = tools_root / f'bun-{version}-{system}-{architecture}'
    executable = directory / 'bun'
    with TOOL_LOCK:
        if not executable.exists():
            directory.mkdir(parents=True, exist_ok=True)
            package = f'@oven/bun-{system}-{architecture}@{version}'
            command = ['npm', 'pack', '--ignore-scripts', '--json', '--pack-destination', str(directory), package]
            result = subprocess.run(command, capture_output=True, text=True, timeout=300, check=True)
            package_receipt = json.loads(result.stdout)[0]
            archive = directory / package_receipt['filename']
            with tarfile.open(archive, 'r:gz') as tar:
                member = tar.getmember('package/bin/bun')
                if not member.isfile():
                    raise ValueError('Bun package binary is not an ordinary file')
                executable.write_bytes(tar.extractfile(member).read())
            executable.chmod(0o755)
            write_json(directory / 'receipt.json', {'package': package, 'packageReceipt': package_receipt,
                       'command': command, 'tarballSha256': sha256(archive.read_bytes()),
                       'binarySha256': sha256(executable.read_bytes()), 'lifecycleScriptsExecuted': False})
        actual = subprocess.check_output([str(executable), '--version'], text=True).strip()
        if actual != version:
            raise ValueError(f'Acquired Bun version differs: {actual} != {version}')
    return executable


def installation_arguments(manager, version):
    if manager == 'npm':
        return ['ci', '--ignore-scripts', '--no-audit', '--no-fund']
    if manager == 'pnpm':
        return ['install', '--frozen-lockfile', '--ignore-scripts', '--ignore-pnpmfile']
    if manager == 'bun':
        return ['install', '--frozen-lockfile', '--ignore-scripts']
    if version.startswith('1.'):
        return ['install', '--frozen-lockfile', '--ignore-scripts', '--non-interactive']
    return ['install', '--immutable', '--mode=skip-build']


def run_recorded(command, root, log, timeout):
    started = time.monotonic()
    timed_out = False
    with log.open('wb') as output:
        try:
            result = subprocess.run(command, cwd=root, stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
            exit_code = result.returncode
        except subprocess.TimeoutExpired:
            timed_out = True
            exit_code = None
    record = {'command': command, 'exitCode': exit_code, 'timedOut': timed_out,
              'elapsedMillis': round((time.monotonic() - started) * 1000),
              'logSha256': sha256(log.read_bytes()), 'logPath': str(log)}
    return record


def check_command(record):
    if record['timedOut']:
        raise RuntimeError(f"Acquisition command timed out; see {record['logPath']}")
    if record['exitCode']:
        raise RuntimeError(f"Acquisition command exited {record['exitCode']}; see {record['logPath']}")


def snapshot(root):
    files, links = {}, {}
    canonical_root = root.resolve()
    for directory, directories, names in os.walk(root, followlinks=False):
        directories[:] = sorted(name for name in directories if name != '.git')
        for name in [*directories, *sorted(names)]:
            path = Path(directory) / name
            relative = path.relative_to(root).as_posix()
            if path.is_symlink():
                target = path.resolve(strict=True)
                if not target.is_relative_to(canonical_root):
                    raise ValueError(f'Acquired symlink escapes frozen root: {relative}')
                links[relative] = target.relative_to(canonical_root).as_posix()
            if path.is_file():
                files[relative] = sha256(path.read_bytes())
    identity = sha256(json.dumps({'files': files, 'links': links}, sort_keys=True, separators=(',', ':')).encode())
    return {'inputSha256': files, 'symlinks': links, 'snapshotSha256': identity}


def verify_snapshot(root, expected):
    actual = snapshot(root)
    if actual != expected:
        raise ValueError('Acquired runtime/config/dependency snapshot changed')


def acquire_project(project, output, install, overrides, scripts, timeout):
    destination = output / project['projectId']
    destination.mkdir(parents=True, exist_ok=False)
    receipt_path = destination / 'acquisition.json'
    checkout = destination / 'source'
    original = Path(project['sourceRoot']).resolve()
    receipt = {'schemaVersion': 1, **project, 'originalSourceRoot': str(original), 'sourceRoot': str(checkout),
               'phase': 'STARTED', 'commands': [], 'measurementMayRunInstall': False,
               'dependencyLifecycleScriptsExecuted': False, 'buildScripts': scripts}
    write_json(receipt_path, receipt)
    try:
        revision = project['projectRevision']
        if git(original, 'rev-parse', 'HEAD').decode().strip() != revision:
            raise ValueError('Original checkout does not match the frozen revision')
        git(original, 'diff', '--quiet', 'HEAD', '--')
        originals = {case['sourceFile']: sha256(git(original, 'show', f"{revision}:{case['sourceFile']}"))
                     for case in project['classes']}
        subprocess.run(['git', 'clone', '--no-hardlinks', '--no-checkout', str(original), str(checkout)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        git(checkout, 'checkout', '--quiet', '--detach', revision)
        receipt['phase'] = 'CLONED'
        receipt['nodeVersion'] = subprocess.check_output(['node', '--version'], text=True).strip()
        manager, version, version_source = manager_for(checkout, overrides)
        receipt['packageManager'] = {'name': manager, 'version': version, 'versionSource': version_source}
        command = manager_command(manager, version, tools_root=output / ".tools") if install or scripts else None
        if install:
            receipt['commands'].append(run_recorded(command + installation_arguments(manager, version),
                                                    checkout, destination / 'install.log', timeout))
            check_command(receipt['commands'][-1])
            receipt['phase'] = 'INSTALLED'
        package = json.loads((checkout / 'package.json').read_text(encoding='utf-8'))
        for index, script in enumerate(scripts):
            if script not in package.get('scripts', {}):
                raise ValueError(f'No original package script named {script}')
            receipt['commands'].append(run_recorded(command + ['run', script], checkout,
                                                    destination / f'build-{index}.log', timeout))
            check_command(receipt['commands'][-1])
        # Builds may add ignored/generated files, but they cannot silently alter original source.
        git(checkout, 'diff', '--quiet', 'HEAD', '--')
        for relative, expected in originals.items():
            if sha256((checkout / relative).read_bytes()) != expected:
                raise ValueError(f'Original class source changed during acquisition: {relative}')
        receipt['originalClassSha256'] = originals
        receipt['snapshot'] = snapshot(checkout)
        receipt['phase'] = 'FROZEN'
        receipt['dependenciesInstalled'] = install
        # A frozen installation is acquisition evidence. Startup graph resolution separately proves
        # that every consumed module and explicit external runtime boundary was captured.
        receipt['acquisitionComplete'] = False
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        receipt['failedPhase'] = receipt['phase']
        receipt['phase'] = 'FAILED'
        receipt['diagnostic'] = str(error)
    write_json(receipt_path, receipt)
    return {'projectId': project['projectId'], 'phase': receipt['phase'], 'sourceRoot': str(checkout),
            'classCount': len(project['classes']), 'receiptPath': str(receipt_path),
            'acquisitionSha256': sha256(receipt_path.read_bytes()), 'diagnostic': receipt.get('diagnostic')}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ledger', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--project-id', action='append', default=[])
    parser.add_argument('--skip-install', action='store_true')
    parser.add_argument('--manager-version', action='append', default=[], metavar='NAME=VERSION')
    parser.add_argument('--build-script', action='append', default=[], metavar='PROJECT=SCRIPT')
    parser.add_argument('--jobs', type=int, default=3)
    parser.add_argument('--timeout-seconds', type=int, default=1800)
    arguments = parser.parse_args()
    ledger_bytes = arguments.ledger.read_bytes()
    projects = project_groups(json.loads(ledger_bytes))
    if arguments.project_id:
        projects = [project for project in projects if project['projectId'] in arguments.project_id]
        if len(projects) != len(set(arguments.project_id)):
            parser.error('Unknown or duplicate project selection')
    if arguments.jobs < 1 or arguments.timeout_seconds < 1:
        parser.error('Jobs and timeout must be positive')
    overrides = dict(value.split('=', 1) for value in arguments.manager_version)
    for manager, version in overrides.items():
        if manager not in DEFAULT_MANAGER_VERSIONS or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', version):
            parser.error('Manager overrides must name a supported manager and an exact version')
    scripts = {}
    for value in arguments.build_script:
        project, script = value.split('=', 1)
        scripts.setdefault(project, []).append(script)
    output = arguments.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=arguments.jobs) as pool:
        futures = [pool.submit(acquire_project, project, output, not arguments.skip_install, overrides,
                               scripts.get(project['projectId'], []), arguments.timeout_seconds) for project in projects]
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            results.append(result)
            print(json.dumps(result), flush=True)
            write_json(output / 'projects.json', {'schemaVersion': 1, 'ledgerSha256': sha256(ledger_bytes),
                                                'classCount': sum(item['classCount'] for item in results),
                                                'projects': sorted(results, key=lambda item: item['projectId'])})
    return 1 if any(result['phase'] == 'FAILED' for result in results) else 0


if __name__ == '__main__':
    raise SystemExit(main())
