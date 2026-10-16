import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { executeWitness, type WitnessNativeReplay, type WitnessTarget } from '../src/external-witness.js';
import { encodeJsValue, ProtocolError } from '../src/js-value.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../src/test/resources');
const module = 'properties/witness/ExternalWitness.ts';
const sha256 = createHash('sha256').update(await readFile(path.join(root, module))).digest('hex');
const binding = (pointId: string) => ({ pointId, contextId: 'original', revision: 'v1', occurrence: 0 });
const entryModule = 'properties/inference/EntryRelations.ts';
const entryHash = createHash('sha256').update(await readFile(path.join(root, entryModule))).digest('hex');

function entryRequest(
  left: number, right: number, template: 'STRICT_EQUAL' | 'LESS_OR_EQUAL' = 'LESS_OR_EQUAL', predicate = 'always',
) {
  const target: WitnessTarget = {
    id: 'entry-relation', role: 'HYPOTHESIS',
    binding: { pointId: 'entry-relation', contextId: 'original-entry', revision: entryHash, occurrence: 0 },
    entryRelation: { leftInputIndex: 0, rightInputIndex: 1, template },
  };
  const domain = { kind: 'number', min: { value: 'negative-infinity' }, max: { value: 'positive-infinity' }, allowNaN: true };

  return {
    manifest: {
      propertyId: 'witness.entry', predicate: { module: entryModule, exportName: predicate, executionKind: 'sync' },
      inputs: [{ name: 'left', domain }, { name: 'right', domain }], assertions: [],
    },
    sourceRoots: [root], examples: [], numRuns: 1, timeoutMillis: 5000,
    witness: {
      mode: 'replay', target, inputs: [encodeJsValue(left), encodeJsValue(right)],
      sources: [{ module: entryModule, sha256: entryHash }],
    },
  };
}

test('adapter-owned entry comparisons retain JavaScript NaN signed-zero and infinity semantics', async () => {
  const cases = [
    [-0, 0, 'STRICT_EQUAL', true],
    [Number.NaN, Number.NaN, 'STRICT_EQUAL', false],
    [Number.POSITIVE_INFINITY, Number.POSITIVE_INFINITY, 'STRICT_EQUAL', true],
    [Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY, 'LESS_OR_EQUAL', true],
    [Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY, 'LESS_OR_EQUAL', false],
    [Number.NaN, 0, 'LESS_OR_EQUAL', false],
  ] as const;

  for (const [left, right, template, holds] of cases) {
    const replay = (await executeWitness(entryRequest(left, right, template))).witnessReplay;

    assert.equal(replay?.outcome, 'HOLDS');
    assert.equal(replay?.events[0]?.holds, holds);
    assert.equal(replay?.targetMatched, !holds);
  }
});

test('entry refutation snapshots precede callback mutation and only admitted inputs receive an event', async () => {
  const mutation = (await executeWitness(entryRequest(80, 0, 'LESS_OR_EQUAL', 'entryMutation'))).witnessReplay;
  const rejected = entryRequest(80, 0);
  const guarded = { ...rejected, manifest: {
    ...rejected.manifest, precondition: { module: entryModule, exportName: 'ordered', executionKind: 'sync' },
  } };

  const skipped = (await executeWitness(guarded)).witnessReplay;

  assert.equal(mutation?.outcome, 'HOLDS');
  assert.equal(mutation?.events[0]?.holds, false);
  assert.equal(mutation?.targetMatched, true);
  assert.equal(skipped?.admission, 'PRECONDITION_REJECTED');
  assert.equal(skipped?.targetMatched, false);
  assert.deepEqual(skipped?.events, []);
});

test('native reduction retains the same admitted entry relation and holding original oracle', async () => {
  const initial = entryRequest(80, 0);
  const domain = { kind: 'integer', min: 0, max: 100 };
  const selected = { ...initial, manifest: {
    ...initial.manifest, inputs: [{ name: 'left', domain }, { name: 'right', domain }],
  } };
  const expected = (await executeWitness(selected)).witnessReplay;

  const reduced = (await executeWitness({ ...selected, witness: { ...selected.witness, mode: 'reduce', expected } })).witnessReduction;
  const final = (await executeWitness({ ...selected, witness: { ...selected.witness, inputs: reduced?.inputs } })).witnessReplay;

  assert.equal(reduced?.status, 'EXHAUSTED');
  assert.ok((reduced?.acceptedShrinks ?? 0) > 0);
  assert.deepEqual(reduced?.inputs, [encodeJsValue(1), encodeJsValue(0)]);
  assert.equal(final?.outcome, 'HOLDS');
  assert.equal(final?.targetMatched, true);
  assert.deepEqual(final?.events[0]?.binding, initial.witness.target.binding);
});

