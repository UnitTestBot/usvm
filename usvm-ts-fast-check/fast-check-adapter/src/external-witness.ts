import { AsyncLocalStorage } from 'node:async_hooks';
import { performance } from 'node:perf_hooks';
import fc from 'fast-check';
import { adapterDiagnostic } from './diagnostics.js';
import { EntryPointInvocationError, isNamedCheckFailure, loadEntryPoint, sourceHashMatches, type LoadedEntryPoint } from './entry-point.js';
import { describeNamedCheckFailure, describePropertyFailure, invokePropertyOnce, validateCompilerLocalBinding, validateRequest, type FastCheckFailureDetails } from './execute-property.js';
import { decodeJsValue, encodeJsValue, ProtocolError, protocolError, type JsConcreteValue, type TaggedJsValue } from './js-value.js';
import { buildPropertyArbitrary } from './property-arbitrary.js';
import { stringifyProtocolJson } from './protocol-json.js';
import type { CompilerNumericLocalBinding } from './observe-property.js';
import { inWitnessSupport } from './witness-support.js';

export interface WitnessBinding {
  pointId: string;
  contextId: string;
  revision: string;
  occurrence: number;
}

interface DslNumericLocalRelation {
  compiler: CompilerNumericLocalBinding;
  inputIndex: number;
  resultIsLeft: boolean;
  template: 'STRICT_EQUAL' | 'LESS_OR_EQUAL';
}

export interface WitnessTarget {
  id: string;
  role: 'PROPERTY' | 'COVERAGE' | 'HYPOTHESIS';
  binding?: WitnessBinding;
  expectedOutcome?: 'FALSE' | 'THREW';
  dslNumericLocalRelation?: DslNumericLocalRelation;
  entryRelation?: {
    leftInputIndex: number;
    rightInputIndex: number;
    template: 'STRICT_EQUAL' | 'LESS_OR_EQUAL';
  };
}

export interface WitnessEvent {
  binding: WitnessBinding;
  guard: boolean;
  holds: boolean;
}

export interface WitnessNativeReplay {
  admission: 'ADMITTED' | 'DOMAIN_REJECTED' | 'PRECONDITION_REJECTED' | 'ERROR' | 'UNKNOWN';
  outcome: 'HOLDS' | 'FALSE' | 'THREW' | 'SKIPPED' | 'ERROR' | 'UNKNOWN';
  targetMatched: boolean;
  events: WitnessEvent[];
  failure?: FastCheckFailureDetails;
  diagnostic?: string;
  runtimeVersion?: string;
}

export interface WitnessReduction {
  status: 'UNSUPPORTED' | 'EXHAUSTED' | 'FAILED' | 'TIMEOUT';
  inputs?: TaggedJsValue[];
  attempts: number;
  acceptedShrinks: number;
  diagnostic?: string;
}

export interface WitnessOperation {
  mode: 'replay' | 'reduce';
  inputs: TaggedJsValue[];
  target: WitnessTarget;
  sources: Array<{ module: string; sha256: string }>;
  expected?: WitnessNativeReplay;
}

export interface WitnessExecutionSuccess {
  status: 'ok';
  witnessPropertyId: string;
  witnessTarget: WitnessTarget;
  witnessReplay?: WitnessNativeReplay;
  witnessReduction?: WitnessReduction;
}

interface WitnessContext {
  events: WitnessEvent[];
  active: boolean;
  contractError?: string;
  compilerTarget: WitnessTarget | undefined;
  compilerInput: number | undefined;
  compilerCallbacks: number;
  compilerDiagnostic?: string;
}
const contexts = new AsyncLocalStorage<WitnessContext>();

