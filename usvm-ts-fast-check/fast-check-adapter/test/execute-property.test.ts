import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, mkdir, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { tsImport } from 'tsx/esm/api';
import { installObservationHook, ObservationRecorder, type ObservationRequest } from '../src/observe-property.js';
import { encodeJsValue, ProtocolError } from '../src/js-value.js';
import {
  executeProperty,
  type FastCheckExecutionRequest,
  type FastCheckRunResult,
} from '../src/execute-property.js';

test('pinned fast-check callback shim preserves length assertion outcomes on bounded arrays', async () => {
  const fixture = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)),
    '../../../src/test/resources/properties/real/ArrayArbitraryProperty.ts',
  );
  const property = await tsImport(fixture, import.meta.url) as {
    AssertionError: new (message: string) => Error;
    expect(values: number[]): { toHaveLength(expected: number): void };
    originalUniqueAssertion(values: number[]): void;
    originalUniqueOracle(values: number[]): boolean;
  };
  const cases = [[], [0], [0, 1], [1, 1], [0, 1, 0], [10, 9, 8, 7], [10, 10, 10, 10]];

  for (const values of cases) {
    const expected = values.length === new Set(values).size;

    if (expected) {
      assert.equal(property.originalUniqueAssertion(values), undefined);
      assert.equal(property.originalUniqueOracle(values), true);
    } else {
      assert.throws(() => property.originalUniqueAssertion(values), property.AssertionError);
      assert.throws(() => property.originalUniqueOracle(values), property.AssertionError);
    }
  }
});

test('assertion shim evaluates expected operand before reading actual length and preserves thrown values', async () => {
  const fixture = path.resolve(
    path.dirname(fileURLToPath(import.meta.url)),
    '../../../src/test/resources/properties/real/ArrayArbitraryProperty.ts',
  );
  const property = await tsImport(fixture, import.meta.url) as {
    expect(values: number[]): { toHaveLength(expected: number): void };
  };
  const order: string[] = [];
  const value = {
    get length(): number {
      order.push('actual');
      return 1;
    },
  } as number[];
  const expected = (): number => {
    order.push('expected');
    return 1;
  };

  property.expect(value).toHaveLength(expected());

  assert.deepEqual(order, ['expected', 'actual']);

  const thrown = new Error('length getter');
  const throwing = {
    get length(): number {
      throw thrown;
    },
  } as number[];

  assert.throws(() => property.expect(throwing).toHaveLength(1), (error) => error === thrown);
});

test('rejects array-index manifests that conflict with declared input domains before sampling', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const array = (minLength: number, maxLength: number) => ({
      kind: 'array',
      element: { kind: 'integer', min: 0, max: 1 },
      minLength,
      maxLength,
    });
    const invalidDomains = [
      [array(1, 3), { kind: 'integer', min: 99, max: 99 }],
      [array(0, 3), { kind: 'integer', min: 0, max: 2 }],
      [array(1, 33), { kind: 'integer', min: 0, max: 32 }],
    ];

    for (const inputDomains of invalidDomains) {
      const request = executionRequest(sourceRoot, 'alwaysTrue', { inputDomains });
      request.manifest.generator = {
        id: 'values.valid-index',
        kind: 'array-index',
        arrayInputIndex: 0,
        indexInputIndex: 1,
      };

      await assert.rejects(executeProperty(request), (error: unknown) =>
        error instanceof ProtocolError && error.path === 'manifest.generator');
    }
  });
});

