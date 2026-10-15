#!/usr/bin/env python3
"""Frozen 30s, seed17, four-profile raw symbolic diagnostic after the 180/200 startup gate."""
import argparse
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

SMOKE_SCRIPT = Path(__file__).with_name('run-class-start-smoke.py')
SPEC = importlib.util.spec_from_file_location('startup', SMOKE_SCRIPT)
startup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(startup)
PROFILES = ('EMPTY_STOP', 'EMPTY_FRESH', 'FROZEN_STOP', 'FROZEN_FRESH')
EXPECTED_CLASSES = 200
MINIMUM_STARTS = 180
BUDGET_MILLIS = 30_000
SEED = 17


def runtime_catalog(calls):
    catalog = json.loads(subprocess.check_output([str(calls), 'class-catalog'], text=True, timeout=60))
    resource = 'org/usvm/ts/calls/build.properties'
    identities = []
    for jar_path in sorted((calls.parent.parent / 'lib').glob('*.jar')):
        with zipfile.ZipFile(jar_path) as jar:
            if resource not in jar.namelist():
                continue
            lines = jar.read(resource).decode('utf-8').splitlines()
            properties = dict(line.split('=', 1) for line in lines if '=' in line and not line.startswith('#'))
            identities.append(properties)
    if len(identities) != 1:
        raise ValueError('Expected exactly one packaged Calls build identity')
    identity = identities[0]
    if catalog['toolRevision'] != identity.get('tool.revision'):
        raise ValueError('Catalog differs from the packaged Calls revision')
    frontend = identity.get('native.frontend.revision')
    if not frontend:
        raise ValueError('Packaged Calls build has no native frontend identity')
    return {**catalog, 'nativeFrontendRevision': frontend}


def verify_startup_gate(manifest, results, fingerprint):
    if len(manifest['classes']) != EXPECTED_CLASSES or manifest.get('expectedClassCount', EXPECTED_CLASSES) != EXPECTED_CLASSES:
        raise ValueError('The exploratory denominator must remain the frozen original 200 classes')
    if results['manifestSha256'] != fingerprint or not results['complete']:
        raise ValueError('Startup gate requires completed results for this exact frozen manifest')
    rows = results['results']
    if [row['identity'] for row in rows] != [case['identity'] for case in manifest['classes']]:
        raise ValueError('Startup result identities or original ordering differ')
    for case, row in zip(manifest['classes'], rows):
        startup.validate_result(row, case, fingerprint)
    qualifying = sum(row['qualifyingStarted'] for row in rows)
    if qualifying != results['qualifyingStartedCount'] or qualifying < MINIMUM_STARTS:
        raise ValueError(f'Real startup gate requires {MINIMUM_STARTS}/200; got {qualifying}/200')
    return qualifying


def profile_manifest(source, profile, catalog, original_directory, output):
    if (catalog['toolRevision'] != source['toolRevision']
            or catalog['nativeFrontendRevision'] != source['nativeFrontendRevision']):
        raise ValueError('Exploratory runtime differs from the exact runtime that proved the startup gate')
    result = copy.deepcopy(source)
    result.update(budgetMillis=BUDGET_MILLIS, solverLimitMillis=250, timeQuantumMillis=25,
                  collectedStatesLimit=None, profile=profile, seed=SEED)
    for case in result['classes']:
        receipt = case.get('acquisitionReceipt')
        if receipt is None:
            continue
        original = (original_directory / receipt).resolve(strict=True)
        if startup.sha(original) != case['acquisitionSha256']:
            raise ValueError('Frozen acquisition receipt changed before the measurement')
        destination = output / 'receipts' / f'{case["acquisitionSha256"]}.json'
        destination.parent.mkdir(parents=True, exist_ok=True)
        if not destination.exists():
            shutil.copyfile(original, destination)
        if startup.sha(destination) != case['acquisitionSha256']:
            raise ValueError('Copied acquisition receipt changed')
        case['acquisitionReceipt'] = destination.relative_to(output).as_posix()
    return result


