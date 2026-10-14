#!/usr/bin/env python3
"""Sequential paired class campaign with profile-independent source/native admission."""
import argparse
import csv
import hashlib
import json
import subprocess
from pathlib import Path

PROFILES = ('EMPTY_STOP', 'EMPTY_FRESH', 'FROZEN_STOP', 'FROZEN_FRESH')
BUDGETS = (30_000, 120_000)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def freeze(path, value):
    if path.exists():
        if read(path) != value:
            raise ValueError(f'Persisted campaign identity changed: {path}')
        return

    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def invoke(calls, arguments, log, timeout):
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open('w', encoding='utf-8') as output:
        try:
            result = subprocess.run([str(calls), *map(str, arguments)], stdout=output,
                                    stderr=subprocess.STDOUT, timeout=timeout, check=False)
            return {'exitCode': result.returncode, 'log': str(log)}
        except subprocess.TimeoutExpired:
            return {'exitCode': None, 'timeoutSeconds': timeout, 'log': str(log)}


def select_classes(ledger, eligible):
    if ledger is None:
        return sorted(eligible), []

    decisions = ledger['decisions']
    chosen = []
    missing = []
    used = set()
    for selected in sorted((item for item in decisions if item['origin'] == 'selected'),
                           key=lambda item: item['order']):
        alternatives = [selected] + sorted(
            (item for item in decisions if item['origin'] == 'reserve'
             and item['projectId'] == selected['projectId'] and item['stratum'] == selected['stratum']),
            key=lambda item: item['order'])
        replacement = next((item for item in alternatives
                            if item.get('classId') in eligible and item['classId'] not in used), None)
        if replacement is None:
            missing.append({'identity': selected['identity'], 'projectId': selected['projectId'],
                            'stratum': selected['stratum'], 'reason': 'No eligible preordered same-stratum reserve'})
        else:
            used.add(replacement['classId'])
            chosen.append(replacement['classId'])

    return chosen, missing


def admits_primary_comparison(preflight):
    if preflight.get('admissionVersion') != 1:
        raise ValueError('Legacy profile-dependent preflight cannot admit a new scientific comparison')

    return preflight['eligible'] and preflight['primaryInferenceEligible'] is True


def require_native_acquisition_health(output, stage, records):
    if (output / 'acquisition-health-failure.json').exists():
        raise RuntimeError('Saved native acquisition failure invalidates the entire acquisition attempt')

    failures = [{'classId': record.get('classId', record.get('identity', {}).get('classId')),
                 'nativeInfrastructureFailures': record['nativeInfrastructureFailures']}
                for record in records if record.get('nativeInfrastructureFailures')]
    if failures:
        freeze(output / 'acquisition-health-failure.json', {'stage': stage, 'failures': failures})
        raise RuntimeError('Native instrumentation corruption invalidates the entire acquisition attempt')


def native_replay_failure(event, statement_ids):
    original = event.get('original')
    instrumented = event.get('instrumented')
    if instrumented is not None and statement_ids is not None:
        foreign = set(instrumented.get('coveredStatementKeys', [])) - statement_ids
        if foreign:
            return f'Instrumentation reported foreign statement IDs: {sorted(foreign)}'
    if original is None or instrumented is None:
        return None
    if original['inputs'] != instrumented['inputs']:
        return 'Instrumentation changed the frozen native input transport'

    original_observation = original['observation']
    instrumented_observation = instrumented['observation']
    if original_observation['completion'] != instrumented_observation['completion']:
        return 'Instrumentation changed native completion'
    original_representable = original_observation.get('representable', True)
    instrumented_representable = instrumented_observation.get('representable', True)
    if original_representable != instrumented_representable:
        return 'Instrumentation changed native observability or observation schema'
    if original_representable and original_observation != instrumented_observation:
        return 'Instrumentation changed native returned values or receiver state'

    return None


def saved_replay_failures(output):
    journals = [(path, path.parent.parent.name)
                for path in sorted((output / 'prepared').glob('*/preflight/replays.jsonl'))]
    journals.extend((path, path.relative_to(output / 'cells').parts[0])
                    for path in sorted((output / 'cells').rglob('replays.jsonl')))
    universes = {}
    failures = []
    for path, class_id in journals:
        if class_id not in universes:
            record = output / 'prepared' / class_id / 'class.json'
            universes[class_id] = ({statement['id'] for statement in
                                   read(record)['instrumentation']['universe']['statements']}
                                  if record.exists() else None)
        for line in path.read_text(encoding='utf-8').splitlines():
            if not line.strip():
                continue
            event = json.loads(line)
            failure = native_replay_failure(event, universes[class_id])
            if failure is not None:
                failures.append({'classId': class_id, 'nativeInfrastructureFailures': [failure],
                                 'journal': str(path), 'eventIndex': event['index']})

    return failures