/** A passive hook: evaluate the original guard/relation first; return its boolean without substituting an oracle. */
export function installWitnessHook(): void {
  Object.defineProperty(globalThis, Symbol.for('org.usvm.ts.pbt.witness'), {
    configurable: true,
    value: (pointId: string, contextId: string, revision: string, guard: boolean, holds: boolean): boolean => {
      const context = contexts.getStore();
      if (context?.active) {
        const protectedBinding = context.compilerTarget?.binding;
        const compilerPoint = protectedBinding?.pointId === pointId && protectedBinding.contextId === contextId
          && protectedBinding.revision === revision;
        if (compilerPoint) return holds;

        const valid = [pointId, contextId, revision].every((value) => typeof value === 'string' && value.length > 0)
          && typeof guard === 'boolean' && typeof holds === 'boolean';
        if (!valid || context.events.length >= 256) {
          context.contractError = adapterDiagnostic.witnessEventsInvalid.code;

          return holds;
        }

        const occurrence = context.events.filter((event) => event.binding.pointId === pointId
          && event.binding.contextId === contextId && event.binding.revision === revision).length;
        context.events.push({ binding: { pointId, contextId, revision, occurrence }, guard, holds });
      }

      return holds;
    },
  });
}

/** Uses the same invocation boundary, original entrypoints, codecs, and fast-check shrink stream as ordinary PBT. */
export async function executeWitness(value: unknown): Promise<WitnessExecutionSuccess> {
  const request = validateRequest(value);
  const operation = validateOperation((value as Record<string, unknown>).witness, request.manifest.inputs.length);
  const identity = { witnessPropertyId: request.manifest.propertyId, witnessTarget: operation.target };
  const target = operation.target;
  const compilerRelation = target.dslNumericLocalRelation;
  if (compilerRelation !== undefined) {
    compilerRelation.compiler = validateCompilerLocalBinding(
      compilerRelation.compiler, request.manifest, operation.sources, 'witness.target.dslNumericLocalRelation',
    );
  }
  const declaredCheck = request.manifest.assertions?.some((check) => check.id === target.id) === true;
  const diagnosticCheck = request.manifest.predicate.checkResultExportName !== undefined && declaredCheck;
  const originalCheckObservation = operation.mode === 'replay' && target.binding === undefined
    && target.id === request.manifest.predicate.exportName
    && request.manifest.predicate.checkResultExportName !== undefined;
  const validOriginalCheck = target.role !== 'PROPERTY' || (target.binding === undefined
    ? originalCheckObservation || diagnosticCheck
      || (request.manifest.assertions?.length ?? 0) === 0 && target.id === request.manifest.predicate.exportName
    : target.binding.pointId === target.id && declaredCheck);
  if (!validOriginalCheck) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Original named check requires a companion or executable binding', 'witness.target');
  }
  for (const source of operation.sources) {
    const matches = await sourceHashMatches(source.module, source.sha256, request.sourceRoots);
    if (!matches) {
      const diagnostic = adapterDiagnostic.entryPointSourceHashMismatch.code;
      if (operation.mode === 'reduce') {
        return { status: 'ok', ...identity, witnessReduction: {
          status: 'FAILED', attempts: 0, acceptedShrinks: 0, diagnostic,
        } };
      }

      return { status: 'ok', ...identity, witnessReplay: {
        admission: 'UNKNOWN', outcome: 'UNKNOWN', targetMatched: false, events: [],
        diagnostic, runtimeVersion: process.version,
      } };
    }
  }
  const requiredModules = [request.manifest.predicate.module, request.manifest.precondition?.module]
    .filter((module): module is string => module !== undefined);
  if (!requiredModules.every((module) => operation.sources.some((source) => source.module === module))) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Witness sources must pin the original entrypoints', 'witness.sources');
  }

  installWitnessHook();
  const values = operation.inputs.map((input, index) => decodeJsValue(input, `witness.inputs[${index}]`));
  const arbitrary = buildPropertyArbitrary(request.manifest);
  if (!inWitnessSupport(request.manifest, values)) {
    if (operation.mode === 'reduce') {
      return { status: 'ok', ...identity, witnessReduction: {
        status: 'FAILED', attempts: 0, acceptedShrinks: 0, diagnostic: 'Input left declared support',
      } };
    }
    return { status: 'ok', ...identity, witnessReplay: {
      admission: 'DOMAIN_REJECTED', outcome: 'SKIPPED', targetMatched: false, events: [], runtimeVersion: process.version,
    } };
  }
  const predicate = await loadEntryPoint(
    request.manifest.predicate, request.sourceRoots, 'manifest.predicate', request.tsconfig,
    compilerRelation === undefined ? undefined : {
      binding: compilerRelation.compiler,
      observe: captureCompilerLocal,
    },
  );
  const precondition = request.manifest.precondition === undefined ? undefined
    : await loadEntryPoint(
      request.manifest.precondition, request.sourceRoots, 'manifest.precondition', request.tsconfig,
    );
  const replay = (input: JsConcreteValue[]) => replayOnce(input, operation.target, predicate, precondition, diagnosticCheck);

  if (operation.mode === 'replay') {
    const first = await replay(values);
    const second = await replay(values);
    if (signature(first) !== signature(second)) first.diagnostic = 'nondeterministic';

    return { status: 'ok', ...identity, witnessReplay: first };
  }

  if (!arbitrary.canShrinkWithoutContext(values)) {
    return { status: 'ok', ...identity, witnessReduction: {
      status: 'UNSUPPORTED', attempts: 0, acceptedShrinks: 0,
      diagnostic: 'The backend arbitrary cannot shrink this external value without native context',
    } };
  }

  const expected = operation.expected;
  if (expected === undefined || !expected.targetMatched) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Reduction requires a confirmed target', 'witness.expected');
  }
  const startedAt = performance.now();
  let attempts = 0;
  let reductionDiagnostic: string | undefined;
  const property = fc.asyncProperty(new ExternalValueArbitrary(arbitrary, values), async (input) => {
    attempts += 1;
    try {
      if (!inWitnessSupport(request.manifest, input)) return true;
      const first = await replay(input);
      const second = await replay(input);
      const nondeterministic = signature(first) !== signature(second);
      if (nondeterministic) {
        reductionDiagnostic ??= 'Nondeterministic shrinking invocation';

        return true;
      }
      if (first.outcome === 'ERROR') {
        reductionDiagnostic ??= first.diagnostic ?? 'Original oracle error during reduction';

        return true;
      }

      return !sameTarget(first, expected);
    } catch (error: unknown) {
      reductionDiagnostic ??= describePropertyFailure(error).message;

      return true;
    }
  });
  try {
    const details = await fc.check(property, {
      numRuns: 1,
      seed: request.seed ?? 0,
      interruptAfterTimeLimit: request.timeoutMillis,
      markInterruptAsFailure: true,
    });
    const timedOut = details.interrupted || performance.now() - startedAt >= request.timeoutMillis;
    if (timedOut || reductionDiagnostic !== undefined || details.counterexample === null) {
      return { status: 'ok', ...identity, witnessReduction: {
        status: timedOut ? 'TIMEOUT' : 'FAILED', attempts, acceptedShrinks: details.numShrinks,
        diagnostic: reductionDiagnostic ?? 'Reduction did not retain the target',
      } };
    }

    return { status: 'ok', ...identity, witnessReduction: {
      status: 'EXHAUSTED', inputs: details.counterexample[0].map(encodeJsValue),
      attempts, acceptedShrinks: details.numShrinks,
    } };
  } catch (error: unknown) {
    return { status: 'ok', ...identity, witnessReduction: {
      status: 'FAILED', attempts, acceptedShrinks: 0, diagnostic: describePropertyFailure(error).message,
    } };
  }
}

