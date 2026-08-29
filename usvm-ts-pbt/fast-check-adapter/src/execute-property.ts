import { performance } from 'node:perf_hooks';
import fc from 'fast-check';
import type { Parameters, RunDetails } from 'fast-check';
import {
  adapterDiagnostic,
  type AdapterDiagnosticDescriptor,
} from './diagnostics.js';
import {
  type ExecutionKind,
  loadEntryPoint,
  type LoadedEntryPoint,
  type TypeScriptEntryPointReference,
} from './entry-point.js';
import {
  decodeJsValue,
  encodeJsValue,
  type JsConcreteValue,
  ProtocolError,
  protocolError,
  type TaggedJsValue,
} from './js-value.js';
import { projectDomain } from './project-domain.js';

export interface PropertyManifestInput {
  name: string;
  domain: unknown;
}

export interface PropertyManifestWire {
  propertyId: string;
  inputs: PropertyManifestInput[];
  predicate: TypeScriptEntryPointReference;
  precondition?: TypeScriptEntryPointReference;
}

export interface FastCheckExecutionRequest {
  manifest: PropertyManifestWire;
  sourceRoots: string[];
  seed?: number;
  replayPath?: string;
  numRuns: number;
  timeoutMillis: number;
  examples: TaggedJsValue[][];
}

export interface FastCheckFailureDetails {
  kind: 'property' | 'timeout';
  errorName: string;
  message: string;
}

export interface FastCheckRunResult {
  propertyId: string;
  status: 'success' | 'failure';
  seed: number;
  replayPath: string | null;
  counterexample: TaggedJsValue[] | null;
  numRuns: number;
  numSkips: number;
  numShrinks: number;
  failure: FastCheckFailureDetails | null;
  executionTimeMillis: number;
}

export interface FastCheckExecutionSuccess {
  status: 'ok';
  result: FastCheckRunResult;
}

export async function executeProperty(requestValue: unknown): Promise<FastCheckExecutionSuccess> {
  const request = validateRequest(requestValue);
  const startedAt = performance.now();

  const predicate = await loadEntryPoint(request.manifest.predicate, request.sourceRoots, 'manifest.predicate');
  const precondition = request.manifest.precondition === undefined
    ? undefined
    : await loadEntryPoint(request.manifest.precondition, request.sourceRoots, 'manifest.precondition');

  const arbitrary = fc.tuple(
    ...request.manifest.inputs.map((input, index) =>
      projectDomain(input.domain, `manifest.inputs[${index}].domain`)),
  );
  const property = buildProperty(arbitrary, predicate, precondition);
  const parameters = buildParameters(request);

  const details = await checkProperty(property, parameters, request.replayPath);

  if (details.errorInstance instanceof ProtocolError) throw details.errorInstance;

  return {
    status: 'ok',
    result: toRunResult(
      request.manifest.propertyId,
      details,
      Math.max(0, Math.round(performance.now() - startedAt)),
    ),
  };
}

function buildProperty(
  arbitrary: fc.Arbitrary<unknown[]>,
  predicate: LoadedEntryPoint,
  precondition: LoadedEntryPoint | undefined,
): fc.IProperty<[unknown[]]> | fc.IAsyncProperty<[unknown[]]> {
  const asynchronous = predicate.executionKind === 'async' || precondition?.executionKind === 'async';

  if (asynchronous) {
    return fc.asyncProperty(arbitrary, async (values: unknown[]): Promise<boolean> => {
      if (precondition !== undefined && !(await precondition.invoke(cloneArguments(values)))) fc.pre(false);

      return await predicate.invoke(cloneArguments(values));
    });
  }

  return fc.property(arbitrary, (values: unknown[]): boolean => {
    if (precondition !== undefined && !precondition.invoke(cloneArguments(values))) fc.pre(false);

    return predicate.invoke(cloneArguments(values)) as boolean;
  });
}

async function checkProperty(
  property: fc.IProperty<[unknown[]]> | fc.IAsyncProperty<[unknown[]]>,
  parameters: Parameters<[unknown[]]>,
  replayPath: string | undefined,
): Promise<RunDetails<[unknown[]]>> {
  try {
    return await Promise.resolve(fc.check(property, parameters));
  } catch (error: unknown) {
    const replayFailed = replayPath !== undefined
      && error instanceof Error
      && error.message.startsWith('Unable to replay,');
    if (replayFailed) {
      throw protocolError(
        adapterDiagnostic.protocolReplayPathInvalid,
        'Replay path cannot be applied to this property run',
        'replayPath',
      );
    }

    throw error;
  }
}

