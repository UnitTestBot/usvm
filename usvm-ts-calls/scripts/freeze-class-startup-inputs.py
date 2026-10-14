#!/usr/bin/env python3
"""Freeze startup graphs from acquired projects; never install or execute project code."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import re
import subprocess


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n', encoding='utf-8')


def safe_path(root, name):
    relative = Path(name)
    if relative.is_absolute() or '..' in relative.parts:
        raise ValueError(f'Unsafe frozen input name: {name}')
    file = root / relative
    if not file.resolve().is_relative_to(root.resolve()):
        raise ValueError(f'Frozen input escapes project: {name}')
    return file


def materialized_name(name):
    parts = Path(name).parts
    if 'node_modules' not in parts:
        return name
    first = parts.index('node_modules')
    return Path(*parts[:first], '.usvm-installed', *['packages' if part == 'node_modules' else part
                                                  for part in parts[first + 1:]]).as_posix()


def frontend_environment(module):
    script = ('const ts=require(process.argv[1]); const lib=ts.optionDeclarations.find(o=>o.name==="lib"); '
              'process.stdout.write(JSON.stringify({version:ts.version,options:ts.optionDeclarations.map(o=>o.name),'
              'libs:[...lib.element.type.keys()]}));')
    value = json.loads(subprocess.check_output(['node', '-e', script, str(module)], text=True))
    compiler = module / 'lib' / 'typescript.js' if module.is_dir() else module
    value['compilerSha256'] = digest(compiler.read_bytes())
    return value


def project_frontend_options(original, environment):
    projected = dict(original)
    boundaries = []
    supported = set(environment['options'])
    type_only = {'noUncheckedSideEffectImports', 'strictBuiltinIteratorReturn', 'noCheck',
                 'isolatedDeclarations', 'erasableSyntaxOnly', 'ignoreDeprecations'}
    for name in sorted(type_only - supported):
        if name in projected:
            boundaries.append({'kind': 'type-check-option', 'option': name, 'original': projected.pop(name),
                               'projected': None, 'reason': 'Unsupported frontend type-check-only option'})
    libs = projected.get('lib')
    if isinstance(libs, list):
        available = {name.lower() for name in environment['libs']}
        updated = list(dict.fromkeys(name if name.lower() in available else 'esnext' for name in libs))
        if updated != libs:
            if 'esnext' not in available:
                raise ValueError('Frontend lacks the declared compatibility type library esnext')
            boundaries.append({'kind': 'type-library-environment', 'option': 'lib', 'original': libs,
                               'projected': updated, 'reason': 'Frontend declaration library; no runtime emulation claimed'})
            projected['lib'] = updated
    return projected, {'environment': environment, 'boundaries': boundaries,
                       'unprojectedUnsupportedOptions': sorted(set(projected) - supported)}


def inspect_case(case, receipt_path, inspector, output, timeout, environment=None):
    installation = json.loads(receipt_path.read_bytes())
    installed = installation.get('phase') == 'FROZEN' and installation.get('dependenciesInstalled') is True
    root = Path(installation['sourceRoot'] if installed else installation['originalSourceRoot']).resolve()
    revision = re.search(r'@([0-9a-f]{40}):', case['identity']).group(1)
    class_id = re.sub(r'[^A-Za-z0-9_-]', '-', case['projectId'] + '-' + case['className']) + '-' + digest(case['identity'].encode())[:8]
    source = safe_path(root, case['sourceFile'])
    expected = subprocess.check_output(['git', '-C', str(root), 'show', f"{revision}:{case['sourceFile']}"])
    graph = {'files': [case['sourceFile']]}
    diagnostic = None
    command = None
    try:
        if revision != installation['projectRevision'] or source.read_bytes() != expected:
            raise ValueError('Original target revision or bytes changed')
        command = ['node', str(inspector), 'inspect-startup-closure', str(root), str(source)]
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=True)
        graph = json.loads(result.stdout)
        diagnostic = graph.get('diagnostic')
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        diagnostic = str(error)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            diagnostic += '\n' + error.stderr
        graph['reasonCode'] = 'STARTUP_GRAPH_FAILED'
    inputs = {case['sourceFile']: digest(expected)}
    resolution = graph.get('resolution', {})
    inputs.update(resolution.get('inputSha256', {}))
    for name in graph['files']:
        inputs.setdefault(name, digest(safe_path(root, name).read_bytes()))
    if inputs[case['sourceFile']] != digest(expected):
        diagnostic = 'Original target hash differs from graph input'
        graph['reasonCode'] = 'ORIGINAL_SOURCE_CHANGED'
    snapshot = installation.get('snapshot', {}).get('inputSha256', {})
    frozen_match = installation.get('phase') == 'FROZEN' and all(snapshot.get(name) == value for name, value in inputs.items())
    original_match = False
    if not frozen_match:
        original_match = True
        for name, value in inputs.items():
            try:
                original = subprocess.check_output(['git', '-C', str(root), 'show', f'{revision}:{name}'], stderr=subprocess.DEVNULL)
                if digest(original) != value:
                    original_match = False
            except subprocess.CalledProcessError:
                original_match = False
            if not original_match:
                break
    inputs_proven = frozen_match or original_match
    current_match = all(digest(safe_path(root, name).read_bytes()) == value for name, value in inputs.items())
    complete = inputs_proven and current_match and not graph.get('reasonCode')
    compiler = json.loads(resolution.get('tsconfig', '{}')).get('compilerOptions', {})
    original_compiler = dict(compiler)
    projection = None
    if environment is not None:
        compiler, projection = project_frontend_options(compiler, environment)
    frontend_files = [materialized_name(name) for name in graph['files']]
    paths = compiler.get('paths', {})
    compiler['paths'] = {name: [materialized_name(target) for target in targets] for name, targets in paths.items()}
    compiler.update({'types': [], 'noEmit': True})
    if not inputs_proven and diagnostic is None:
        diagnostic = 'Graph input is not covered by an immutable installation snapshot or original revision bytes'
    evidence = {'schemaVersion': 1, 'classId': class_id, 'identity': case['identity'], 'graph': graph,
                'installationComplete': installed, 'inputProof': 'installation-snapshot' if frozen_match else 'original-revision' if original_match else 'incomplete', 'installationDiagnostic': installation.get('diagnostic'), 'graphCommand': command, 'acquisitionComplete': complete, 'diagnostic': diagnostic,
                'originalCompilerOptions': original_compiler, 'frontendCompilerProjection': projection,
                'originalInputSha256': inputs, 'sourceMappings': {name: materialized_name(name) for name in graph['files']},
                'installationReceipt': str(receipt_path), 'installationSha256': digest(receipt_path.read_bytes())}
    write_json(output / 'graphs' / f'{class_id}.json', evidence)
    return {'case': case, 'classId': class_id, 'revision': revision, 'root': root, 'inputs': inputs,
            'installation': installation, 'receiptPath': receipt_path, 'complete': complete, 'evidence': evidence,
            'originalClassBytes': expected,
            'frontendConfig': json.dumps({'compilerOptions': compiler, 'files': sorted(frontend_files)})}


def freeze_case(result, output):
    case = result['case']
    project_root = safe_path(output / 'sources', case['projectId'])
    mappings = result['evidence']['sourceMappings']
    copied = {}
    for name, expected in result['inputs'].items():
        materialized = mappings.get(name, name)
        target = safe_path(project_root, materialized)
        data = safe_path(result['root'], name).read_bytes()
        if name == case['sourceFile']:
            data = result['originalClassBytes']
        actual = digest(data)
        if actual != expected:
            result['complete'] = False
            result['evidence']['diagnostic'] = 'Original frozen input changed before materialization'
        if target.exists() and target.read_bytes() != data:
            raise ValueError(f'Conflicting shared materialized input: {materialized}')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        copied[materialized] = actual
    receipt_name = f"receipts/{result['classId']}.json"
    receipt = {'schemaVersion': 1, 'projectRevision': result['revision'], 'sourceRoot': str(project_root),
               'classes': [{key: case[key] for key in ['identity', 'sourceFile', 'className']}],
               'originalClassSha256': {case['sourceFile']: digest(result['originalClassBytes'])},
               'snapshot': {'inputSha256': copied}, 'acquisitionComplete': result['complete'],
               'phase': 'GRAPH_FROZEN' if result['complete'] else 'ACQUISITION_INCOMPLETE',
               'originalAcquisitionReceipt': str(result['receiptPath']),
               'originalAcquisitionSha256': digest(result['receiptPath'].read_bytes()),
               'inputProof': result['evidence']['inputProof'], 'nodeVersion': result['installation'].get('nodeVersion'),
               'packageManager': result['installation'].get('packageManager'),
               'installationDiagnostic': result['installation'].get('diagnostic'),
               'originalInputSha256': result['inputs'], 'sourceMappings': mappings,
               'runtimeBoundaries': result['evidence']['graph'].get('resolution', {}).get('runtimeBoundaries', []),
               'initializationIssues': result['evidence']['graph'].get('initializationIssues', []),
               'diagnostic': result['evidence']['diagnostic'],
               'originalCompilerOptions': result['evidence']['originalCompilerOptions'],
               'frontendCompilerProjection': result['evidence']['frontendCompilerProjection'],
               'scope': 'Acquired graph evidence; symbolic support and actual class startup remain separate'}
    receipt_path = output / receipt_name
    write_json(receipt_path, receipt)
    write_json(output / 'graphs' / f"{result['classId']}.json", result['evidence'])
    return {'classId': result['classId'], 'identity': case['identity'], 'projectRevision': result['revision'],
            'sourceRoot': str(project_root), 'sourceFile': case['sourceFile'], 'className': case['className'],
            'inputSha256': copied, 'frontendConfig': result['frontendConfig'], 'acquisitionReceipt': receipt_name,
            'acquisitionSha256': digest(receipt_path.read_bytes()), 'acquisitionComplete': result['complete']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ledger', type=Path, required=True)
    parser.add_argument('--acquisition', type=Path, action='append', required=True)
    parser.add_argument('--inspector', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--frontend-typescript', type=Path, required=True)
    parser.add_argument('--tool-revision', required=True)
    parser.add_argument('--native-frontend-revision', required=True)
    parser.add_argument('--jobs', type=int, default=3)
    parser.add_argument('--graph-timeout-seconds', type=int, default=90)
    parser.add_argument('--budget-millis', type=int, default=2000)
    parser.add_argument('--solver-limit-millis', type=int, default=250)
    parser.add_argument('--collected-states-limit', type=int, default=10)
    parser.add_argument('--seed', type=int, default=17)
    parser.add_argument('--profile', default='FROZEN_STOP')
    args = parser.parse_args()
    if args.jobs < 1 or args.graph_timeout_seconds < 1 or not re.fullmatch(r'[0-9a-f]{40}', args.tool_revision):
        parser.error('Positive jobs/timeout and an exact tool revision are required')
    ledger_bytes = args.ledger.read_bytes()
    cases = json.loads(ledger_bytes)['decisions']
    if len({case['identity'] for case in cases}) != len(cases):
        parser.error('Original class identities must be unique')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    receipts = {}
    for case in cases:
        candidates = [attempt.resolve() / case['projectId'] / 'acquisition.json' for attempt in args.acquisition]
        available = [candidate for candidate in candidates if candidate.is_file()]
        if not available:
            parser.error(f"Missing acquisition receipt for original project {case['projectId']}")
        receipts[case['projectId']] = next((candidate for candidate in available
            if json.loads(candidate.read_bytes()).get('phase') == 'FROZEN'), available[0])
    environment = frontend_environment(args.frontend_typescript.resolve())
    write_json(output / 'frontend-environment.json', environment)
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        futures = [pool.submit(inspect_case, case, receipts[case['projectId']], args.inspector.resolve(),
                               output, args.graph_timeout_seconds, environment) for case in cases]
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            results.append(result)
            print(json.dumps({'classId': result['classId'], 'acquisitionComplete': result['complete'],
                              'diagnostic': result['evidence']['diagnostic']}), flush=True)
    rows = [freeze_case(result, output) for result in sorted(results, key=lambda item: item['case']['identity'])]
    manifest = {'schemaVersion': 1, 'toolRevision': args.tool_revision,
                'nativeFrontendRevision': args.native_frontend_revision, 'ledgerSha256': digest(ledger_bytes),
                'expectedClassCount': len(cases), 'classes': rows, 'budgetMillis': args.budget_millis,
                'solverLimitMillis': args.solver_limit_millis, 'collectedStatesLimit': args.collected_states_limit,
                'seed': args.seed, 'profile': args.profile}
    write_json(output / 'manifest.json', manifest)
    print(json.dumps({'manifest': str(output / 'manifest.json'), 'classes': len(rows),
                      'acquisitionComplete': sum(row['acquisitionComplete'] for row in rows)}), flush=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