def require_saved_acquisition_health(output):
    require_native_acquisition_health(output, 'saved', [])
    records = [read(path) for path in sorted((output / 'prepared').glob('*/preflight.json'))]
    records.extend(read(path) for path in sorted((output / 'cells').rglob('result.json')))
    eligibility = output / 'eligibility.json'
    if eligibility.exists():
        records.extend(read(eligibility))
    records.extend(saved_replay_failures(output))

    require_native_acquisition_health(output, 'saved', records)


def scheduled_cells(plan, budgets=BUDGETS):
    if plan['budgetsMillis'] != list(BUDGETS) or any(budget not in BUDGETS for budget in budgets):
        raise ValueError('A phase must preserve the full frozen 30/120-second protocol')

    for budget in budgets:
        for class_index, class_id in enumerate(plan['classIds']):
            for seed in plan['seeds']:
                rotation = (class_index + seed) % len(PROFILES)
                profiles = PROFILES[rotation:] + PROFILES[:rotation]
                for profile in profiles:
                    yield budget, class_id, seed, profile


def preserves_input_values(prepared, declared):
    """Kotlin writes domain defaults; every explicitly frozen value must still agree."""
    if isinstance(declared, dict):
        if not isinstance(prepared, dict):
            return False
        return all(preserves_input_values(prepared.get(key), value) for key, value in declared.items())
    if isinstance(declared, list):
        if not isinstance(prepared, list) or len(prepared) != len(declared):
            return False
        return all(preserves_input_values(actual, expected) for actual, expected in zip(prepared, declared))
    return prepared == declared


def cell_directory(output, class_id, seed, budget, profile):
    return output / 'cells' / class_id / str(seed) / str(budget) / profile


def validate_result(result, expected, prepared, universe):
    if result.get('nativeInfrastructureFailures'):
        raise ValueError('Native instrumentation corruption invalidates the acquisition attempt')

    identity = result['identity']
    for key, value in expected.items():
        if identity.get(key) != value:
            raise ValueError(f'Paired cell identity differs for {key}')

    if identity['preparedSha256'] != sha(prepared / identity['classId'] / 'class.json'):
        raise ValueError('Prepared class identity differs')
    if identity['preflightSha256'] != sha(prepared / identity['classId'] / 'preflight.json'):
        raise ValueError('Frozen preflight identity differs')
    if identity['universeSha256'] != universe['universeSha256']:
        raise ValueError('Statement universe identity differs')

    ids = {statement['id'] for statement in universe['statements']}
    previous = set()
    for point in result['checkpoints']:
        covered = set(point['coveredStatementIds'])
        uncovered = set(point['uncoveredStatementIds'])
        if point['totalStatements'] != len(ids) or covered & uncovered or covered | uncovered != ids:
            raise ValueError('Invalid exact statement numerator/denominator')
        if not previous <= covered:
            raise ValueError('Coverage history is not monotone')
        if point['searchBudgetMillis'] > identity['budgetMillis']:
            raise ValueError('Coverage checkpoint exceeds cell budget')
        previous = covered

    if not result['checkpoints'] or result['checkpoints'][-1]['searchBudgetMillis'] != identity['budgetMillis']:
        raise ValueError('Final requested-budget coverage checkpoint is absent')


