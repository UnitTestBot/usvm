import { performance } from 'node:perf_hooks';
import fc from 'fast-check';
import type { Parameters, RunDetails } from 'fast-check';
import {
  adapterDiagnostic,
  type AdapterDiagnosticDescriptor,
} from './diagnostics.js';
import {
  EntryPointInvocationError,
  type ExecutionKind,
  loadEntryPoint,
  verifySourceHash,
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
import { buildPropertyArbitrary, PhaseTrackingArbitrary, validateJointGenerator } from './property-arbitrary.js';
import {
  installObservationHook,
  ObservationRecorder,
  type ObservationArtifact,
  type ObservationPointRequest,
  type ObservationRequest,
} from './observe-property.js';

export interface PropertyManifestInput {
  name: string;
  domain: unknown;
  generatorId?: string;
}

export interface PropertySourcePointWire {
  module: string;
  line: number;
  column: number;
}

export interface PropertyAssertionWire {
  id: string;
  source: PropertySourcePointWire;
  testedCall?: PropertySourcePointWire;
  operands: Array<{ id: string; source: PropertySourcePointWire }>;
}

export interface PropertyManifestWire {
  propertyId: string;
  inputs: PropertyManifestInput[];
  predicate: TypeScriptEntryPointReference;
  precondition?: TypeScriptEntryPointReference;
  assertions?: PropertyAssertionWire[];
  sourceIdentity?: { sourceSha256: string; buildSha256: string; buildScope: string };
  generator?: {
    id: string;
    kind: 'array-index';
    arrayInputIndex: number;
    indexInputIndex: number;
  };
}

export interface FastCheckExecutionRequest {
  manifest: PropertyManifestWire;
  sourceRoots: string[];
  seed?: number;
  /** Replay follows the same invocation contract as generation and shrinking. */
  replayPath?: string;
  numRuns: number;
  timeoutMillis: number;
  examples: TaggedJsValue[][];
  observationRequest?: ObservationRequest;
}

export interface FastCheckFailureDetails {
  kind: 'property' | 'precondition-exhausted' | 'timeout';
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
  observations?: ObservationArtifact;
}

export interface FastCheckExecutionSuccess {
  status: 'ok';
  result: FastCheckRunResult;
}

export async function executeProperty(requestValue: unknown): Promise<FastCheckExecutionSuccess> {
  const request = validateRequest(requestValue);
  const startedAt = performance.now();

  if (request.observationRequest !== undefined) {
    for (const source of request.observationRequest.sources) {
      await verifySourceHash(source.module, source.sha256, request.sourceRoots);
    }
    installObservationHook();
  }

  const predicate = await loadEntryPoint(request.manifest.predicate, request.sourceRoots, 'manifest.predicate');
  const precondition = request.manifest.precondition === undefined
    ? undefined
    : await loadEntryPoint(request.manifest.precondition, request.sourceRoots, 'manifest.precondition');

  const contractErrors: ContractErrorState = { first: undefined };
  const recorder = request.observationRequest === undefined
    ? undefined
    : new ObservationRecorder(request.manifest.propertyId, request.observationRequest);
  const baseArbitrary = buildPropertyArbitrary(request.manifest);
  const tracker = recorder === undefined ? undefined : new PhaseTrackingArbitrary(baseArbitrary);
  const arbitrary = tracker ?? baseArbitrary;
  const phaseOf = (values: JsConcreteValue[]) => request.replayPath === undefined
    ? tracker?.phaseOf(values) ?? 'unknown'
    : 'replay';
  const property = buildProperty(arbitrary, predicate, precondition, contractErrors, recorder, phaseOf);
  const parameters = buildParameters(request, tracker);

  const details = await checkProperty(property, parameters, request.replayPath);

  if (contractErrors.first !== undefined) throw contractErrors.first;
  if (details.errorInstance instanceof ProtocolError) throw details.errorInstance;

  return {
    status: 'ok',
    result: toRunResult(
      request.manifest.propertyId,
      details,
      Math.max(0, Math.round(performance.now() - startedAt)),
      recorder?.result,
    ),
  };
}

function buildProperty(
  arbitrary: fc.Arbitrary<JsConcreteValue[]>,
  predicate: LoadedEntryPoint,
  precondition: LoadedEntryPoint | undefined,
  contractErrors: ContractErrorState,
  recorder: ObservationRecorder | undefined,
  phaseOf: (values: JsConcreteValue[]) => 'generation' | 'explicit' | 'shrink' | 'replay' | 'unknown',
): fc.IProperty<[JsConcreteValue[]]> | fc.IAsyncProperty<[JsConcreteValue[]]> {
  const asynchronous = predicate.executionKind === 'async' || precondition?.executionKind === 'async';

  if (asynchronous) {
    return fc.asyncProperty(arbitrary, (values: JsConcreteValue[]): Promise<boolean> => preserveAsyncContractError(
      contractErrors,
      async () => {
        const result = await invokePropertyOnce(values, predicate, precondition, recorder, phaseOf(values));
        if (result === null) fc.pre(false);

        return result;
      },
    ));
  }

  return fc.property(arbitrary, (values: JsConcreteValue[]): boolean => preserveContractError(
    contractErrors,
    () => {
      const result = invokePropertyOnce(values, predicate, precondition, recorder, phaseOf(values)) as boolean | null;
      if (result === null) fc.pre(false);

      return result;
    },
  ));
}

/** One invocation boundary; #353 exact replay uses the same shape after integration. */
function invokePropertyOnce(
  values: JsConcreteValue[],
  predicate: LoadedEntryPoint,
  precondition: LoadedEntryPoint | undefined,
  recorder: ObservationRecorder | undefined,
  phase: 'generation' | 'explicit' | 'shrink' | 'replay' | 'unknown',
): boolean | null | Promise<boolean | null> {
  const invocationValues = cloneArguments(values);
  const invoke = (): boolean | null | Promise<boolean | null> => {
    if (predicate.executionKind === 'async' || precondition?.executionKind === 'async') {
      return (async () => {
        try {
          if (precondition !== undefined && !(await invokePrecondition(precondition, invocationValues))) {
            recorder?.finish('rejected', 'skipped');

            return null;
          }
        } catch (error: unknown) {
          recorder?.finish('threw', 'threw');
          throw error;
        }

        try {
          const result = await predicate.invoke(invocationValues);
          recorder?.finish('admitted', result ? 'holds' : 'false');

          return result;
        } catch (error: unknown) {
          recorder?.finish('admitted', 'threw');
          throw error;
        }
      })();
    }

    try {
      if (precondition !== undefined && !invokeSynchronousPrecondition(precondition, invocationValues)) {
        recorder?.finish('rejected', 'skipped');

        return null;
      }
    } catch (error: unknown) {
      recorder?.finish('threw', 'threw');
      throw error;
    }

    try {
      const result = predicate.invoke(invocationValues) as boolean;
      recorder?.finish('admitted', result ? 'holds' : 'false');

      return result;
    } catch (error: unknown) {
      recorder?.finish('admitted', 'threw');
      throw error;
    }
  };

  return recorder === undefined ? invoke() : recorder.run(values, phase, invoke);
}

interface ContractErrorState {
  first: ProtocolError | undefined;
}

function preserveContractError<T>(state: ContractErrorState, invocation: () => T): T {
  if (state.first !== undefined) throw state.first;

  try {
    return invocation();
  } catch (error: unknown) {
    if (error instanceof ProtocolError) state.first ??= error;

    throw error;
  }
}

async function preserveAsyncContractError<T>(
  state: ContractErrorState,
  invocation: () => Promise<T>,
): Promise<T> {
  if (state.first !== undefined) throw state.first;

  try {
    return await invocation();
  } catch (error: unknown) {
    if (error instanceof ProtocolError) state.first ??= error;

    throw error;
  }
}

async function invokePrecondition(
  precondition: LoadedEntryPoint,
  values: JsConcreteValue[],
): Promise<boolean> {
  try {
    return await precondition.invoke(values);
  } catch (error: unknown) {
    throw classifyPreconditionError(error);
  }
}

function invokeSynchronousPrecondition(
  precondition: LoadedEntryPoint,
  values: JsConcreteValue[],
): boolean {
  try {
    return precondition.invoke(values) as boolean;
  } catch (error: unknown) {
    throw classifyPreconditionError(error);
  }
}

function classifyPreconditionError(error: unknown): ProtocolError {
  if (error instanceof ProtocolError) return error;

  const thrownValue = error instanceof EntryPointInvocationError ? error.thrownValue : error;

  return protocolError(
    adapterDiagnostic.entryPointPreconditionThrew,
    `Property precondition threw ${describeThrownValue(thrownValue)}`,
    'manifest.precondition',
  );
}

function describeThrownValue(value: unknown): string {
  try {
    if (value instanceof Error) {
      const name = String(value.name || 'Error');

      return value.message.length === 0 ? name : `${name}: ${value.message}`;
    }

    return `a non-Error value: ${String(value)}`;
  } catch {
    return 'an unprintable value';
  }
}

async function checkProperty(
  property: fc.IProperty<[JsConcreteValue[]]> | fc.IAsyncProperty<[JsConcreteValue[]]>,
  parameters: Parameters<[JsConcreteValue[]]>,
  replayPath: string | undefined,
): Promise<RunDetails<[JsConcreteValue[]]>> {
  try {
    return await Promise.resolve(fc.check(property, parameters));
  } catch (error: unknown) {
    if (replayPath !== undefined && isFastCheckReplayFailure(error)) {
      throw protocolError(
        adapterDiagnostic.protocolReplayPathInvalid,
        'Replay path cannot be applied to this property run',
        'replayPath',
      );
    }

    throw error;
  }
}

/** See [the contract](../../../usvm-ts-pbt/PROPERTY_EXECUTION_CONTRACT.md) for the invocation and isolation rules. */
function cloneArguments(values: JsConcreteValue[]): JsConcreteValue[] {
  return structuredClone(values);
}

function buildParameters(
  request: FastCheckExecutionRequest,
  tracker: PhaseTrackingArbitrary | undefined,
): Parameters<[JsConcreteValue[]]> {
  const decodedExamples = request.examples.map((example, exampleIndex): [JsConcreteValue[]] => {
    if (example.length !== request.manifest.inputs.length) {
      throw protocolError(
        adapterDiagnostic.protocolExamplesArity,
        `Explicit example ${exampleIndex} has ${example.length} values, expected ${request.manifest.inputs.length}`,
        `examples[${exampleIndex}]`,
      );
    }

    const values = example.map((value, valueIndex) =>
      decodeJsValue(value, `examples[${exampleIndex}][${valueIndex}]`));
    tracker?.markExplicit(values);
    if (request.manifest.generator !== undefined) {
      const [array, index] = values;
      if (!Array.isArray(array) || typeof index !== 'number' || !Number.isInteger(index)
        || index < 0 || index >= array.length) {
        throw protocolError(
          adapterDiagnostic.protocolExamplesInvalid,
          'Explicit example violates declared joint generator support',
          `examples[${exampleIndex}]`,
        );
      }
    }

    return [values];
  });

  const parameters: Parameters<[JsConcreteValue[]]> = {
    numRuns: request.numRuns,
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
  details: RunDetails<[JsConcreteValue[]]>,
  executionTimeMillis: number,
  observations: ObservationArtifact | undefined,
): FastCheckRunResult {
  const counterexampleValues = details.counterexample?.[0];
  const counterexample = counterexampleValues === undefined
    ? null
    : counterexampleValues.map(encodeJsValue);
  const failure = details.failed ? failureDetails(details) : null;

  const result: FastCheckRunResult = {
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
  if (observations !== undefined) result.observations = observations;

  return result;
}

function failureDetails(details: RunDetails<[JsConcreteValue[]]>): FastCheckFailureDetails {
  if (details.interrupted && details.counterexample === null) {
    return {
      kind: 'timeout',
      errorName: 'TimeoutError',
      message: 'Property execution exceeded the configured timeout',
    };
  }

  if (details.counterexample === null) {
    return {
      kind: 'precondition-exhausted',
      errorName: 'PreconditionExhausted',
      message: 'Property could not satisfy its precondition within the skip limit',
    };
  }

  const error = details.errorInstance instanceof EntryPointInvocationError
    ? details.errorInstance.thrownValue
    : details.errorInstance;

  return {
    kind: 'property',
    ...describePropertyFailure(error),
  };
}

function describePropertyFailure(value: unknown): Pick<FastCheckFailureDetails, 'errorName' | 'message'> {
  try {
    if (value instanceof Error) {
      return {
        errorName: typeof value.name === 'string' && value.name.length > 0 ? value.name : 'Error',
        message: typeof value.message === 'string' && value.message.length > 0
          ? value.message
          : 'Property execution failed',
      };
    }

    return {
      errorName: 'ThrownValue',
      message: String(value),
    };
  } catch {
    return {
      errorName: 'ThrownValue',
      message: 'An unprintable value',
    };
  }
}

/** The pinned fast-check version exposes invalid replay paths only through a stable message prefix. */
function isFastCheckReplayFailure(error: unknown): boolean {
  return error instanceof Error && error.message.startsWith(FAST_CHECK_REPLAY_FAILURE_PREFIX);
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
  if (request.observationRequest !== undefined) {
    validated.observationRequest = validateObservationRequest(request.observationRequest, manifest);
  }

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

    const validatedInput: PropertyManifestInput = { name: input.name, domain: input.domain };
    if (input.generatorId !== undefined) {
      if (typeof input.generatorId !== 'string' || input.generatorId.length === 0) {
        throw protocolError(adapterDiagnostic.protocolManifestInputInvalid, 'Invalid generator ID', `manifest.inputs[${index}].generatorId`);
      }

      validatedInput.generatorId = input.generatorId;
    }

    return validatedInput;
  });

  const validated: PropertyManifestWire = {
    propertyId: manifest.propertyId as string,
    inputs,
    predicate: validateEntryPoint(manifest.predicate, 'manifest.predicate'),
  };

  if (manifest.precondition !== undefined) {
    validated.precondition = validateEntryPoint(manifest.precondition, 'manifest.precondition');
  }

  if (manifest.generator !== undefined) {
    const generator = requireRecord(
      manifest.generator,
      adapterDiagnostic.protocolManifestInvalid,
      'Generator must be an object',
      'manifest.generator',
    );
    const validGenerator = typeof generator.id === 'string' && generator.id.length > 0
      && generator.kind === 'array-index'
      && generator.arrayInputIndex === 0 && generator.indexInputIndex === 1
      && inputs.length === 2;
    if (!validGenerator) {
      throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Invalid joint generator', 'manifest.generator');
    }

    validated.generator = generator as unknown as NonNullable<PropertyManifestWire['generator']>;
    validateJointGenerator(validated);
  }

  if (manifest.assertions !== undefined) {
    if (!Array.isArray(manifest.assertions)) {
      throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Assertions must be an array', 'manifest.assertions');
    }

    validated.assertions = manifest.assertions.map((value: unknown, index: number) =>
      validateAssertion(value, `manifest.assertions[${index}]`));
  }

  if (manifest.sourceIdentity !== undefined) {
    const identity = requireRecord(manifest.sourceIdentity, adapterDiagnostic.protocolManifestInvalid,
      'Source identity must be an object', 'manifest.sourceIdentity');
    if (typeof identity.sourceSha256 !== 'string' || typeof identity.buildSha256 !== 'string'
      || typeof identity.buildScope !== 'string') {
      throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Invalid source identity', 'manifest.sourceIdentity');
    }

    validated.sourceIdentity = {
      sourceSha256: identity.sourceSha256,
      buildSha256: identity.buildSha256,
      buildScope: identity.buildScope,
    };
  }

  return validated;
}

function validateAssertion(value: unknown, path: string): PropertyAssertionWire {
  const assertion = requireRecord(value, adapterDiagnostic.protocolManifestInvalid, 'Assertion must be an object', path);
  if (typeof assertion.id !== 'string' || !Array.isArray(assertion.operands)) {
    throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Invalid assertion identity or operands', path);
  }

  const operands = assertion.operands.map((value: unknown, index: number) => {
    const operandPath = `${path}.operands[${index}]`;
    const operand = requireRecord(value, adapterDiagnostic.protocolManifestInvalid, 'Operand must be an object', operandPath);
    if (typeof operand.id !== 'string') {
      throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Invalid operand ID', operandPath);
    }

    return { id: operand.id, source: validateSourcePoint(operand.source, `${operandPath}.source`) };
  });
  const result: PropertyAssertionWire = {
    id: assertion.id,
    source: validateSourcePoint(assertion.source, `${path}.source`),
    operands,
  };
  if (assertion.testedCall !== undefined) {
    result.testedCall = validateSourcePoint(assertion.testedCall, `${path}.testedCall`);
  }

  return result;
}

function validateSourcePoint(value: unknown, path: string): PropertySourcePointWire {
  const point = requireRecord(value, adapterDiagnostic.protocolManifestInvalid, 'Source point must be an object', path);
  if (typeof point.module !== 'string' || !Number.isInteger(point.line) || !Number.isInteger(point.column)
    || (point.line as number) < 1 || (point.column as number) < 1) {
    throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Invalid source point', path);
  }

  return { module: point.module, line: point.line as number, column: point.column as number };
}

function validateObservationRequest(value: unknown, manifest: PropertyManifestWire): ObservationRequest {
  const request = requireRecord(value, adapterDiagnostic.protocolRequestInvalid,
    'Observation request must be an object', 'observationRequest');
  const validLimits = Number.isInteger(request.maxInvocations) && (request.maxInvocations as number) >= 1
    && (request.maxInvocations as number) <= 64
    && Number.isInteger(request.maxPointsPerInvocation) && (request.maxPointsPerInvocation as number) >= 1
    && (request.maxPointsPerInvocation as number) <= 8
    && Number.isInteger(request.maxArrayElements) && (request.maxArrayElements as number) >= 1
    && (request.maxArrayElements as number) <= 64
    && Number.isInteger(request.maxBytes) && (request.maxBytes as number) >= 1024
    && (request.maxBytes as number) <= 65_536;
  if (!validLimits || !Array.isArray(request.points) || !Array.isArray(request.sources)) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Invalid observation bounds', 'observationRequest');
  }

  const sources = request.sources.map((value: unknown, index: number) => {
    const source = requireRecord(value, adapterDiagnostic.protocolRequestInvalid,
      'Observation source must be an object', `observationRequest.sources[${index}]`);
    if (typeof source.module !== 'string' || typeof source.sha256 !== 'string'
      || !/^[0-9a-f]{64}$/.test(source.sha256)) {
      throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Invalid source hash', `observationRequest.sources[${index}]`);
    }

    return { module: source.module, sha256: source.sha256 };
  });
  const points = request.points.map((value: unknown, index: number) => {
    const path = `observationRequest.points[${index}]`;
    const point = requireRecord(value, adapterDiagnostic.protocolRequestInvalid, 'Point must be an object', path);
    if (typeof point.id !== 'string' || typeof point.assertionId !== 'string'
      || typeof point.operandId !== 'string'
      || !['argument', 'return', 'intermediate', 'pre', 'post'].includes(point.kind as string)) {
      throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Invalid point identity', path);
    }

    const validatedPoint: ObservationPointRequest = {
      id: point.id,
      assertionId: point.assertionId,
      operandId: point.operandId,
      source: validateSourcePoint(point.source, `${path}.source`),
      callSite: validateSourcePoint(point.callSite, `${path}.callSite`),
      kind: point.kind as ObservationPointRequest['kind'],
    };
    if (point.kind === 'argument') {
      if (!Number.isInteger(point.inputIndex) || (point.inputIndex as number) < 0
        || (point.inputIndex as number) >= manifest.inputs.length) {
        throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Invalid argument index', `${path}.inputIndex`);
      }
      validatedPoint.inputIndex = point.inputIndex as number;
    } else if (point.inputIndex !== undefined) {
      throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Only argument points may name an input index', path);
    }

    return validatedPoint;
  });
  const uniqueSources = new Set(sources.map((source) => source.module));
  const uniquePoints = new Set(points.map((point) => point.id));
  const allSourcesPresent = uniqueSources.has(manifest.predicate.module)
    && points.every((point) => uniqueSources.has(point.source.module));
  const allPointsBound = points.every((point) => manifest.assertions?.some((assertion) =>
    assertion.id === point.assertionId && samePoint(assertion.testedCall, point.callSite)
      && assertion.operands.some((operand) =>
        operand.id === point.operandId && samePoint(operand.source, point.source))) ?? false);
  if (points.length === 0 || uniquePoints.size !== points.length || uniqueSources.size !== sources.length
    || !allSourcesPresent || !allPointsBound) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid,
      'Observation points must bind to declared assertion operands and selected source hashes', 'observationRequest');
  }

  return {
    points,
    sources,
    maxInvocations: request.maxInvocations as number,
    maxPointsPerInvocation: request.maxPointsPerInvocation as number,
    maxArrayElements: request.maxArrayElements as number,
    maxBytes: request.maxBytes as number,
  };
}

function samePoint(left: PropertySourcePointWire | undefined, right: PropertySourcePointWire): boolean {
  return left !== undefined && left.module === right.module
    && left.line === right.line && left.column === right.column;
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
const FAST_CHECK_REPLAY_FAILURE_PREFIX = 'Unable to replay,';
