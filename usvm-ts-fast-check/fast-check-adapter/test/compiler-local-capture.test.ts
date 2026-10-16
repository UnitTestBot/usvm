import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { executeProperty } from '../src/execute-property.js';
import { encodeJsValue, ProtocolError } from '../src/js-value.js';
import type { CompilerNumericLocalBinding, ObservationPointRequest } from '../src/observe-property.js';

async function fixture(
  run: (request: ReturnType<typeof buildRequest>, root: string) => Promise<void>,
  callback = "observer?.(4, 'result', result);",
  withCallbackParameter = true,
): Promise<void> {
  const root = await realpath(await mkdtemp(path.join(tmpdir(), 'pbt-compiler-local-')));
  const parameter = withCallbackParameter ? ', observer?: (node: number, local: string, value: unknown) => void' : '';
  const source = `
    let calls = 0;
    function underlying(value: number): number {
      calls += 1;
      const hook = (globalThis as any)[Symbol.for('org.usvm.ts.pbt.observe')];
      hook?.('result', 999);
      return value;
    }
    export function predicate(value: number): boolean {
      calls = 0;
      const result = underlying(value);
      return (result === value || (result !== result && value !== value)) && calls === 1;
    }
    export function companion(value: number${parameter}) {
      calls = 0;
      const result = underlying(value);
      ${callback}
      return { passed: (result === value || (result !== result && value !== value)) && calls === 1,
        checkId: '', actual: true, expected: true };
    }
  `;
  await writeFile(path.join(root, 'generated.ts'), source);
  const hash = createHash('sha256').update(source).digest('hex');
  try {
    await run(buildRequest(root, hash), root);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

function buildRequest(root: string, hash: string) {
  const callSite = { module: 'generated.ts', line: 10, column: 22 };
  const source = { module: 'generated.ts', line: 11, column: 14 };
  const binding: CompilerNumericLocalBinding = {
    producer: 'usvm-kotlin-dsl-v1', abi: 'numeric-local-callback-v1', programId: 'compiler.local',
    nodeId: 4, localName: 'result', predicateExportName: 'predicate', companionExportName: 'companion',
    predicateAssignment: { module: 'generated.ts', line: 10, column: 7 },
    companionAssignment: { module: 'generated.ts', line: 15, column: 7 },
    sourceSha256: hash, occurrence: 0,
  };
  const result: ObservationPointRequest = {
    id: 'result', assertionId: 'identity', operandId: 'result', source, callSite, kind: 'return', compilerLocal: binding,
  };
  const input: ObservationPointRequest = {
    id: 'input', assertionId: 'identity', operandId: 'input', source, callSite, kind: 'entry-argument', inputIndex: 0,
  };

  return {
    manifest: {
      propertyId: 'compiler.local',
      predicate: { module: 'generated.ts', exportName: 'predicate', executionKind: 'sync', checkResultExportName: 'companion' },
      inputs: [{ name: 'value', domain: { kind: 'integer', min: 1, max: 10 } }],
      assertions: [{ id: 'identity', source, testedCall: callSite, operands: [{ id: 'result', source }, { id: 'input', source }] }],
    },
    sourceRoots: [root], examples: [[encodeJsValue(7)]], seed: 42, numRuns: 2, timeoutMillis: 5000,
    observationRequest: {
      points: [result, input], sources: [{ module: 'generated.ts', sha256: hash }],
      maxInvocations: 8, maxPointsPerInvocation: 4, maxArrayElements: 2, maxBytes: 65_536,
    },
  };
}

test('declared compiler callback captures the computed result and public hooks cannot replace it', async () => {
  await fixture(async (request) => {
    const result = (await executeProperty(request)).result;

    assert.equal(result.status, 'success');
    assert.equal(result.observations?.buildStatus, 'unverified');
    for (const invocation of result.observations!.invocations) {
      const value = invocation.points.filter((event) => event.pointId === 'result');
      const input = invocation.points.find((event) => event.pointId === 'input');

      assert.equal(value.length, 1);
      assert.deepEqual(value[0]?.value.value, input?.value.value);
      assert.deepEqual(value[0]?.compilerLocal, request.observationRequest.points[0]?.compilerLocal);
      assert.equal(value[0]?.occurrence, 0);
      assert.ok(value[0]!.eventOrdinal > input!.eventOrdinal);
    }
  });
});

test('missing callback remains missing even when original code invokes the public hook', async () => {
  await fixture(async (request) => {
    const result = (await executeProperty(request)).result;

    assert.equal(result.status, 'success');
    assert.ok(result.observations!.invocations.every((invocation) =>
      invocation.points.every((event) => event.pointId !== 'result')));
  }, '');
});

test('compiler callback snapshots preserve NaN signed zero and infinities', async () => {
  await fixture(async (request) => {
    const values = [Number.NaN, -0, Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY];
    const selected = { ...request, manifest: { ...request.manifest, inputs: [{ name: 'value', domain: {
      kind: 'number', min: { value: 'negative-infinity' }, max: { value: 'positive-infinity' }, allowNaN: true,
    } }] }, examples: values.map((value) => [encodeJsValue(value)]), numRuns: values.length + 2 };

    const result = (await executeProperty(selected)).result;
    const explicit = result.observations!.invocations.filter((invocation) => invocation.phase === 'explicit');

    assert.equal(result.status, 'success');
    assert.equal(explicit.length, values.length);
    assert.deepEqual(explicit.map((invocation) => invocation.points.find((event) => event.pointId === 'result')?.value.value),
      values.map(encodeJsValue));
  });
});

test('wrong compiler receipt and unsupported numeric value preserve the original oracle and record unavailable points', async () => {
  const cases = [
    ["observer?.(5, 'result', result);", 'compiler-local-identity-mismatch'],
    ["observer?.(4, 'other', result);", 'compiler-local-identity-mismatch'],
    ["observer?.(4, 'result', 'unsupported');", 'compiler-local-numeric-required'],
  ];
  for (const [callback, reason] of cases) {
    await fixture(async (request) => {
      const result = (await executeProperty(request)).result;
      const events = result.observations!.invocations.flatMap((invocation) =>
        invocation.points.filter((event) => event.pointId === 'result'));

      assert.equal(result.status, 'success');
      assert.ok(events.length > 0);
      assert.ok(events.every((event) => event.value.status === 'unsupported' && event.value.reason === reason));
    }, callback);
  }
});

test('repeated compiler callback receipts keep occurrence identity', async () => {
  await fixture(async (request) => {
    const result = (await executeProperty(request)).result;
    const events = result.observations!.invocations[0]!.points.filter((event) => event.pointId === 'result');

    assert.equal(result.status, 'success');
    assert.deepEqual(events.map((event) => event.occurrence), [0, 1]);
  }, "observer?.(4, 'result', result); observer?.(4, 'result', result);");
});

test('compiler declaration validates ABI producer program exports source occurrence and kind', async () => {
  await fixture(async (request) => {
    const point = request.observationRequest.points[0]!;
    const binding = point.compilerLocal!;
    const invalidBindings = [
      { ...binding, producer: 'arbitrary-source' }, { ...binding, abi: 'other-callback' },
      { ...binding, programId: 'other-property' }, { ...binding, predicateExportName: 'other' },
      { ...binding, companionExportName: 'other' }, { ...binding, sourceSha256: '0'.repeat(64) },
      { ...binding, occurrence: 1 }, { ...binding, predicateAssignment: binding.companionAssignment },
    ];
    const invalidPoints: ObservationPointRequest[] = [
      ...invalidBindings.map((compilerLocal) => ({ ...point, compilerLocal })),
      { ...point, kind: 'intermediate' },
    ];
    for (const selected of invalidPoints) {
      await assert.rejects(executeProperty({ ...request, observationRequest: {
        ...request.observationRequest, points: [selected, request.observationRequest.points[1]],
      } }), (error: unknown) => error instanceof ProtocolError && error.code === 'protocol.request.invalid');
    }
  });
});

test('changed selected source and incompatible companion signature cannot supply capture', async () => {
  await fixture(async (request, root) => {
    await writeFile(path.join(root, 'generated.ts'), 'export function predicate() { return true; }');

    await assert.rejects(executeProperty(request), (error: unknown) =>
      error instanceof ProtocolError && error.code === 'entrypoint.source-hash.mismatch');
  });
  await fixture(async (request) => {
    await assert.rejects(executeProperty(request), (error: unknown) =>
      error instanceof ProtocolError && error.code === 'protocol.request.invalid');
  }, '', false);
});
