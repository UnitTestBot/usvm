import assert from 'node:assert/strict';
import { mkdtemp, mkdir, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { encodeJsValue } from '../src/js-value.js';
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

test('reports exhausted preconditions as a property failure without a counterexample', async () => {
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
    assert.equal(response.result.failure?.kind, 'property');
    assert.equal(response.result.failure?.errorName, 'PropertyFailure');
    assert.equal(
      response.result.failure?.message,
      'Property could not satisfy its precondition within the skip limit',
    );
  });
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
      inputDomain: { kind: 'constant', value: encodeJsValue(originalValue) },
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
      inputDomain: { kind: 'constant', value: encodeJsValue(originalValue) },
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

test('isolates predicate input from recursive array mutation in the precondition', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'receivesOriginalNestedArray', {
      precondition: {
        module: 'properties.ts',
        exportName: 'mutatesNestedArrayAndAccepts',
        executionKind: 'sync',
      },
      inputDomain: { kind: 'constant', value: encodeJsValue([[1]]) },
    });

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'success');
  });
});

test('isolates asynchronous predicate input from recursive array mutation in the precondition', async () => {
  await withPropertyModule(async (sourceRoot) => {
    const request = executionRequest(sourceRoot, 'asyncReceivesOriginalNestedArray', {
      predicateExecutionKind: 'async',
      precondition: {
        module: 'properties.ts',
        exportName: 'asyncMutatesNestedArrayAndAccepts',
        executionKind: 'async',
      },
      inputDomain: { kind: 'constant', value: encodeJsValue([[1]]) },
    });

    const response = await executeProperty(request);

    assert.equal(response.result.status, 'success');
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
}

function executionRequest(
  sourceRoot: string,
  predicateExport: string,
  overrides: RequestOverrides = {},
): FastCheckExecutionRequest {
  const manifest: FastCheckExecutionRequest['manifest'] = {
    propertyId: `example.${predicateExport}`,
    inputs: [{
      name: 'value',
      domain: overrides.inputDomain ?? { kind: 'integer', min: -10, max: 10 },
    }],
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

export function mutatesNestedArrayAndAccepts(value: unknown[][]): boolean {
  value[0]![0] = {};

  return true;
}

export function receivesOriginalNestedArray(value: unknown[][]): boolean {
  return value[0]?.[0] === 1;
}

export async function asyncMutatesNestedArrayAndAccepts(value: unknown[][]): Promise<boolean> {
  value[0]![0] = {};

  return true;
}

export async function asyncReceivesOriginalNestedArray(value: unknown[][]): Promise<boolean> {
  return value[0]?.[0] === 1;
}

export function throwsInput(value: unknown): never {
  throw value;
}
`.trimStart();
