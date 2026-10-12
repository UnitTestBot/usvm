import { AsyncLocalStorage } from 'node:async_hooks';
import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { types } from 'node:util';
import { adapterDiagnostic } from './diagnostics.js';
import { encodeJsValue, protocolError, type TaggedJsValue } from './js-value.js';
import { stringifyProtocolJson } from './protocol-json.js';

export interface ObservationPointRequest {
  id: string;
  assertionId: string;
  operandId: string;
  source: SourcePoint;
  callSite: SourcePoint;
  kind: 'argument' | 'return' | 'intermediate' | 'pre' | 'post';
  inputIndex?: number;
}

export interface SourcePoint {
  module: string;
  line: number;
  column: number;
}

export interface ObservationSource {
  module: string;
  sha256: string;
}

export interface ObservationRequest {
  points: ObservationPointRequest[];
  sources: ObservationSource[];
  maxInvocations: number;
  maxPointsPerInvocation: number;
  maxArrayElements: number;
  maxBytes: number;
}

export interface ObservationValue {
  status: 'captured' | 'unsupported' | 'truncated';
  value?: TaggedJsValue;
  reason?: string;
}

interface InvocationRecord {
  invocationId: number;
  parentInvocationId: null;
  callSite: SourcePoint | null;
  origin: 'fast-check';
  phase: 'generation' | 'explicit' | 'shrink' | 'replay' | 'unknown';
  input: ObservationValue;
  admission: 'pending' | 'admitted' | 'rejected' | 'threw';
  outcome: 'pending' | 'holds' | 'false' | 'threw' | 'skipped';
  points: Array<Omit<ObservationPointRequest, 'id'> & {
    pointId: string;
    occurrence: number;
    eventOrdinal: number;
    value: ObservationValue;
  }>;
}

export interface ObservationArtifact {
  propertyId: string;
  runId: string;
  verifiedSources: ObservationSource[];
  buildStatus: 'unverified';
  invocations: InvocationRecord[];
  droppedInvocations: number;
  droppedPoints: number;
  captureTimeMillis: number;
}

interface ObservationContext {
  recorder: ObservationRecorder;
  invocation: InvocationRecord;
}

const contexts = new AsyncLocalStorage<ObservationContext>();
const hookSymbol = Symbol.for('org.usvm.ts.pbt.observe');

/** Explicit hook observes a computed value and returns the identical value. */
export function installObservationHook(): void {
  Object.defineProperty(globalThis, hookSymbol, {
    configurable: true,
    value: (pointId: string, value: unknown): unknown => {
      const context = contexts.getStore();
      context?.recorder.point(context.invocation, pointId, value);

      return value;
    },
  });
}

export class ObservationRecorder {
  private readonly artifact: ObservationArtifact;
  private readonly pointsById: Map<string, ObservationPointRequest>;

  constructor(propertyId: string, private readonly request: ObservationRequest) {
    this.pointsById = new Map(request.points.map((point) => [point.id, point]));
    this.artifact = {
      propertyId,
      runId: randomUUID(),
      verifiedSources: request.sources,
      buildStatus: 'unverified',
      invocations: [],
      droppedInvocations: 0,
      droppedPoints: 0,
      captureTimeMillis: 0,
    };

    if (this.reservedArtifactBytes() > request.maxBytes) {
      throw protocolError(
        adapterDiagnostic.protocolRequestInvalid,
        'Observation identity and source metadata exceed the artifact byte budget',
        'observationRequest.maxBytes',
      );
    }
  }

  get result(): ObservationArtifact {
    this.artifact.captureTimeMillis = Math.round(this.artifact.captureTimeMillis);

    return this.artifact;
  }

  run<T>(
    values: unknown[],
    phase: InvocationRecord['phase'],
    invocation: () => T,
  ): T {
    if (this.artifact.invocations.length >= this.request.maxInvocations) {
      this.artifact.droppedInvocations = incrementCounter(this.artifact.droppedInvocations);

      return invocation();
    }

    const record: InvocationRecord = {
      invocationId: this.artifact.invocations.length,
      parentInvocationId: null,
      callSite: this.request.points[0]?.callSite ?? null,
      origin: 'fast-check',
      phase,
      input: this.snapshot(values),
      admission: 'pending',
      outcome: 'pending',
      points: [],
    };
    this.artifact.invocations.push(record);
    if (this.reservedArtifactBytes() > this.request.maxBytes) {
      this.artifact.invocations.pop();
      this.artifact.droppedInvocations = incrementCounter(this.artifact.droppedInvocations);

      return invocation();
    }

    return contexts.run({ recorder: this, invocation: record }, invocation);
  }

  finish(admission: InvocationRecord['admission'], outcome: InvocationRecord['outcome']): void {
    const record = contexts.getStore()?.invocation;
    if (record !== undefined) {
      record.admission = admission;
      record.outcome = outcome;
    }
  }

  point(record: InvocationRecord, pointId: string, value: unknown): void {
    const point = this.pointsById.get(pointId);
    if (point === undefined || record.points.length >= this.request.maxPointsPerInvocation) {
      this.artifact.droppedPoints = incrementCounter(this.artifact.droppedPoints);

      return;
    }

    record.points.push({
      pointId: point.id,
      assertionId: point.assertionId,
      operandId: point.operandId,
      source: point.source,
      callSite: point.callSite,
      kind: point.kind,
      ...(point.inputIndex === undefined ? {} : { inputIndex: point.inputIndex }),
      occurrence: record.points.filter((event) => event.pointId === point.id).length,
      eventOrdinal: record.points.length,
      value: this.snapshot(value),
    });
    if (this.reservedArtifactBytes() > this.request.maxBytes) {
      record.points.pop();
      this.artifact.droppedPoints = incrementCounter(this.artifact.droppedPoints);
    }
  }