test('bounded explicit observation preserves special values and refuses proxies, accessors, and aliases', () => {
  const request: ObservationRequest = {
    points: [{
      id: 'result',
      assertionId: 'assertion',
      operandId: 'result',
      source: { module: 'property.ts', line: 1, column: 1 },
      callSite: { module: 'property.ts', line: 1, column: 1 },
      kind: 'intermediate',
    }],
    sources: [{ module: 'property.ts', sha256: 'a'.repeat(64) }],
    maxInvocations: 2,
    maxPointsPerInvocation: 4,
    maxArrayElements: 4,
    maxBytes: 65_536,
  };
  const recorder = new ObservationRecorder('property', request);
  installObservationHook();
  const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.observe')] as
    (pointId: string, value: unknown) => unknown;
  const proxy = new Proxy([1], {});
  let getterCalls = 0;
  const accessor = [1];
  Object.defineProperty(accessor, '0', { get: () => { getterCalls += 1; return 1; } });
  const shared = [1];

  recorder.run([Number.NaN, -0], 'unknown', () => {
    assert.equal(hook('result', Number.POSITIVE_INFINITY), Number.POSITIVE_INFINITY);
    assert.equal(hook('result', proxy), proxy);
    assert.equal(hook('result', accessor), accessor);
    hook('result', [shared, shared]);
  });
  hook('result', 7);

  const [first] = recorder.result.invocations;
  assert.ok(first);
  assert.deepEqual(first.input.value, { kind: 'array', elements: [
    { kind: 'number', value: 'nan' },
    { kind: 'number', value: 'finite', bits: '8000000000000000' },
  ] });
  assert.deepEqual(first.points.map((event) => event.value.status),
    ['captured', 'unsupported', 'unsupported', 'unsupported']);
  assert.deepEqual(first.points.slice(1).map((event) => event.value.reason),
    ['proxy', 'accessor', 'alias-or-cycle']);
  assert.equal(getterCalls, 0);
  assert.equal(recorder.result.invocations.length, 1);
});

test('observation limits report dropped invocations and points without changing returned values', () => {
  const request: ObservationRequest = {
    points: [{
      id: 'point', assertionId: 'assertion', operandId: 'point',
      source: { module: 'property.ts', line: 1, column: 1 },
      callSite: { module: 'property.ts', line: 1, column: 1 },
      kind: 'intermediate',
    }],
    sources: [{ module: 'property.ts', sha256: 'a'.repeat(64) }],
    maxInvocations: 1,
    maxPointsPerInvocation: 1,
    maxArrayElements: 2,
    maxBytes: 1024,
  };
  const recorder = new ObservationRecorder('property', request);
  installObservationHook();
  const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.observe')] as
    (pointId: string, value: unknown) => unknown;

  recorder.run([1], 'unknown', () => {
    const large = 'x'.repeat(2048);
    assert.equal(hook('point', large), large);
    hook('point', 4);
  });
  recorder.run([2], 'unknown', () => hook('point', 5));

  assert.equal(recorder.result.invocations[0]?.input.status, 'captured');
  assert.equal(recorder.result.invocations[0]?.points[0]?.value.status, 'truncated');
  assert.equal(recorder.result.droppedPoints, 1);
  assert.equal(recorder.result.droppedInvocations, 1);
});

test('repeated point occurrences and a numeric tested return stay distinct from predicate outcome', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const baselineRequest = executionRequest(sourceRoot, 'repeatedPoint');
    baselineRequest.examples = [[encodeJsValue(2)]];
    baselineRequest.numRuns = 1;
    const point = { module: 'properties.ts', line: 1, column: 1 };
    baselineRequest.manifest.assertions = [{
      id: 'loop-result', source: point, testedCall: point,
      operands: [{ id: 'input', source: point }, { id: 'step', source: point },
        { id: 'tested-return', source: point }],
    }];
    const observedRequest: FastCheckExecutionRequest = {
      ...baselineRequest,
      observationRequest: {
        points: [
          { id: 'input', assertionId: 'loop-result', operandId: 'input', source: point, callSite: point,
            kind: 'argument', inputIndex: 0 },
          { id: 'step', assertionId: 'loop-result', operandId: 'step', source: point, callSite: point,
            kind: 'intermediate' },
          { id: 'tested-return', assertionId: 'loop-result', operandId: 'tested-return',
            source: point, callSite: point, kind: 'return' },
        ],
        sources: [{ module: 'properties.ts', sha256: createHash('sha256').update(PROPERTY_MODULE_SOURCE).digest('hex') }],
        maxInvocations: 64, maxPointsPerInvocation: 8, maxArrayElements: 16, maxBytes: 65_536,
      },
    };

    const baseline = await executeProperty(baselineRequest);
    const observed = await executeProperty(observedRequest);

    const { observations, ...observedResult } = observed.result;
    assert.deepEqual(semanticResult(observedResult), semanticResult(baseline.result));
    assert.ok(observations);
    const first = observations.invocations[0];
    assert.ok(first);
    assert.equal(first.phase, 'explicit');
    assert.equal(first.outcome, 'false');
    assert.deepEqual(first.points.map((event) => [event.pointId, event.occurrence, event.eventOrdinal]), [
      ['input', 0, 0], ['step', 0, 1], ['step', 1, 2], ['step', 2, 3], ['tested-return', 0, 4],
    ]);
    assert.deepEqual(first.points.map((event) => event.value.value), [
      encodeJsValue(2), encodeJsValue(0), encodeJsValue(1), encodeJsValue(3), encodeJsValue(42),
    ]);
  });
});

