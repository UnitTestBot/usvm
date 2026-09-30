import { AsyncLocalStorage } from 'node:async_hooks';
import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { types } from 'node:util';
import { encodeJsValue, type TaggedJsValue } from './js-value.js';

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
      this.artifact.droppedInvocations += 1;

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
    if (this.artifactBytes() > this.request.maxBytes) {
      this.artifact.invocations.pop();
      this.artifact.droppedInvocations += 1;

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
      this.artifact.droppedPoints += 1;

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
    if (this.artifactBytes() > this.request.maxBytes) {
      record.points.pop();
      this.artifact.droppedPoints += 1;
    }
  }

  private snapshot(value: unknown): ObservationValue {
    const startedAt = performance.now();
    try {
      const reason = unsafeReason(value, this.request.maxArrayElements, new WeakSet(), 0);
      if (reason !== undefined) return { status: 'unsupported', reason };

      const tagged = encodeJsValue(value);
      const bytes = Buffer.byteLength(JSON.stringify(tagged), 'utf8');
      if (this.artifactBytes() + bytes > this.request.maxBytes) {
        return { status: 'truncated', reason: 'byte-budget' };
      }

      return { status: 'captured', value: tagged };
    } finally {
      this.artifact.captureTimeMillis += performance.now() - startedAt;
    }
  }

  private artifactBytes(): number {
    return Buffer.byteLength(JSON.stringify(this.artifact), 'utf8');
  }
}

function unsafeReason(value: unknown, maxArrayElements: number, seen: WeakSet<object>, depth: number): string | undefined {
  if (value === null || value === undefined || ['boolean', 'string', 'number'].includes(typeof value)) return undefined;
  if (types.isProxy(value)) return 'proxy';
  if (!Array.isArray(value)) return 'unsupported-type';
  if (depth >= 4) return 'depth-limit';
  if (Object.getPrototypeOf(value) !== Array.prototype) return 'array-prototype';
  if (seen.has(value)) return 'alias-or-cycle';
  if (value.length > maxArrayElements) return 'array-element-limit';

  seen.add(value);
  const descriptors = Object.getOwnPropertyDescriptors(value);
  if (Object.keys(descriptors).some((key) => key !== 'length' && !/^(0|[1-9]\d*)$/.test(key))) {
    return 'array-extra-property';
  }
  for (let index = 0; index < value.length; index += 1) {
    const descriptor = descriptors[String(index)];
    if (descriptor === undefined) return 'sparse-array';
    if (!('value' in descriptor)) return 'accessor';

    const reason = unsafeReason(descriptor.value, maxArrayElements, seen, depth + 1);
    if (reason !== undefined) return reason;
  }

  return undefined;
}
