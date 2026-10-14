#!/usr/bin/env python3
"""Isolated raw-class startup probes, retaining every frozen class and failure."""
import argparse
import hashlib
import json
import os
import re
import signal
import shutil
from pathlib import Path
import subprocess


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    with temporary.open('w', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)


def freeze(path, value):
    if path.exists():
        if read(path) != value:
            raise ValueError(f'Frozen startup identity changed: {path}')
    else:
        save(path, value)


def verify_case(case, manifest_directory=None):
    root = Path(case['sourceRoot']).resolve(strict=True)
    for relative, expected in case['inputSha256'].items():
        name = Path(relative)
        if name.is_absolute() or '..' in name.parts:
            raise ValueError(f'Unsafe frozen input: {relative}')
        actual = (root / name).resolve(strict=True)
        if not actual.is_relative_to(root) or sha(actual) != expected:
            raise ValueError(f'Frozen startup input changed: {relative}')
    receipt_name = case.get('acquisitionReceipt')
    if receipt_name is None:
        if case.get('acquisitionComplete', False):
            raise ValueError('Complete acquisition needs a frozen receipt')
        return
    if manifest_directory is None:
        raise ValueError('Acquisition receipt needs its manifest root')
    relative = Path(receipt_name)
    if relative.is_absolute() or '..' in relative.parts:
        raise ValueError('Unsafe acquisition receipt path')
    receipt_path = (manifest_directory / relative).resolve(strict=True)
    if not receipt_path.is_relative_to(manifest_directory) or sha(receipt_path) != case.get('acquisitionSha256'):
        raise ValueError('Frozen acquisition receipt changed')
    receipt = read(receipt_path)
    if receipt['projectRevision'] != case['projectRevision'] or Path(receipt['sourceRoot']).resolve() != root:
        raise ValueError('Acquisition revision or source root differs')
    matching = any(owner['identity'] == case['identity'] and owner['sourceFile'] == case['sourceFile']
                   and owner['className'] == case['className'] for owner in receipt['classes'])
    if not matching or receipt['originalClassSha256'].get(case['sourceFile']) != case['inputSha256'][case['sourceFile']]:
        raise ValueError('Acquisition receipt differs from the frozen original class')
    snapshot = receipt['snapshot']['inputSha256']
    if any(snapshot.get(name) != digest for name, digest in case['inputSha256'].items()):
        raise ValueError('Startup input is outside its frozen acquired snapshot')
    if case.get('acquisitionComplete', False) and not receipt.get('acquisitionComplete', False):
        raise ValueError('Installation without graph proof cannot qualify')


def runtime_identity(calls):
    jars = sorted((calls.parent.parent / 'lib').glob('*.jar'))
    if not jars:
        raise ValueError('Use the installed Calls distribution with its frozen runtime jars')
    files = {'launcher': sha(calls), 'driver': sha(Path(__file__)),
             'jars': {path.name: sha(path) for path in jars}}
    java = Path(os.environ['JAVA_HOME']) / 'bin/java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java'))
    node = Path(shutil.which('node'))
    files.update(java={'path': str(java.resolve(strict=True)), 'sha256': sha(java)},
                 node={'path': str(node.resolve(strict=True)), 'sha256': sha(node)})
    files['javaVersion'] = subprocess.check_output([str(java), '-version'], stderr=subprocess.STDOUT,
                                                  text=True, timeout=10)
    files['JVMEnvironment'] = {name: os.environ.get(name) for name in ('JAVA_OPTS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')}
    files['frontendOverrides'] = {name: os.environ.get(name) for name in ('ETS_FRONTEND_SCRIPT', 'ETS_FRONTEND_DIR')}
    return files


def validate_result(result, case, fingerprint):
    if (result['classId'] != case['classId'] or result['identity'] != case['identity']
            or result['manifestSha256'] != fingerprint):
        raise ValueError('Saved startup result identity differs')
    started = result['initialStates'] > 0 and result['originalTargetStatementsEntered'] > 0
    if result['started'] != started:
        raise ValueError('Startup proof needs a real initial state and original target-class interpreter entry')
    if result['targetStatementEntered'] != (result['originalTargetStatementsEntered'] > 0):
        raise ValueError('Original target-class entry flag differs from its count')
    if result['qualifyingStarted'] and (not started or not case.get('acquisitionComplete', False)):
        raise ValueError('Incomplete acquisition cannot establish qualifying startup')


def failure_result(case, fingerprint, stage, diagnostic):
    return {'classId': case['classId'], 'identity': case['identity'], 'manifestSha256': fingerprint,
            'stage': stage, 'started': False, 'qualifyingStarted': False, 'initialStates': 0,
            'executedSteps': 0, 'targetClassSteps': 0, 'originalTargetClassSteps': 0,
            'syntheticOrUnmappedTargetClassSteps': 0, 'originalTargetStatementsEntered': 0,
            'targetStatementEntered': False, 'targetSuccessfulTransitions': 0, 'startupStatus': 'NOT_STARTED',
            'acquisitionComplete': case.get('acquisitionComplete', False),
            'acquisitionSha256': case.get('acquisitionSha256'), 'stageElapsedMillis': {}, 'diagnostic': diagnostic}