test('observation preserves mutation, special values, throws, and short-circuit outcomes', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const nestedArray = { kind: 'array', element: { kind: 'array',
      element: { kind: 'integer', min: 1, max: 1 }, minLength: 1, maxLength: 1 },
      minLength: 1, maxLength: 1 };
    const cases = [
      { exportName: 'mutatesNestedArrayToObject', domain: nestedArray },
      { exportName: 'throwsInput', domain: constantDomain('thrown-value') },
      { exportName: 'recognizesNegativeZero', domain: constantDomain(-0) },
      { exportName: 'shortCircuitsObservation', domain: constantDomain(1) },
    ];
    const point = { module: 'properties.ts', line: 1, column: 1 };
    const sourceHash = createHash('sha256').update(PROPERTY_MODULE_SOURCE).digest('hex');

    for (const entry of cases) {
      const baselineRequest = executionRequest(sourceRoot, entry.exportName, { inputDomain: entry.domain });
      baselineRequest.numRuns = 1;
      baselineRequest.manifest.assertions = [{ id: 'case', source: point, testedCall: point,
        operands: [{ id: 'side-effect', source: point }] }];
      const observedRequest: FastCheckExecutionRequest = {
        ...baselineRequest,
        observationRequest: {
          points: [{ id: 'side-effect', assertionId: 'case', operandId: 'side-effect',
            source: point, callSite: point, kind: 'intermediate' }],
          sources: [{ module: 'properties.ts', sha256: sourceHash }],
          maxInvocations: 64, maxPointsPerInvocation: 8, maxArrayElements: 16, maxBytes: 65_536,
        },
      };

      const baseline = await executeProperty(baselineRequest);
      const observed = await executeProperty(observedRequest);

      const { observations, ...observedResult } = observed.result;
      assert.deepEqual(semanticResult(observedResult), semanticResult(baseline.result), entry.exportName);
      assert.ok(observations);
      assert.equal(observations.invocations[0]?.input.status, 'captured');
      if (entry.exportName === 'shortCircuitsObservation') {
        assert.equal(observations.invocations[0]?.points.length, 0);
      }
    }
  });
});

test('executes a synchronous TypeScript predicate with deterministic success details', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'alwaysTrue');

    const first = await executeProperty(request);
    const second = await executeProperty(request);

    assert.equal(first.status, 'ok');
    assert.equal(first.result.status, 'success');
    assert.equal(first.result.seed, 42);
    assert.equal(first.result.numRuns, 20);
    assert.equal(first.result.counterexample, null);
    assert.deepEqual(semanticResult(first.result), semanticResult(second.result));
  });
});