function cloneArguments(values: unknown[]): JsConcreteValue[] {
  return cloneRecursiveArrays(values, new Map()) as JsConcreteValue[];
}

function cloneRecursiveArrays(value: unknown, clones: Map<unknown[], unknown[]>): unknown {
  if (!Array.isArray(value)) return value;

  const existing = clones.get(value);
  if (existing !== undefined) return existing;

  const clone: unknown[] = [];
  clones.set(value, clone);
  value.forEach((element) => clone.push(cloneRecursiveArrays(element, clones)));

  return clone;
}

function buildParameters(request: FastCheckExecutionRequest): Parameters<[unknown[]]> {
  const decodedExamples = request.examples.map((example, exampleIndex) => {
    if (example.length !== request.manifest.inputs.length) {
      throw protocolError(
        adapterDiagnostic.protocolExamplesArity,
        `Explicit example ${exampleIndex} has ${example.length} values, expected ${request.manifest.inputs.length}`,
        `examples[${exampleIndex}]`,
      );
    }

    const values = example.map((value, valueIndex) =>
      decodeJsValue(value, `examples[${exampleIndex}][${valueIndex}]`));

    return [values] as [unknown[]];
  });

  const parameters: Parameters<[unknown[]]> = {
    numRuns: request.numRuns,
    timeout: request.timeoutMillis,
    interruptAfterTimeLimit: request.timeoutMillis,
    markInterruptAsFailure: true,
    examples: decodedExamples,
  };

  if (request.seed !== undefined) parameters.seed = request.seed;
  if (request.replayPath !== undefined) parameters.path = request.replayPath;

  return parameters;
}

function toRunResult(
  propertyId: string,
  details: RunDetails<[unknown[]]>,
  executionTimeMillis: number,
): FastCheckRunResult {
  const counterexampleValues = details.counterexample?.[0];
  const counterexample = counterexampleValues === undefined
    ? null
    : counterexampleValues.map(encodeJsValue);
  const failure = details.failed ? failureDetails(details) : null;

  return {
    propertyId,
    status: details.failed ? 'failure' : 'success',
    seed: details.seed,
    replayPath: details.counterexamplePath,
    counterexample,
    numRuns: details.numRuns,
    numSkips: details.numSkips,
    numShrinks: details.numShrinks,
    failure,
    executionTimeMillis,
  };
}

function failureDetails(details: RunDetails<[unknown[]]>): FastCheckFailureDetails {
  const error = details.errorInstance;
  const timeout = (details.interrupted && details.counterexample === null) || isFastCheckTimeout(error);

  if (error instanceof Error) {
    return {
      kind: timeout ? 'timeout' : 'property',
      errorName: error.name || 'Error',
      message: error.message || 'Property execution failed',
    };
  }

  if (timeout) {
    return {
      kind: 'timeout',
      errorName: 'TimeoutError',
      message: 'Property execution exceeded the configured timeout',
    };
  }

  if (details.counterexample === null) {
    return {
      kind: 'property',
      errorName: 'PropertyFailure',
      message: 'Property could not satisfy its precondition within the skip limit',
    };
  }

  return {
    kind: 'property',
    errorName: 'ThrownValue',
    message: String(error),
  };
}

function isFastCheckTimeout(error: unknown): boolean {
  return error instanceof Error && error.message.startsWith('Property timeout:');
}

