import assert from 'node:assert/strict';
import { mkdtemp, mkdir, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { encodeJsValue, ProtocolError } from '../src/js-value.js';
import {
  executeProperty,
  type FastCheckExecutionRequest,
  type FastCheckRunResult,
} from '../src/execute-property.js';

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
  inputDomains?: unknown[];
}

function contractExecutionRequest(
  predicateExport: string,
  overrides: ContractRequestOverrides = {},
): FastCheckExecutionRequest {
  const requestOverrides: RequestOverrides = {};
  if (overrides.inputDomains !== undefined) requestOverrides.inputDomains = overrides.inputDomains;

  const request = executionRequest(CONTRACT_SOURCE_ROOT, predicateExport, requestOverrides);
  request.manifest.predicate.module = CONTRACT_MODULE;

  if (overrides.preconditionExport !== undefined) {
    request.manifest.precondition = {
      module: CONTRACT_MODULE,
      exportName: overrides.preconditionExport,
      executionKind: 'sync',
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
`.trimStart();

const CONTRACT_SOURCE_ROOT = fileURLToPath(new URL('../../../src/test/resources/', import.meta.url));
const CONTRACT_MODULE = 'properties/contract/PropertyExecutionContract.ts';