test('returns a shrunk counterexample and replays it with the reported seed and path', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const first = await executeProperty(executionRequest(sourceRoot, 'isNegative'));

    assert.equal(first.result.status, 'failure');
    assert.equal(first.result.failure?.kind, 'property');
    assert.ok(first.result.counterexample);
    assert.ok(first.result.replayPath);

    const replay = await executeProperty({
      ...executionRequest(sourceRoot, 'isNegative'),
      replayPath: first.result.replayPath,
      seed: first.result.seed,
    });

    assert.deepEqual(replay.result.counterexample, first.result.counterexample);
    assert.equal(replay.result.replayPath, first.result.replayPath);
  });
});

test('supports asynchronous predicates and preconditions', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'asyncAlwaysTrue', {
      predicateExecutionKind: 'async',
      precondition: {
        module: 'properties.ts',
        exportName: 'asyncIsOne',
        executionKind: 'async',
      },
      inputDomain: { kind: 'integer', min: 0, max: 1 },
    });

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'success');
    assert.equal(response.result.numRuns, 20);
    assert.ok(response.result.numSkips > 0);
  });
});

test('classifies exhausted preconditions separately from property violations', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'alwaysTrue', {
      precondition: {
        module: 'properties.ts',
        exportName: 'neverAccepts',
        executionKind: 'sync',
      },
    });
    request.numRuns = 1;

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'failure');
    assert.equal(response.result.counterexample, null);
    assert.equal(response.result.failure?.kind, 'precondition-exhausted');
    assert.equal(response.result.failure?.errorName, 'PreconditionExhausted');
    assert.equal(
      response.result.failure?.message,
      'Property could not satisfy its precondition within the skip limit',
    );
  });
});

test('reports a throwing precondition as an execution error instead of a counterexample', async () => {
  const request = contractExecutionRequest('alwaysTrue', {
    preconditionExport: 'throwingPrecondition',
  });

  await assert.rejects(
    executeProperty(request),
    (error: unknown) => error instanceof ProtocolError
      && error.code === 'entrypoint.precondition.threw'
      && error.path === 'manifest.precondition'
      && error.diagnosticMessage === 'Property precondition threw a non-Error value: precondition exploded',
  );
});

test('reports a non-boolean precondition as an entry-point contract error', async () => {
  const request = contractExecutionRequest('alwaysTrue', {
    preconditionExport: 'nonBooleanPrecondition',
  });

  await assert.rejects(
    executeProperty(request),
    (error: unknown) => error instanceof ProtocolError
      && error.code === 'entrypoint.result.invalid'
      && error.path === 'manifest.precondition.result',
  );
});

test('keeps unprintable precondition exceptions classified as execution errors', async () => {
  const cases = [
    { exportName: 'throwingOpaquePrecondition', executionKind: 'sync' as const },
    { exportName: 'asyncThrowingOpaquePrecondition', executionKind: 'async' as const },
    { exportName: 'throwingUnprintableErrorPrecondition', executionKind: 'sync' as const },
    { exportName: 'asyncThrowingUnprintableErrorPrecondition', executionKind: 'async' as const },
    { exportName: 'throwingUnprintableNamePrecondition', executionKind: 'sync' as const },
  ];

  for (const { exportName, executionKind } of cases) {
    const request = contractExecutionRequest('alwaysTrue', {
      preconditionExport: exportName,
      preconditionExecutionKind: executionKind,
    });

    await assert.rejects(
      executeProperty(request),
      (error: unknown) => error instanceof ProtocolError
        && error.code === 'entrypoint.precondition.threw'
        && error.path === 'manifest.precondition',
    );
  }
});

test('keeps timeout-shaped predicate exceptions classified as property violations', async () => {
  const cases = [
    { exportName: 'throwingTimeoutMessagePredicate', executionKind: 'sync' as const },
    { exportName: 'asyncThrowingTimeoutMessagePredicate', executionKind: 'async' as const },
  ];

  for (const { exportName, executionKind } of cases) {
    const request = contractExecutionRequest(exportName, { predicateExecutionKind: executionKind });

    const response = await executeProperty(request);

    assert.equal(response.result.failure?.kind, 'property');
    assert.ok(response.result.counterexample);
    assert.equal(response.result.failure?.message, 'Property timeout: exceeded limit of 20 milliseconds');
  }
});