def summarize(output, manifests):
    rows = []
    for case in next(iter(manifests.values()))['classes']:
        universe_hashes = set()
        for profile, manifest in manifests.items():
            path = output / profile / 'classes' / case['classId'] / 'result.json'
            fingerprint = startup.sha(output / f'{profile}.json')
            result = startup.read(path) if path.exists() else startup.failure_result(case, fingerprint, 'PENDING', 'No child receipt')
            startup.validate_result(result, case, fingerprint)
            raw = result.get('rawAnalysis')
            if raw is not None:
                universe_hashes.add(raw['statementUniverseSha256'])
            rows.append({'classId': case['classId'], 'identity': case['identity'], 'profile': profile,
                         'seed': SEED, 'budgetMillis': BUDGET_MILLIS, 'started': result['started'],
                         'startupStatus': result['startupStatus'], 'stage': result['stage'],
                         'stopReason': result.get('stopReason'), 'timedOut': result.get('timedOut', False),
                         'unsupportedCall': result.get('unsupportedCall', False),
                         'engineFailed': result.get('engineFailed', False), 'runtimeLimited': result.get('runtimeLimited', False),
                         'rawAnalysisAvailable': raw is not None,
                         'returnedStates': raw['returnedStates'] if raw is not None else None,
                         'exceptionalStates': raw['exceptionalStates'] if raw is not None else None,
                         'stoppedPrefixStates': raw['stoppedPrefixStates'] if raw is not None else None,
                         'remainingStates': raw['remainingStates'] if raw is not None else None,
                         'stateSnapshotFailures': len(raw['stateSnapshotFailures']) if raw is not None else None,
                         'analysisElapsedMillis': raw['analysisElapsedMillis'] if raw is not None else None,
                         'budgetOverrunMillis': raw['budgetOverrunMillis'] if raw is not None else None,
                         'sourceMappedIrUniverseSize': len(raw['sourceMappedIrUniverse']) if raw is not None else None,
                         'sourceMappedIrEnteredSize': len(raw['sourceMappedIrEntered']) if raw is not None else None,
                         'sourceMappedIrUniverseSha256': raw['statementUniverseSha256'] if raw is not None else None,
                         'nativeClassCoverage': None, 'receipt': str(path),
                         'durableStateSnapshotsPath': result.get('durableStateSnapshotsPath'),
                         'durableSymbolicExpressionsPath': result.get('durableSymbolicExpressionsPath'),
                         'stateSnapshotJournal': raw.get('stateSnapshots') if raw is not None else None,
                         'symbolicExpressionJournal': raw.get('symbolicExpressions') if raw is not None else None,
                         'diagnostic': result.get('diagnostic')})
        if len(universe_hashes) > 1:
            raise ValueError(f'Source-mapped IR universe changed between profiles: {case["identity"]}')
    summary = {'schemaVersion': 1, 'expectedClassCount': EXPECTED_CLASSES, 'expectedCellCount': EXPECTED_CLASSES * len(PROFILES),
               'budgetMillis': BUDGET_MILLIS, 'seed': SEED, 'profiles': list(PROFILES), 'results': rows,
               'complete': all(row['stage'] != 'PENDING' for row in rows),
               'rawAnalysisAvailableCells': sum(row['rawAnalysisAvailable'] for row in rows),
               'proof': 'Exploratory symbolic source-mapped IR and retained state diagnostics; native coverage is unavailable'}
    startup.save(output / 'summary.json', summary)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--calls', type=Path, required=True)
    parser.add_argument('--startup-manifest', type=Path, required=True)
    parser.add_argument('--startup-summary', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--child-timeout-seconds', type=int, default=120)
    args = parser.parse_args()
    manifest_path = args.startup_manifest.resolve(strict=True)
    source = startup.read(manifest_path)
    startup_results = startup.read(args.startup_summary)
    qualifying = verify_startup_gate(source, startup_results, startup.sha(manifest_path))
    for case, result in zip(source['classes'], startup_results['results']):
        if result['qualifyingStarted']:
            startup.verify_case(case, manifest_path.parent)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    calls = args.calls.resolve(strict=True)
    catalog = runtime_catalog(calls)
    runtime = startup.runtime_identity(calls)
    startup_identity = startup.read(args.startup_summary.resolve(strict=True).parent / 'identity.json')
    if startup_identity.get('manifestSha256') != startup.sha(manifest_path) or startup_identity.get('runtime') != runtime:
        raise ValueError('Exploratory runtime bytes differ from the exact frozen startup runtime')
    identity = {'startupManifestSha256': startup.sha(manifest_path), 'startupSummarySha256': startup.sha(args.startup_summary),
                'qualifyingStarted': qualifying, 'runtime': runtime, 'driverSha256': startup.sha(Path(__file__)),
                'catalog': catalog, 'profiles': list(PROFILES), 'budgetMillis': BUDGET_MILLIS, 'seed': SEED,
                'collectedStatesLimit': None, 'timeQuantumMillis': 25, 'solverLimitMillis': 250,
                'childTimeoutSeconds': args.child_timeout_seconds}
    startup.freeze(output / 'identity.json', identity)
    manifests = {}
    for profile in PROFILES:
        manifest = profile_manifest(source, profile, catalog, manifest_path.parent, output)
        startup.freeze(output / f'{profile}.json', manifest)
        manifests[profile] = manifest
    summarize(output, manifests)
    for profile in PROFILES:
        print(f'{profile}: frozen 200 classes, 30s, seed17, no terminal-state cap', flush=True)
        subprocess.run([sys.executable, str(SMOKE_SCRIPT), '--calls', str(calls), '--command', 'class-symbolic-probe',
                        '--manifest', str(output / f'{profile}.json'), '--output', str(output / profile),
                        '--ir-cache', str(output / 'ir-cache'), '--child-timeout-seconds', str(args.child_timeout_seconds)],
                       check=True)
        summarize(output, manifests)
    if startup.runtime_identity(calls) != runtime:
        raise ValueError('Runtime changed during the four-profile diagnostic')
    summary = summarize(output, manifests)
    print(json.dumps({key: value for key, value in summary.items() if key != 'results'}), flush=True)


if __name__ == '__main__':
    main()