function validateRequest(value: unknown): FastCheckExecutionRequest {
  const request = requireRecord(
    value,
    adapterDiagnostic.protocolRequestInvalid,
    'Request must be a JSON object',
    'request',
  );

  const validSourceRoots = Array.isArray(request.sourceRoots)
    && request.sourceRoots.length > 0
    && request.sourceRoots.every((root) => typeof root === 'string');

  const validRunCount = Number.isInteger(request.numRuns)
    && (request.numRuns as number) >= 1;

  const validTimeout = Number.isInteger(request.timeoutMillis)
    && (request.timeoutMillis as number) >= 1
    && (request.timeoutMillis as number) <= MAX_TIMER_DELAY_MILLIS;

  const validExamples = Array.isArray(request.examples);

  if (!validSourceRoots || !validRunCount || !validTimeout || !validExamples) {
    throw protocolError(
      adapterDiagnostic.protocolRequestInvalid,
      'Source roots, run count, timeout, or examples are invalid',
      'request',
    );
  }

  if (request.seed !== undefined && !isSignedInt(request.seed)) {
    throw protocolError(
      adapterDiagnostic.protocolSeedInvalid,
      'Seed must be a signed 32-bit integer',
      'seed',
    );
  }

  const invalidReplayPath = request.replayPath !== undefined
    && (typeof request.replayPath !== 'string' || !REPLAY_PATH_PATTERN.test(request.replayPath));
  if (invalidReplayPath) {
    throw protocolError(
      adapterDiagnostic.protocolReplayPathInvalid,
      'Replay path is invalid',
      'replayPath',
    );
  }

  const manifest = validateManifest(request.manifest);
  const rawExamples = request.examples as unknown[];
  const examples = rawExamples.map((example: unknown, index: number) => {
    if (!Array.isArray(example)) {
      throw protocolError(
        adapterDiagnostic.protocolExamplesInvalid,
        'Each explicit example must be an array',
        `examples[${index}]`,
      );
    }

    return example as TaggedJsValue[];
  });

  const validated: FastCheckExecutionRequest = {
    manifest,
    sourceRoots: request.sourceRoots as string[],
    numRuns: request.numRuns as number,
    timeoutMillis: request.timeoutMillis as number,
    examples,
  };

  if (request.seed !== undefined) validated.seed = request.seed as number;
  if (request.replayPath !== undefined) validated.replayPath = request.replayPath as string;

  return validated;
}

function validateManifest(value: unknown): PropertyManifestWire {
  const manifest = requireRecord(
    value,
    adapterDiagnostic.protocolManifestInvalid,
    'Manifest must be an object',
    'manifest',
  );

  const valid = typeof manifest.propertyId === 'string'
    && manifest.propertyId.length > 0
    && Array.isArray(manifest.inputs)
    && manifest.inputs.length > 0;
  if (!valid) {
    throw protocolError(
      adapterDiagnostic.protocolManifestInvalid,
      'Manifest identity or inputs are invalid',
      'manifest',
    );
  }

  const rawInputs = manifest.inputs as unknown[];
  const inputs = rawInputs.map((value: unknown, index: number): PropertyManifestInput => {
    const input = requireRecord(
      value,
      adapterDiagnostic.protocolManifestInputInvalid,
      'Property input must be an object',
      `manifest.inputs[${index}]`,
    );

    if (typeof input.name !== 'string' || !('domain' in input)) {
      throw protocolError(
        adapterDiagnostic.protocolManifestInputInvalid,
        'Property input requires a name and domain',
        `manifest.inputs[${index}]`,
      );
    }

    return { name: input.name, domain: input.domain };
  });

  const validated: PropertyManifestWire = {
    propertyId: manifest.propertyId as string,
    inputs,
    predicate: validateEntryPoint(manifest.predicate, 'manifest.predicate'),
  };

  if (manifest.precondition !== undefined) {
    validated.precondition = validateEntryPoint(manifest.precondition, 'manifest.precondition');
  }

  return validated;
}

function validateEntryPoint(value: unknown, entryPath: string): TypeScriptEntryPointReference {
  const entryPoint = requireRecord(
    value,
    adapterDiagnostic.protocolEntryPointInvalid,
    'Entry point must be an object',
    entryPath,
  );

  const executionKind = entryPoint.executionKind;
  const valid = typeof entryPoint.module === 'string'
    && entryPoint.module.length > 0
    && typeof entryPoint.exportName === 'string'
    && entryPoint.exportName.length > 0
    && (executionKind === 'sync' || executionKind === 'async');
  if (!valid) {
    throw protocolError(
      adapterDiagnostic.protocolEntryPointInvalid,
      'Entry point reference is invalid',
      entryPath,
    );
  }

  return {
    module: entryPoint.module as string,
    exportName: entryPoint.exportName as string,
    executionKind: executionKind as ExecutionKind,
  };
}

function requireRecord(
  value: unknown,
  diagnostic: AdapterDiagnosticDescriptor,
  message: string,
  path: string,
): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw protocolError(diagnostic, message, path);
  }

  return value as Record<string, unknown>;
}

function isSignedInt(value: unknown): value is number {
  return typeof value === 'number'
    && Number.isInteger(value)
    && value >= -0x80000000
    && value <= 0x7fffffff;
}

// Node timers use signed 32-bit millisecond delays; larger values are clamped to one millisecond.
const MAX_TIMER_DELAY_MILLIS = 2 ** 31 - 1;
const REPLAY_PATH_PATTERN = /^\d+(?::\d+)*$/;