test('keeps hostile predicate exceptions classified as property violations', async () => {
  const cases = [
    { exportName: 'throwingOpaquePrecondition', executionKind: 'sync' as const },
    { exportName: 'asyncThrowingOpaquePrecondition', executionKind: 'async' as const },
    { exportName: 'throwingUnprintableErrorPrecondition', executionKind: 'sync' as const },
    { exportName: 'asyncThrowingUnprintableErrorPrecondition', executionKind: 'async' as const },
    { exportName: 'throwingUnprintableNamePrecondition', executionKind: 'sync' as const },
    { exportName: 'throwingFastCheckBrandedPredicate', executionKind: 'sync' as const },
    { exportName: 'asyncThrowingFastCheckBrandedPredicate', executionKind: 'async' as const },
    { exportName: 'throwingHostileProxyPredicate', executionKind: 'sync' as const },
  ];

  for (const { exportName, executionKind } of cases) {
    const request = contractExecutionRequest(exportName, { predicateExecutionKind: executionKind });

    const response = await executeProperty(request);

    assert.equal(response.result.failure?.kind, 'property');
    assert.ok(response.result.counterexample);
    assert.equal(typeof response.result.failure?.errorName, 'string');
    assert.equal(typeof response.result.failure?.message, 'string');
  }
});

test('keeps false, throwing, and assertion predicates classified as property violations', async () => {
  for (const predicateExport of ['falsePredicate', 'throwingPredicate', 'assertionPredicate']) {
    const response = await executeProperty(contractExecutionRequest(predicateExport));

    assert.equal(response.result.status, 'failure');
    assert.equal(response.result.failure?.kind, 'property');
    assert.ok(response.result.counterexample);
  }
});

test('reports a non-boolean predicate as an entry-point contract error', async () => {
  await assert.rejects(
    executeProperty(contractExecutionRequest('nonBooleanPredicate')),
    (error: unknown) => error instanceof ProtocolError
      && error.code === 'entrypoint.result.invalid'
      && error.path === 'manifest.predicate.result',
  );
});

test('preserves synchronous contract errors when shrinking reaches a regular violation', async () => {
  const cases = [
    {
      predicateExport: 'falsePredicate',
      preconditionExport: 'throwingWhenPositivePrecondition',
      expectedCode: 'entrypoint.precondition.threw',
      expectedPath: 'manifest.precondition',
    },
    {
      predicateExport: 'nonBooleanWhenPositivePredicate',
      expectedCode: 'entrypoint.result.invalid',
      expectedPath: 'manifest.predicate.result',
    },
  ];

  for (const contractCase of cases) {
    const request = contractExecutionRequest(contractCase.predicateExport, {
      inputDomains: [{ kind: 'integer', min: 0, max: 100 }],
      ...(contractCase.preconditionExport === undefined
        ? {}
        : { preconditionExport: contractCase.preconditionExport }),
    });
    request.examples = [[encodeJsValue(100)]];
    request.numRuns = 1;

    await assert.rejects(
      executeProperty(request),
      (error: unknown) => error instanceof ProtocolError
        && error.code === contractCase.expectedCode
        && error.path === contractCase.expectedPath,
    );
  }
});