  private snapshot(value: unknown): ObservationValue {
    const startedAt = performance.now();
    try {
      const budget = { remaining: this.request.maxBytes - this.reservedArtifactBytes() };

      return captureValue(value, this.request.maxArrayElements, new WeakSet(), 0, budget);
    } finally {
      this.artifact.captureTimeMillis = Math.min(
        Number.MAX_SAFE_INTEGER,
        this.artifact.captureTimeMillis + performance.now() - startedAt,
      );
    }
  }

  private reservedArtifactBytes(): number {
    // Reserve final counters/timing and the longest admission/outcome before accepting any record.
    // Completing an async invocation or incrementing dropped counters must never grow past maxBytes.
    const envelope = {
      ...this.artifact,
      invocations: this.artifact.invocations.map((record) => ({
        ...record,
        admission: 'rejected',
        outcome: 'pending',
      })),
      droppedInvocations: Number.MAX_SAFE_INTEGER,
      droppedPoints: Number.MAX_SAFE_INTEGER,
      captureTimeMillis: Number.MAX_SAFE_INTEGER,
    };

    return Buffer.byteLength(stringifyProtocolJson(envelope), 'utf8');
  }
}

function incrementCounter(value: number): number {
  return Math.min(Number.MAX_SAFE_INTEGER, value + 1);
}

interface CaptureBudget {
  remaining: number;
}

type CaptureResult =
  | { status: 'captured'; value: TaggedJsValue }
  | { status: 'unsupported' | 'truncated'; reason: string };

const stringCodeUnit = Function.prototype.call.bind(String.prototype.charCodeAt) as
  (value: string, index: number) => number;

function captureValue(
  value: unknown,
  maxArrayElements: number,
  seen: WeakSet<object>,
  depth: number,
  budget: CaptureBudget,
): CaptureResult {
  if (budget.remaining < 0) return { status: 'truncated', reason: 'byte-budget' };

  if (typeof value === 'string') {
    budget.remaining -= '{"kind":"string","value":}'.length;
    if (!reserveJsonString(value, budget)) return { status: 'truncated', reason: 'byte-budget' };

    return { status: 'captured', value: { kind: 'string', value } };
  }
  if (value === null || value === undefined || typeof value === 'boolean' || typeof value === 'number') {
    const tagged = encodeJsValue(value);
    budget.remaining -= stringifyProtocolJson(tagged).length;
    if (budget.remaining < 0) return { status: 'truncated', reason: 'byte-budget' };

    return { status: 'captured', value: tagged };
  }

  if (types.isProxy(value)) return { status: 'unsupported', reason: 'proxy' };
  if (!Array.isArray(value)) return { status: 'unsupported', reason: 'unsupported-type' };
  if (depth >= 4) return { status: 'unsupported', reason: 'depth-limit' };
  if (Object.getPrototypeOf(value) !== Array.prototype) return { status: 'unsupported', reason: 'array-prototype' };
  if (seen.has(value)) return { status: 'unsupported', reason: 'alias-or-cycle' };
  if (value.length > maxArrayElements) return { status: 'unsupported', reason: 'array-element-limit' };

  budget.remaining -= '{"kind":"array","elements":[]}'.length;
  if (budget.remaining < 0) return { status: 'truncated', reason: 'byte-budget' };

  seen.add(value);
  const elements: TaggedJsValue[] = [];
  for (let index = 0; index < value.length; index += 1) {
    if (index > 0) budget.remaining -= 1;
    if (budget.remaining < 0) return { status: 'truncated', reason: 'byte-budget' };

    const descriptor = Object.getOwnPropertyDescriptor(value, String(index));
    if (descriptor === undefined) return { status: 'unsupported', reason: 'sparse-array' };
    if (!('value' in descriptor)) return { status: 'unsupported', reason: 'accessor' };

    const captured = captureValue(descriptor.value, maxArrayElements, seen, depth + 1, budget);
    if (captured.status !== 'captured') return captured;

    elements.push(captured.value);
  }

  // Inspect names only after the bounded value walk, rather than allocating every descriptor up front.
  const hasExtraProperty = Object.getOwnPropertyNames(value)
    .some((key) => key !== 'length' && !/^(0|[1-9]\d*)$/.test(key));
  if (hasExtraProperty) return { status: 'unsupported', reason: 'array-extra-property' };

  return { status: 'captured', value: { kind: 'array', elements } };
}

function reserveJsonString(value: string, budget: CaptureBudget): boolean {
  // Count the actual JSON UTF-8 cost without allocating its escaped representation.
  // Stop at the first excess: a large string or nested array never requires a full serialization.
  budget.remaining -= 2;
  for (let index = 0; index < value.length && budget.remaining >= 0; index += 1) {
    const unit = stringCodeUnit(value, index);
    if (unit === 0x22 || unit === 0x5c || unit === 0x08 || unit === 0x09
      || unit === 0x0a || unit === 0x0c || unit === 0x0d) {
      budget.remaining -= 2;
    } else if (unit < 0x20) {
      budget.remaining -= 6;
    } else if (unit < 0x80) {
      budget.remaining -= 1;
    } else if (unit < 0x800) {
      budget.remaining -= 2;
    } else if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = stringCodeUnit(value, index + 1);
      const isSurrogatePair = next >= 0xdc00 && next <= 0xdfff;
      budget.remaining -= isSurrogatePair ? 4 : 6;
      if (isSurrogatePair) index += 1;
    } else {
      budget.remaining -= unit >= 0xdc00 && unit <= 0xdfff ? 6 : 3;
    }
  }

  return budget.remaining >= 0;
}