def child_result(child, receipt, case, fingerprint):
    generated = child / 'result.json'
    if generated.exists():
        result = read(generated)
    elif (child / 'progress.json').exists():
        result = read(child / 'progress.json')
        result.update(stage='SOURCE_ENTRY_CHILD_TIMEOUT' if receipt.get('timedOut') else 'SOURCE_ENTRY_CHILD_EXIT_FAILURE',
                      timedOut=receipt.get('timedOut', False), engineFailed=not receipt.get('timedOut', False),
                      startupStatus='STARTED_WITH_LIMITATION', diagnostic=f'Child stopped after durable source entry: {receipt}')
        journal = child / 'unknown-calls.json'
        if journal.exists():
            result['unknownCalls'] = read(journal)
        elif (child / 'unknown-calls.jsonl').exists():
            result['unknownCalls'] = [json.loads(line) for line in (child / 'unknown-calls.jsonl').read_text().splitlines()]
        if (child / 'states.jsonl').exists():
            result['durableStateSnapshotsPath'] = str(child / 'states.jsonl')
    else:
        stage = 'CHILD_TIMEOUT' if receipt.get('timedOut') else 'CHILD_EXIT_FAILURE'
        result = failure_result(case, fingerprint, stage, f'Child did not produce result: {receipt}')
    validate_result(result, case, fingerprint)
    return result


def summarize(manifest, output, fingerprint):
    rows = []
    for case in manifest['classes']:
        path = output / 'classes' / case['classId'] / 'result.json'
        if path.exists():
            result = read(path)
            validate_result(result, case, fingerprint)
        else:
            result = failure_result(case, fingerprint, 'PENDING', 'No completed child receipt')
        rows.append(result)
    qualifying = sum(row['qualifyingStarted'] for row in rows)
    summary = {'schemaVersion': 1, 'manifestSha256': fingerprint,
               'expectedClassCount': manifest.get('expectedClassCount', 200), 'classCount': len(rows),
               'startedCount': sum(row['started'] for row in rows), 'qualifyingStartedCount': qualifying,
               'complete': all(row['stage'] != 'PENDING' for row in rows),
               'qualifyingFraction': qualifying / len(rows), 'results': rows,
               'proof': 'Actual initial states plus original target-class interpreter entry; no native coverage claim'}
    save(output / 'summary.json', summary)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--calls', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--child-timeout-seconds', type=int, default=60)
    parser.add_argument('--command', choices=('class-start-smoke', 'class-symbolic-probe'), default='class-start-smoke')
    parser.add_argument('--ir-cache', type=Path)
    args = parser.parse_args()
    manifest_path = args.manifest.resolve(strict=True)
    manifest = read(manifest_path)
    classes = manifest['classes']
    if len(classes) != manifest.get('expectedClassCount', 200) or not classes:
        raise ValueError('Frozen startup ledger must retain every class')
    if (len({case['classId'] for case in classes}) != len(classes)
            or len({case['identity'] for case in classes}) != len(classes)):
        raise ValueError('Duplicate startup identity')
    if any(re.fullmatch(r'[A-Za-z0-9_-]+', case['classId']) is None for case in classes):
        raise ValueError('Unsafe class output identity')
    if args.child_timeout_seconds <= 0:
        raise ValueError('Child timeout must be positive')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    calls = args.calls.resolve(strict=True)
    fingerprint = sha(manifest_path)
    environment = dict(os.environ)
    if environment.get('ETS_FRONTEND_SCRIPT') is not None or environment.get('ETS_FRONTEND_DIR') is not None:
        raise ValueError('Startup probes require the pinned bundled frontend')
    environment['USVM_TS_CALLS_OPTS'] = '-Xms1g -Xmx6g -XX:+UseG1GC'
    environment['USVM_TS_STARTUP_IR_CACHE'] = str((args.ir_cache or output / 'ir-cache').resolve())
    runtime = runtime_identity(calls)
    freeze(output / 'identity.json', {'manifestSha256': fingerprint, 'calls': str(calls), 'runtime': runtime,
                                    'childTimeoutSeconds': args.child_timeout_seconds,
                                    'command': args.command, 'irCache': environment['USVM_TS_STARTUP_IR_CACHE'],
                                    'JVMOptions': environment['USVM_TS_CALLS_OPTS']})
    for index, case in enumerate(classes):
        directory = output / 'classes' / case['classId']
        result_path = directory / 'result.json'
        directory.mkdir(parents=True, exist_ok=True)
        try:
            verify_case(case, manifest_path.parent)
        except (OSError, ValueError) as error:
            if result_path.exists():
                raise ValueError(f'Saved startup inputs drifted: {case["classId"]}') from error
            save(result_path, failure_result(case, fingerprint, 'ACQUISITION_INPUT_FAILURE', str(error)))
            summarize(manifest, output, fingerprint)
            continue
        if result_path.exists():
            validate_result(read(result_path), case, fingerprint)
            continue
        child = directory / 'child'
        if child.exists():
            raise ValueError(f'Interrupted child needs a fresh attempt directory: {case["classId"]}')
        print(f'{index + 1}/{len(classes)} {case["identity"]}', flush=True)
        log = directory / 'process.log'
        with log.open('w', encoding='utf-8') as stream:
            process = subprocess.Popen([str(calls), args.command, str(manifest_path),
                                        case['classId'], str(child)], stdout=stream,
                                       stderr=subprocess.STDOUT, env=environment, start_new_session=True)
            try:
                code = process.wait(timeout=args.child_timeout_seconds)
                receipt = {'exitCode': code, 'log': str(log)}
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                receipt = {'exitCode': None, 'timedOut': True, 'processGroupKilled': True, 'log': str(log)}
        save(directory / 'process.json', receipt)
        result = child_result(child, receipt, case, fingerprint)
        verify_case(case, manifest_path.parent)
        save(result_path, result)
        summary = summarize(manifest, output, fingerprint)
        print(f'  started={result["started"]}; qualifying={summary["qualifyingStartedCount"]}/{len(classes)}', flush=True)
    summary = summarize(manifest, output, fingerprint)
    if runtime_identity(calls) != runtime:
        raise ValueError('Runtime changed during the startup run; receipts require a fresh attempt')
    print(json.dumps({key: value for key, value in summary.items() if key != 'results'}), flush=True)


if __name__ == '__main__':
    main()