test('preserves asynchronous contract errors when shrinking reaches a regular violation', async () => {
  const cases = [
    {
      predicateExport: 'falsePredicate',
      preconditionExport: 'asyncThrowingWhenPositivePrecondition',
      preconditionExecutionKind: 'async' as const,
      expectedCode: 'entrypoint.precondition.threw',
      expectedPath: 'manifest.precondition',
    },
    {
      predicateExport: 'asyncNonBooleanWhenPositivePredicate',
      predicateExecutionKind: 'async' as const,
      expectedCode: 'entrypoint.result.invalid',
      expectedPath: 'manifest.predicate.result',
    },
  ];

  for (const contractCase of cases) {
    const request = contractExecutionRequest(contractCase.predicateExport, {
      inputDomains: [{ kind: 'integer', min: 0, max: 100 }],
      ...(contractCase.predicateExecutionKind === undefined
        ? {}
        : { predicateExecutionKind: contractCase.predicateExecutionKind }),
      ...(contractCase.preconditionExport === undefined
        ? {}
        : { preconditionExport: contractCase.preconditionExport }),
      ...(contractCase.preconditionExecutionKind === undefined
        ? {}
        : { preconditionExecutionKind: contractCase.preconditionExecutionKind }),
    });
    request.examples = [[encodeJsValue(100)]];
    request.numRuns = 1;

    await assert.rejects(
      executeProperty(request),
      (error: unknown) => error instanceof ProtocolError
        && error.code === contractCase.expectedCode
        && error.path === contractCase.expectedPath,
    );
  }
});

test('preserves positional special values through one invocation', async () => {
  const request = contractExecutionRequest('recognizesSpecialValues', {
    inputDomains: [
      constantDomain(undefined),
      constantDomain(null),
      constantDomain(-0),
      constantDomain(Number.NaN),
      constantDomain(Number.POSITIVE_INFINITY),
      constantDomain(Number.NEGATIVE_INFINITY),
    ],
  });

  const response = await executeProperty(request);

  assert.equal(response.result.status, 'success');
});

test('isolates predicate mutation between explicit examples and generated samples', async () => {
  const request = contractExecutionRequest('isolatesPredicateMutation', {
    inputDomains: [{
      kind: 'array',
      element: { kind: 'integer', min: 1, max: 1 },
      minLength: 1,
      maxLength: 1,
    }],
  });
  request.examples = [[encodeJsValue([1])]];
  request.numRuns = 2;

  const response = await executeProperty(request);

  assert.equal(response.result.status, 'success');
  assert.equal(response.result.numRuns, 2);
});

test('shrinks and replays the unmodified sample after predicate-local mutation', async () => {
  const arrayDomain = {
    kind: 'array',
    element: { kind: 'integer', min: -10, max: 10 },
    minLength: 1,
    maxLength: 3,
  };
  const request = contractExecutionRequest('mutatesAndFails', {
    inputDomains: [arrayDomain],
  });

  const first = await executeProperty(request);
  const replay = await executeProperty({
    ...request,
    replayPath: first.result.replayPath ?? undefined,
    seed: first.result.seed,
  });

  assert.equal(first.result.status, 'failure');
  assert.ok(first.result.numShrinks > 0);
  assert.notDeepEqual(first.result.counterexample, [encodeJsValue([999])]);
  assert.deepEqual(replay.result.counterexample, first.result.counterexample);
});

test('executes explicit examples through the same predicate', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'isNotSeven');
    request.examples = [[encodeJsValue(7)]];

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'failure');
    assert.deepEqual(response.result.counterexample, [encodeJsValue(7)]);
  });
});

test('reports asynchronous predicate timeout as a structured timeout failure', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'neverCompletes', {
      predicateExecutionKind: 'async',
    });
    request.timeoutMillis = 20;
    request.numRuns = 1;

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'failure');
    assert.equal(response.result.failure?.kind, 'timeout');
    assert.equal(response.result.counterexample, null);
  });
});

test('an interrupted asynchronous observation keeps an explicit pending state', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'neverCompletes', { predicateExecutionKind: 'async' });
    const point = { module: 'properties.ts', line: 1, column: 1 };
    request.manifest.assertions = [{ id: 'timeout', source: point, testedCall: point,
      operands: [{ id: 'unreached', source: point }] }];
    request.observationRequest = {
      points: [{ id: 'unreached', assertionId: 'timeout', operandId: 'unreached',
        source: point, callSite: point, kind: 'intermediate' }],
      sources: [{ module: 'properties.ts', sha256: createHash('sha256').update(PROPERTY_MODULE_SOURCE).digest('hex') }],
      maxInvocations: 1, maxPointsPerInvocation: 1, maxArrayElements: 4, maxBytes: 4096,
    };
    request.timeoutMillis = 20;
    request.numRuns = 1;

    const result = await executeProperty(request);

    assert.equal(result.result.failure?.kind, 'timeout');
    assert.equal(result.result.observations?.invocations[0]?.admission, 'pending');
    assert.equal(result.result.observations?.invocations[0]?.outcome, 'pending');
  });
});