test('entry target indices templates context and occurrence are validated before execution', async () => {
  const original = entryRequest(80, 0);
  const target = original.witness.target;
  const cases = [
    { ...target, entryRelation: { ...target.entryRelation!, leftInputIndex: -1 } },
    { ...target, entryRelation: { ...target.entryRelation!, rightInputIndex: 2 } },
    { ...target, entryRelation: { ...target.entryRelation!, template: 'COERCING_EQUAL' } },
    { ...target, binding: { ...target.binding!, contextId: 'helper' } },
    { ...target, binding: { ...target.binding!, occurrence: 1 } },
  ];

  for (const selected of cases) {
    await assert.rejects(executeWitness({ ...original, witness: { ...original.witness, target: selected } }),
      (error: unknown) => error instanceof ProtocolError && error.code === 'protocol.request.invalid');
  }
});

function request(predicate: string, target: WitnessTarget = { id: predicate, role: 'PROPERTY' }, inputs = [encodeJsValue(80)]) {
  return {
    manifest: {
      propertyId: 'witness.test',
      predicate: { module, exportName: predicate, executionKind: 'sync' },
      inputs: [{ name: 'value', domain: { kind: 'integer', min: 0, max: 100 } }],
      assertions: target.role === 'PROPERTY' && target.binding !== undefined
        ? [{ id: target.id, source: { module, line: 1, column: 1 }, operands: [] }] : [],
    },
    sourceRoots: [root], examples: [], numRuns: 1, timeoutMillis: 5000,
    witness: { mode: 'replay', target, inputs, sources: [{ module, sha256 }] },
  };
}

async function reduced(predicate: string, target?: WitnessTarget) {
  const replayRequest = request(predicate, target);
  const original = (await executeWitness(replayRequest)).witnessReplay as WitnessNativeReplay;
  const reductionRequest = { ...replayRequest, witness: { ...replayRequest.witness, mode: 'reduce', expected: original } };
  const result = (await executeWitness(reductionRequest)).witnessReduction;

  assert.equal(result?.status, 'EXHAUSTED');
  assert.ok(result.acceptedShrinks > 0);
  assert.ok(result.attempts > 1);

  return result;
}

test('external integer actually shrinks through native fast-check and retains the original failure', async () => {
  const result = await reduced('threshold');

  assert.deepEqual(result.inputs, [encodeJsValue(10)]);
});

test('a different failed named check cannot replace the target while shrinking', async () => {
  const result = await reduced('namedChecks', { id: 'threshold-check', role: 'PROPERTY', binding: binding('threshold-check') });

  assert.deepEqual(result.inputs, [encodeJsValue(15)]);
});

test('guarded hypothesis reduction keeps the complete original oracle holding', async () => {
  const target: WitnessTarget = { id: 'hypothesis', role: 'HYPOTHESIS', binding: binding('hypothesis') };
  const result = await reduced('hypothesis', target);
  const final = (await executeWitness(request('hypothesis', target, result.inputs))).witnessReplay;

  assert.deepEqual(result.inputs, [encodeJsValue(5)]);
  assert.equal(final?.outcome, 'HOLDS');
  assert.equal(final?.targetMatched, true);
});

test('context revision occurrence and false guard must match exactly', async () => {
  const target: WitnessTarget = { id: 'hypothesis', role: 'HYPOTHESIS', binding: binding('hypothesis') };
  for (const [predicate, selectedTarget, inputs] of [
    ['differentContext', target, [encodeJsValue(80)]],
    ['hypothesis', { ...target, binding: { ...target.binding!, revision: 'v2' } }, [encodeJsValue(80)]],
    ['hypothesis', target, [encodeJsValue(0)]],
    ['repeatedHypothesis', target, [encodeJsValue(80)]],
  ] as const) {
    const result = (await executeWitness(request(predicate, selectedTarget, [...inputs]))).witnessReplay;

    assert.equal(result?.targetMatched, false);
  }

  const matched = (await executeWitness(request('repeatedHypothesis', {
    ...target, binding: { ...target.binding!, occurrence: 1 },
  }))).witnessReplay;

  assert.equal(matched?.targetMatched, true);
});

test('precondition and contract errors stay distinct from predicate exceptions', async () => {
  const preconditionRequest = { ...request('threshold'), manifest: {
    ...request('threshold').manifest,
    precondition: { module, exportName: 'preconditionError', executionKind: 'sync' },
  } };
  const error = (await executeWitness(preconditionRequest)).witnessReplay;
  const thrown = (await executeWitness(request('predicateError'))).witnessReplay;
  const contract = (await executeWitness(request('contractError'))).witnessReplay;

  assert.equal(error?.outcome, 'ERROR');
  assert.equal(error?.targetMatched, false);
  assert.equal(thrown?.outcome, 'THREW');
  assert.equal(thrown?.failure?.errorName, 'TypeError');
  assert.equal(contract?.outcome, 'ERROR');
});