/** Only generation is replaced. All reduction comes from the backend's own arbitrary and retained shrink contexts. */
class ExternalValueArbitrary extends fc.Arbitrary<JsConcreteValue[]> {
  constructor(private readonly delegate: fc.Arbitrary<JsConcreteValue[]>, private readonly initial: JsConcreteValue[]) {
    super();
  }

  generate(): fc.Value<JsConcreteValue[]> { return new fc.Value(structuredClone(this.initial), undefined); }
  canShrinkWithoutContext(value: unknown): value is JsConcreteValue[] { return this.delegate.canShrinkWithoutContext(value); }
  shrink(value: JsConcreteValue[], context: unknown): fc.Stream<fc.Value<JsConcreteValue[]>> {
    return this.delegate.shrink(value, context);
  }
}

async function replayOnce(
  values: JsConcreteValue[], target: WitnessTarget, predicate: LoadedEntryPoint, precondition: LoadedEntryPoint | undefined,
  diagnosticCheck: boolean,
): Promise<WitnessNativeReplay> {
  const localRelation = target.dslNumericLocalRelation;
  const localInput = localRelation === undefined ? undefined : values[localRelation.inputIndex];
  const context: WitnessContext = {
    events: [], active: false, compilerCallbacks: 0,
    compilerTarget: localRelation === undefined ? undefined : target,
    compilerInput: typeof localInput === 'number' ? localInput : undefined,
  };
  const result: WitnessNativeReplay = {
    admission: 'ADMITTED', outcome: 'HOLDS', targetMatched: false, events: context.events, runtimeVersion: process.version,
  };
  if (localRelation !== undefined && typeof localInput !== 'number') {
    return { ...result, admission: 'UNKNOWN', outcome: 'UNKNOWN', diagnostic: 'compiler-local-numeric-required' };
  }
  const relation = target.entryRelation;
  const left = relation === undefined ? undefined : values[relation.leftInputIndex];
  const right = relation === undefined ? undefined : values[relation.rightInputIndex];
  if (relation !== undefined && (typeof left !== 'number' || typeof right !== 'number')) {
    return { ...result, admission: 'UNKNOWN', outcome: 'UNKNOWN', diagnostic: 'entry-relation-numeric-required' };
  }
  const entryEvent: WitnessEvent | undefined = relation === undefined ? undefined : {
    binding: target.binding!, guard: true,
    holds: relation.template === 'STRICT_EQUAL' ? left === right : (left as number) <= (right as number),
  };
  try {
    const holds = await contexts.run(context, () => invokePropertyOnce(
      values, predicate, precondition, undefined, 'replay', () => {
        context.active = true;
        if (entryEvent !== undefined) context.events.push(entryEvent);
      },
    ));
    if (holds === null) {
      result.admission = 'PRECONDITION_REJECTED';
      result.outcome = 'SKIPPED';
    } else {
      result.outcome = holds ? 'HOLDS' : 'FALSE';
    }
  } catch (error: unknown) {
    if (isNamedCheckFailure(error)) {
      result.outcome = error.exception === undefined ? 'FALSE' : 'THREW';
      result.failure = describeNamedCheckFailure(error);
    } else if (error instanceof EntryPointInvocationError) {
      result.outcome = 'THREW';
      result.failure = { kind: 'property', ...describePropertyFailure(error.thrownValue) };
    } else {
      result.admission = 'ERROR';
      result.outcome = 'ERROR';
      result.diagnostic = error instanceof ProtocolError ? error.code : describePropertyFailure(error).message;
    }
  }
  context.active = false;
  if (context.contractError !== undefined) {
    result.admission = 'ERROR';
    result.outcome = 'ERROR';
    result.diagnostic = context.contractError;
  }

  const requiresCompilerPoint = localRelation !== undefined && result.admission === 'ADMITTED';
  const validCompilerPoint = !requiresCompilerPoint
    || context.compilerCallbacks === 1 && context.compilerDiagnostic === undefined;
  if (!validCompilerPoint) {
    result.diagnostic = context.compilerDiagnostic ?? 'compiler-local-point-missing';
  }
  const event = target.binding === undefined ? undefined
    : context.events.find((entry) => sameBinding(entry.binding, target.binding as WitnessBinding));
  const propertyFailed = result.outcome === 'FALSE' || result.outcome === 'THREW';
  const guardedEvent = event !== undefined && event.guard;
  const checkMatched = diagnosticCheck
    ? result.failure?.checkId === target.id
    : result.failure?.checkId === undefined || result.failure.checkId === target.id;
  result.targetMatched = validCompilerPoint && result.admission === 'ADMITTED' && (target.role === 'PROPERTY'
    ? propertyFailed && checkMatched && (target.expectedOutcome === undefined || target.expectedOutcome === result.outcome)
      && (target.binding === undefined || (guardedEvent && !event.holds))
    : guardedEvent && (target.role === 'COVERAGE' || !event.holds));

  return result;
}