test('keeps a counterexample classified as a property failure when shrinking is interrupted', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'slowFailure', {
      inputDomain: {
        kind: 'array',
        element: { kind: 'integer', min: -100, max: 100 },
        minLength: 50,
        maxLength: 100,
      },
    });
    request.timeoutMillis = 40;
    request.numRuns = 100;

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'failure');
    assert.ok(response.result.counterexample);
    assert.equal(response.result.failure?.kind, 'property');
  });
});

test('reports the original nested array when the predicate mutates its invocation to an object', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const originalValue = [[1]];
    const request = executionRequest(sourceRoot, 'mutatesNestedArrayToObject', {
      inputDomain: {
        kind: 'array',
        element: {
          kind: 'array',
          element: { kind: 'integer', min: 1, max: 1 },
          minLength: 1,
          maxLength: 1,
        },
        minLength: 1,
        maxLength: 1,
      },
    });

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'failure');
    assert.deepEqual(response.result.counterexample, [encodeJsValue(originalValue)]);
  });
});

test('reports and replays the original array when the predicate creates a cycle', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const originalValue = [1];
    const request = executionRequest(sourceRoot, 'mutatesArrayToCycle', {
      inputDomain: {
        kind: 'array',
        element: { kind: 'integer', min: 1, max: 1 },
        minLength: 1,
        maxLength: 1,
      },
    });

    const first = await executeProperty(request);
    assert.ok(first.result.replayPath);

    const replay = await executeProperty({
      ...request,
      replayPath: first.result.replayPath,
      seed: first.result.seed,
    });

    assert.equal(first.result.status, 'failure');
    assert.deepEqual(first.result.counterexample, [encodeJsValue(originalValue)]);
    assert.deepEqual(replay.result.counterexample, first.result.counterexample);
  });
});

test('preserves non-Error thrown values including falsy primitives', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const cases = ['boom', '', 0, false, null, undefined] as const;

    for (const thrownValue of cases) {
      const request = executionRequest(sourceRoot, 'throwsInput', {
        inputDomain: { kind: 'constant', value: encodeJsValue(thrownValue) },
      });

      const response = await executeProperty(request);

      assert.equal(response.result.status, 'failure');
      assert.equal(response.result.failure?.errorName, 'ThrownValue');
      assert.equal(response.result.failure?.message, String(thrownValue));
    }
  });
});

interface RequestOverrides {
  predicateExecutionKind?: 'sync' | 'async';
  precondition?: FastCheckExecutionRequest['manifest']['precondition'];
  inputDomain?: unknown;
  inputDomains?: unknown[];
}

function executionRequest(
  sourceRoot: string,
  predicateExport: string,
  overrides: RequestOverrides = {},
): FastCheckExecutionRequest {
  const inputDomains = overrides.inputDomains ?? [
    overrides.inputDomain ?? { kind: 'integer', min: -10, max: 10 },
  ];
  const manifest: FastCheckExecutionRequest['manifest'] = {
    propertyId: `example.${predicateExport}`,
    inputs: inputDomains.map((domain, index) => ({ name: `argument${index}`, domain })),
    predicate: {
      module: 'properties.ts',
      exportName: predicateExport,
      executionKind: overrides.predicateExecutionKind ?? 'sync',
    },
  };

  if (overrides.precondition !== undefined) manifest.precondition = overrides.precondition;

  return {
    manifest,
    sourceRoots: [sourceRoot],
    seed: 42,
    numRuns: 20,
    timeoutMillis: 1_000,
    examples: [],
  };
}