test('source drift and repeated outcome drift are retained explicitly', async () => {
  const changed = request('threshold');
  changed.witness.sources[0]!.sha256 = '0'.repeat(64);

  const stale = (await executeWitness(changed)).witnessReplay;

  assert.equal(stale?.admission, 'UNKNOWN');
  assert.equal(stale?.outcome, 'UNKNOWN');
  assert.equal(stale?.diagnostic, 'entrypoint.source-hash.mismatch');
  assert.equal(stale?.targetMatched, false);

  const replay = (await executeWitness(request('nondeterministic'))).witnessReplay;

  assert.equal(replay?.diagnostic, 'nondeterministic');
});

test('native chained arbitrary without external context reports unsupported shrinking', async () => {
  const base = request('supportedIndex', undefined, [encodeJsValue([80, 90]), encodeJsValue(1)]);
  const dependent = { ...base, manifest: { ...base.manifest,
    inputs: [
      { name: 'values', domain: { kind: 'array', element: { kind: 'integer', min: 0, max: 100 }, minLength: 1, maxLength: 3 } },
      { name: 'index', domain: { kind: 'integer', min: 0, max: 2 } },
    ], generator: { id: 'index', kind: 'array-index', arrayInputIndex: 0, indexInputIndex: 1 },
  } };
  const original = (await executeWitness(dependent)).witnessReplay;
  const result = (await executeWitness({ ...dependent, witness: { ...dependent.witness, mode: 'reduce', expected: original } })).witnessReduction;

  assert.equal(result?.status, 'UNSUPPORTED');
  assert.equal(result?.attempts, 0);
});

test('each invocation isolates mutating input from replay and native shrinking', async () => {
  const base = request('mutation', undefined, [encodeJsValue([80, 90, 100, 70])]);
  const mutation = { ...base, manifest: { ...base.manifest, inputs: [{ name: 'values', domain: {
    kind: 'array', element: { kind: 'integer', min: 0, max: 100 }, minLength: 0, maxLength: 5,
  } }] } };
  const original = (await executeWitness(mutation)).witnessReplay;
  const result = (await executeWitness({ ...mutation, witness: { ...mutation.witness, mode: 'reduce', expected: original } })).witnessReduction;

  assert.equal(original?.outcome, 'FALSE');
  assert.equal(original?.diagnostic, undefined);
  assert.deepEqual(result?.inputs, [encodeJsValue([0, 0, 0])]);
  assert.deepEqual(mutation.witness.inputs, [encodeJsValue([80, 90, 100, 70])]);
});

test('declared domain rejects values independently of shrinking capability', async () => {
  for (const input of [encodeJsValue(101), encodeJsValue(-0), encodeJsValue(Number.NaN), encodeJsValue('80')]) {
    const replay = (await executeWitness(request('threshold', undefined, [input]))).witnessReplay;

    assert.equal(replay?.admission, 'DOMAIN_REJECTED');
    assert.equal(replay?.outcome, 'SKIPPED');
    assert.equal(replay?.targetMatched, false);
  }
});

test('shrinking nondeterminism and oracle contract errors return explicit failed reductions', async () => {
  const cases = [
    ['nondeterministicShrink', 'Nondeterministic shrinking invocation'],
    ['shrinkError', 'entrypoint.result.invalid'],
  ] as const;

  for (const [predicate, diagnostic] of cases) {
    const base = request(predicate);
    const original = (await executeWitness(base)).witnessReplay;
    const result = (await executeWitness({ ...base, witness: { ...base.witness, mode: 'reduce', expected: original } })).witnessReduction;

    assert.equal(original?.outcome, 'FALSE');
    assert.equal(result?.status, 'FAILED');
    assert.equal(result?.diagnostic, diagnostic);
    assert.equal(result?.inputs, undefined);
  }
});

test('invalid witness events record an error without interrupting the complete original oracle', async () => {
  const result = (await executeWitness(request('eventError'))).witnessReplay;

  assert.equal(result?.outcome, 'ERROR');
  assert.equal(result?.diagnostic, 'witness.events.invalid');
  assert.equal(result?.targetMatched, false);
  assert.equal(result?.events.at(-1)?.binding.pointId, 'oracle-completed');
});

test('malformed witness operation and wrong symbolic failure kind cannot confirm a target', async () => {
  for (const witness of [null, [], {}, { ...request('threshold').witness, target: null }]) {
    await assert.rejects(executeWitness({ ...request('threshold'), witness }), ProtocolError);
  }
  const replay = (await executeWitness(request('predicateError', {
    id: 'predicateError', role: 'PROPERTY', expectedOutcome: 'FALSE',
  }))).witnessReplay;

  assert.equal(replay?.targetMatched, false);
  assert.equal(replay?.outcome, 'THREW');
});