/** Only the callback passed to the selected companion can produce this protected event. */
function captureCompilerLocal(nodeId: number, localName: string, value: unknown): void {
  const context = contexts.getStore();
  const target = context?.compilerTarget;
  const relation = target?.dslNumericLocalRelation;
  if (context === undefined || !context.active || relation === undefined) return;

  context.compilerCallbacks += 1;
  const valid = context.compilerCallbacks === 1 && nodeId === relation.compiler.nodeId
    && localName === relation.compiler.localName && typeof value === 'number' && context.compilerInput !== undefined;
  if (!valid) {
    context.compilerDiagnostic = 'compiler-local-callback-mismatch';

    return;
  }
  const left = relation.resultIsLeft ? value : context.compilerInput;
  const right = relation.resultIsLeft ? context.compilerInput : value;
  context.events.push({
    binding: target!.binding!, guard: true,
    holds: relation.template === 'STRICT_EQUAL' ? left === right : (left as number) <= (right as number),
  });
}

export function sameTarget(actual: WitnessNativeReplay, expected: WitnessNativeReplay): boolean {
  return actual.admission === 'ADMITTED' && actual.targetMatched && actual.outcome === expected.outcome
    && actual.failure?.checkId === expected.failure?.checkId
    && actual.failure?.errorName === expected.failure?.errorName
    && actual.failure?.message === expected.failure?.message;
}