interface ContractRequestOverrides {
  preconditionExport?: string;
  preconditionExecutionKind?: 'sync' | 'async';
  predicateExecutionKind?: 'sync' | 'async';
  inputDomains?: unknown[];
}

function contractExecutionRequest(
  predicateExport: string,
  overrides: ContractRequestOverrides = {},
): FastCheckExecutionRequest {
  const requestOverrides: RequestOverrides = {};
  if (overrides.inputDomains !== undefined) requestOverrides.inputDomains = overrides.inputDomains;
  if (overrides.predicateExecutionKind !== undefined) {
    requestOverrides.predicateExecutionKind = overrides.predicateExecutionKind;
  }

  const request = executionRequest(CONTRACT_SOURCE_ROOT, predicateExport, requestOverrides);
  request.manifest.predicate.module = CONTRACT_MODULE;

  if (overrides.preconditionExport !== undefined) {
    request.manifest.precondition = {
      module: CONTRACT_MODULE,
      exportName: overrides.preconditionExport,
      executionKind: overrides.preconditionExecutionKind ?? 'sync',
    };
  }

  return request;
}

function constantDomain(value: unknown): unknown {
  return { kind: 'constant', value: encodeJsValue(value) };
}

async function withPropertyModule(block: (sourceRoot: string) => Promise<void>): Promise<void> {
  const workspace = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-execute-property-')));
  const sourceRoot = path.join(workspace, 'src');

  await mkdir(sourceRoot);
  await writeFile(path.join(sourceRoot, 'properties.ts'), PROPERTY_MODULE_SOURCE);

  try {
    await block(sourceRoot);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
}

function semanticResult(result: FastCheckRunResult): Omit<FastCheckRunResult, 'executionTimeMillis'> {
  const { executionTimeMillis: _executionTimeMillis, ...semantic } = result;

  return semantic;
}

const PROPERTY_MODULE_SOURCE = `
export function alwaysTrue(_value: number): boolean { return true; }
export function isNegative(value: number): boolean { return value < 0; }
export async function asyncAlwaysTrue(_value: number): Promise<boolean> { return true; }
export async function asyncIsOne(value: number): Promise<boolean> { return value === 1; }
export function neverAccepts(_value: number): boolean { return false; }
export function isNotSeven(value: number): boolean { return value !== 7; }

export function slowFailure(_value: number[]): boolean {
  const deadline = Date.now() + 10;
  while (Date.now() < deadline) {}

  return false;
}

export async function neverCompletes(_value: number): Promise<boolean> {
  await new Promise<never>(() => undefined);

  return true;
}

export function mutatesNestedArrayToObject(value: unknown[][]): boolean {
  value[0]![0] = {};

  return false;
}

export function mutatesArrayToCycle(value: unknown[]): boolean {
  value[0] = value;

  return false;
}

export function throwsInput(value: unknown): never {
  throw value;
}

export function recognizesNegativeZero(value: number): boolean {
  return Object.is(value, -0);
}

export function shortCircuitsObservation(_value: number): boolean {
  const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.observe')];
  const skipped = false && typeof hook === 'function' && Boolean((hook as (id: string, value: number) => number)('side-effect', 1));

  return skipped === false;
}

export function repeatedPoint(value: number): boolean {
  const hook = (globalThis as Record<symbol, unknown>)[Symbol.for('org.usvm.ts.pbt.observe')];
  if (typeof hook === 'function') (hook as (id: string, result: number) => void)('input', value);
  let total = 0;
  for (let index = 0; index < 3; index += 1) {
    total += index;
    if (typeof hook === 'function') (hook as (id: string, result: number) => void)('step', total);
  }
  const testedReturn = (() => 42)();
  if (typeof hook === 'function') (hook as (id: string, result: number) => void)('tested-return', testedReturn);

  return value !== 2;
}
`.trimStart();

const CONTRACT_SOURCE_ROOT = fileURLToPath(new URL('../../../src/test/resources/', import.meta.url));
const CONTRACT_MODULE = 'properties/contract/PropertyExecutionContract.ts';