def summarize(output, prepared, plan, scheduled_budgets=BUDGETS):
    require_saved_acquisition_health(output)
    manifest = read(prepared / 'manifest.json')
    rows = []
    failures = []
    pending = []
    pairs = []
    for class_id in plan['classIds']:
        record = read(prepared / class_id / 'class.json')
        universe = record['instrumentation']['universe']
        for seed in manifest['seeds']:
            for budget in BUDGETS:
                pair = []
                for profile in PROFILES:
                    path = cell_directory(output, class_id, seed, budget, profile) / 'result.json'
                    if not path.exists():
                        missing = {'classId': class_id, 'seed': seed, 'budgetMillis': budget,
                                   'profile': profile, 'reason': 'Missing completed cell'}
                        if budget in scheduled_budgets:
                            failures.append(missing)
                        else:
                            pending.append(missing)
                        continue

                    result = read(path)
                    expected = {'experimentId': manifest['experimentId'], 'toolRevision': manifest['toolRevision'],
                                'nativeFrontendRevision': manifest['nativeFrontendRevision'],
                                'manifestSha256': sha(prepared / 'manifest.json'),
                                'classId': class_id, 'seed': seed, 'budgetMillis': budget, 'profile': profile}
                    try:
                        validate_result(result, expected, prepared, universe)
                    except (KeyError, ValueError) as error:
                        failures.append({**expected, 'reason': str(error), 'path': str(path)})
                        continue

                    search = result['search']['search']
                    final = result['checkpoints'][-1]
                    row = {'classId': class_id, 'profile': profile, 'seed': seed, 'budgetMillis': budget,
                           'covered': len(final['coveredStatementIds']), 'total': final['totalStatements'],
                           'fraction': len(final['coveredStatementIds']) / final['totalStatements'],
                           'status': search['status'], 'acceptedCandidates': final['acceptedCandidates'],
                           'rejectedCandidates': final['rejectedCandidates'],
                           'searchElapsedMillis': search['searchElapsedMillis'],
                           'setupElapsedMillis': search['preparationElapsedMillis'] + search['machineSetupElapsedMillis'],
                           'replayElapsedMillis': result['replayElapsedMillis'],
                           'solverCalls': result['search'].get('solverCalls'),
                           'solverElapsedMillis': result['search'].get('solverElapsedMillis'),
                           'runtimeLimited': search['runtimeLimited'], 'engineFailed': search['engineFailed'],
                           'candidateCapReached': search['candidateCapReached'],
                           'replayErrors': result['replayErrors'], 'oracleRejections': result['oracleRejections']}
                    rows.append(row)
                    pair.append(row)

                if len(pair) == len(PROFILES):
                    pairs.append({'classId': class_id, 'seed': seed, 'budgetMillis': budget,
                                  'coveredByProfile': {row['profile']: row['covered'] for row in pair},
                                  'total': pair[0]['total'],
                                  'measurementFailures': any(row['engineFailed'] or row['runtimeLimited']
                                                            or row['candidateCapReached'] or row['replayErrors']
                                                            or row['oracleRejections'] for row in pair)})

    summary = {'schemaVersion': 1, 'experimentId': manifest['experimentId'],
               'classCount': len(plan['classIds']), 'completePairedGroups': len(pairs),
               'incompleteOrInvalidCells': failures, 'missingStrata': plan['missingStrata'],
               'scheduledBudgetsMillis': list(scheduled_budgets), 'pendingOtherPhaseCells': pending,
               'complete': bool(plan['classIds']) and not failures and not pending,
               'rows': rows, 'pairs': pairs}
    (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    if rows:
        with (output / 'coverage.csv').open('w', encoding='utf-8', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
            writer.writeheader()
            writer.writerows(rows)

    lines = [f"Experiment: {manifest['experimentId']}", '',
             f"Classes: {len(plan['classIds'])}; complete paired groups: {len(pairs)}; missing strata: {len(plan['missingStrata'])}.",
             f"Complete acquisition: {summary['complete']}; pending cells in the other phase: {len(pending)}; invalid or missing scheduled cells: {len(failures)}.",
             '', '| Class | Seed | Budget (s) | EMPTY_STOP | EMPTY_FRESH | FROZEN_STOP | FROZEN_FRESH |',
             '|---|---:|---:|---:|---:|---:|---:|']
    for pair in pairs:
        values = [f"{pair['coveredByProfile'][profile]}/{pair['total']}" for profile in PROFILES]
        lines.append(f"| {pair['classId']} | {pair['seed']} | {pair['budgetMillis'] // 1000} | " + ' | '.join(values) + ' |')
    lines.extend(['', 'These are paired observations under the frozen domains. Unsupported candidates and missing strata remain in the eligibility ledger.',
                  'Engine-suited datasets must be reported separately from the random/stratified corpus. No statistical superiority is inferred from one seed.'])
    (output / 'RESULTS.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--calls', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--selection-ledger', type=Path)
    parser.add_argument('--preflight-ms', type=int, default=30_000)
    parser.add_argument('--preflight-only', action='store_true')
    parser.add_argument('--summarize-only', action='store_true')
    parser.add_argument('--budget-ms', type=int, choices=BUDGETS,
                        help='Acquire or check one phase while retaining both budgets in the frozen plan')
    args = parser.parse_args()
    scheduled_budgets = (args.budget_ms,) if args.budget_ms is not None else BUDGETS
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    require_saved_acquisition_health(output)
    prepared = output / 'prepared'
    calls = args.calls.resolve()
    ledger = read(args.selection_ledger) if args.selection_ledger else None
    input_manifest = read(args.manifest)
    if input_manifest['budgetsMillis'] != list(BUDGETS):
        raise ValueError('Class budgets must be exactly 30 and 120 seconds')
    freeze(output / 'campaign-input.json', {'manifest': input_manifest, 'selectionLedger': ledger,
                                          'preflightMillis': args.preflight_ms})
    if not (prepared / 'manifest.json').exists():
        outcome = invoke(calls, ['class-prepare', args.manifest.resolve(), prepared],
                         output / 'prepare.log', timeout=600)
        freeze(output / 'prepare-process.json', outcome)
        if outcome['exitCode'] != 0:
            raise RuntimeError(f'Class preparation failed: {outcome}')

    manifest = read(prepared / 'manifest.json')
    if not preserves_input_values(manifest, input_manifest):
        raise ValueError('Prepared manifest differs from campaign input')
    plan_path = output / 'run-plan.json'
    if args.summarize_only:
        summary = summarize(output, prepared, read(plan_path), scheduled_budgets=scheduled_budgets)
        print(json.dumps(summary, indent=2))
        if summary['incompleteOrInvalidCells']:
            raise RuntimeError('Incomplete or invalid scheduled campaign phase; see summary.json')
        if not summary['classCount']:
            raise RuntimeError('No primary admitted classes; no representative comparison was run')
        return

    eligibility = []
    eligible = set()
    for case in manifest['classes']:
        class_id = case['classId']
        directory = prepared / class_id
        failed = directory / 'preparation-failure.json'
        if failed.exists():
            eligibility.append({'classId': class_id, 'eligible': False, 'preparationFailure': read(failed)})
            continue
        preflight = directory / 'preflight.json'
        if not preflight.exists():
            print(f'Preflight {class_id}', flush=True)
            process = invoke(calls, ['class-preflight', prepared, class_id, args.preflight_ms],
                             output / 'logs' / f'{class_id}-preflight.log', timeout=600 + args.preflight_ms // 1000)
            freeze(output / 'processes' / f'{class_id}-preflight.json', process)
        if preflight.exists():
            outcome = read(preflight)
            primary_admitted = admits_primary_comparison(outcome)
            eligibility.append({'classId': class_id, 'eligible': outcome['eligible'],
                                'primaryInferenceEligible': primary_admitted,
                                'diagnosticReady': outcome['diagnosticReady'],
                                'nativeSmoke': outcome['nativeSmoke'],
                                'sourceAdmissionFailures': outcome['sourceAdmissionFailures'],
                                'nativeInfrastructureFailures': outcome['nativeInfrastructureFailures'],
                                'correctnessFrontierFailures': case.get('correctnessFrontierFailures', []),
                                'missingRequiredScenarios': outcome['missingRequiredScenarios'],
                                'reasons': outcome['reasons'], 'preflightSha256': sha(preflight)})
            if primary_admitted:
                eligible.add(class_id)
        else:
            eligibility.append({'classId': class_id, 'eligible': False, 'reasons': ['Preflight failed before final record; see process log']})

    chosen, missing = select_classes(ledger, eligible)
    freeze(output / 'eligibility.json', eligibility)
    require_native_acquisition_health(output, 'preflight', eligibility)
    plan = {'schemaVersion': 1, 'admission': 'profile-independent-source-native-v1',
            'manifestSha256': sha(prepared / 'manifest.json'), 'classIds': chosen,
            'missingStrata': missing, 'profiles': list(PROFILES), 'budgetsMillis': list(BUDGETS),
            'seeds': manifest['seeds'], 'order': 'all 30s cells then all 120s; seeded rotation of profiles; sequential processes'}
    freeze(plan_path, plan)
    print(f'Frozen source/native admission: {len(chosen)} primary classes; {len(missing)} missing strata.', flush=True)
    if args.preflight_only:
        return

    for budget, class_id, seed, profile in scheduled_cells(plan, budgets=scheduled_budgets):
        directory = cell_directory(output, class_id, seed, budget, profile)
        if (directory / 'result.json').exists():
            require_native_acquisition_health(output, 'measurement', [read(directory / 'result.json')])
            continue
        print(f'Measure {class_id} {profile} seed={seed} budget={budget}ms', flush=True)
        process = invoke(calls, ['class-cell', prepared, class_id, profile, seed, budget, directory],
                         output / 'logs' / f'{class_id}-{seed}-{budget}-{profile}.log', timeout=600 + budget // 1000)
        freeze(output / 'processes' / f'{class_id}-{seed}-{budget}-{profile}.json', process)
        if (directory / 'result.json').exists():
            require_native_acquisition_health(output, 'measurement', [read(directory / 'result.json')])

    summary = summarize(output, prepared, plan, scheduled_budgets=scheduled_budgets)
    print(f"Finished: {len(summary['rows'])} cells, {summary['completePairedGroups']} complete paired groups.", flush=True)
    if summary['incompleteOrInvalidCells']:
        raise RuntimeError('Incomplete or invalid scheduled campaign phase; see summary.json')
    if not chosen:
        raise RuntimeError('No primary admitted classes; no representative comparison was run')


if __name__ == '__main__':
    main()