function sameBinding(left: WitnessBinding, right: WitnessBinding): boolean {
  return left.pointId === right.pointId && left.contextId === right.contextId
    && left.revision === right.revision && left.occurrence === right.occurrence;
}

function signature(result: WitnessNativeReplay): string { return stringifyProtocolJson(result); }

function validateOperation(value: unknown, arity: number): WitnessOperation {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Witness operation must be an object', 'witness');
  }
  const operation = value as WitnessOperation | undefined;
  const target = operation?.target;
  const binding = target?.binding;
  const validBinding = binding === undefined || (binding !== null && typeof binding.pointId === 'string' && binding.pointId.length > 0
    && typeof binding.contextId === 'string' && binding.contextId.length > 0
    && typeof binding.revision === 'string' && binding.revision.length > 0
    && Number.isInteger(binding.occurrence) && binding.occurrence >= 0);
  const relation = target?.entryRelation;
  const validEntryRelation = relation === undefined || (relation !== null && typeof relation === 'object'
    && !Array.isArray(relation) && target?.role === 'HYPOTHESIS'
    && binding?.contextId === 'original-entry' && binding.occurrence === 0
    && Number.isInteger(relation.leftInputIndex) && relation.leftInputIndex >= 0 && relation.leftInputIndex < arity
    && Number.isInteger(relation.rightInputIndex) && relation.rightInputIndex >= 0 && relation.rightInputIndex < arity
    && ['STRICT_EQUAL', 'LESS_OR_EQUAL'].includes(relation.template));
  const local = target?.dslNumericLocalRelation;
  const validLocal = local === undefined || (local !== null && typeof local === 'object' && !Array.isArray(local)
    && relation === undefined && target?.role === 'HYPOTHESIS' && binding?.contextId === 'original-compiler-local'
    && binding.occurrence === 0 && Number.isInteger(local.inputIndex) && local.inputIndex >= 0 && local.inputIndex < arity
    && typeof local.resultIsLeft === 'boolean' && ['STRICT_EQUAL', 'LESS_OR_EQUAL'].includes(local.template));
  const valid = operation !== undefined && ['replay', 'reduce'].includes(operation.mode)
    && Array.isArray(operation.inputs) && operation.inputs.length === arity
    && typeof target?.id === 'string' && target.id.length > 0
    && ['PROPERTY', 'COVERAGE', 'HYPOTHESIS'].includes(target.role)
    && (target.expectedOutcome === undefined || ['FALSE', 'THREW'].includes(target.expectedOutcome))
    && validBinding && validEntryRelation && validLocal && (target.role === 'PROPERTY' || binding !== undefined)
    && Array.isArray(operation.sources) && operation.sources.length > 0
    && operation.sources.every((source) => source !== null && typeof source === 'object' && typeof source.module === 'string'
      && typeof source.sha256 === 'string' && /^[0-9a-f]{64}$/.test(source.sha256));
  if (!valid) throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Invalid external witness operation', 'witness');

  return operation;
}
