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
