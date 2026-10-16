import { performance } from 'node:perf_hooks';
import { adapterDiagnostic } from './diagnostics.js';
import { protocolError } from './js-value.js';
import { captureObservationValue, type ObservationArtifact, type ObservationSource } from './observe-property.js';
import { stringifyProtocolJson } from './protocol-json.js';
import type { PropertyManifestWire } from './execute-property.js';

type Invocation = ObservationArtifact['invocations'][number];
export type InputRecord = Pick<Invocation, 'phase' | 'input' | 'admission' | 'outcome'>;

export interface InputExportRequest {
  sources: ObservationSource[];
  maxInvocations: number;
  maxBytes: number;
  maxArrayElements: number;
}

export interface InputExportArtifact {
  propertyId: string;
  verifiedSources: ObservationSource[];
  records: InputRecord[];
  droppedInvocations: number;
  unsupportedInputs: number;
  pendingInvocations: number;
  captureTimeMillis: number;
}

/** Owns input snapshots and incremental byte accounting; history is serialized only when the response is published. */
export class InputExportRecorder {
  private readonly artifact: InputExportArtifact;
  private reservedBytes: number;

  constructor(propertyId: string, private readonly request: InputExportRequest) {
    this.artifact = { propertyId, verifiedSources: request.sources, records: [], droppedInvocations: 0,
      unsupportedInputs: 0, pendingInvocations: 0, captureTimeMillis: 0 };
    const envelope = { ...this.artifact, droppedInvocations: Number.MAX_SAFE_INTEGER,
      unsupportedInputs: Number.MAX_SAFE_INTEGER, pendingInvocations: Number.MAX_SAFE_INTEGER,
      captureTimeMillis: Number.MAX_SAFE_INTEGER };
    this.reservedBytes = Buffer.byteLength(stringifyProtocolJson(envelope), 'utf8');
    if (this.reservedBytes > request.maxBytes) {
      throw protocolError(adapterDiagnostic.protocolRequestInvalid,
        'Input export source metadata exceeds the byte budget', 'inputExportRequest.maxBytes');
    }
  }

  get result(): InputExportArtifact {
    // Async callbacks may finish after fast-check returns a timeout; this receipt preserves publication-time outcomes.
    const records = this.artifact.records.map(record => ({ ...record }));
    return { ...this.artifact, records, captureTimeMillis: Math.round(this.artifact.captureTimeMillis),
      pendingInvocations: records.filter(record => record.outcome === 'pending' || record.admission === 'pending').length };
  }

  begin(values: unknown[], phase: InputRecord['phase']): InputRecord | undefined {
    const startedAt = performance.now();
    try {
      if (this.artifact.records.length >= this.request.maxInvocations) {
        this.drop();
        return undefined;
      }
      const remaining = this.request.maxBytes - this.reservedBytes;
      const record: InputRecord = { phase,
        input: captureObservationValue(values, this.request.maxArrayElements, remaining),
        admission: 'pending', outcome: 'pending' };
      const reservedRecord = { ...record, admission: 'rejected', outcome: 'pending' };
      const bytes = Buffer.byteLength(stringifyProtocolJson(reservedRecord), 'utf8')
        + (this.artifact.records.length === 0 ? 0 : 1);
      if (bytes > remaining) {
        this.drop();
        return undefined;
      }
      this.reservedBytes += bytes;
      this.artifact.records.push(record);
      if (record.input.status !== 'captured') this.artifact.unsupportedInputs += 1;
      return record;
    } finally {
      this.artifact.captureTimeMillis += performance.now() - startedAt;
    }
  }

  private drop(): void {
    this.artifact.droppedInvocations = Math.min(Number.MAX_SAFE_INTEGER, this.artifact.droppedInvocations + 1);
  }
}

export function validateInputExportRequest(value: unknown, manifest: PropertyManifestWire): InputExportRequest {
  const request = value as Record<string, unknown> | null;
  const valid = request !== null && typeof request === 'object'
    && Number.isInteger(request.maxInvocations) && (request.maxInvocations as number) >= 1
    && (request.maxInvocations as number) <= 100_000
    && Number.isInteger(request.maxBytes) && (request.maxBytes as number) >= 1024
    && (request.maxBytes as number) <= 2 * 1024 * 1024
    && Number.isInteger(request.maxArrayElements) && (request.maxArrayElements as number) >= 1
    && (request.maxArrayElements as number) <= 64 && Array.isArray(request.sources);
  if (!valid) throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Invalid input export bounds', 'inputExportRequest');
  const sources = request.sources as ObservationSource[];
  const hashesValid = sources.every(source => source !== null && typeof source === 'object'
    && typeof source.module === 'string' && typeof source.sha256 === 'string' && /^[0-9a-f]{64}$/.test(source.sha256));
  if (!hashesValid) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid, 'Invalid source hash', 'inputExportRequest.sources');
  }
  const modules = sources.map(source => source.module);
  const unique = new Set(modules);
  const complete = unique.has(manifest.predicate.module)
    && (manifest.precondition === undefined || unique.has(manifest.precondition.module));
  if (unique.size !== sources.length || !complete) {
    throw protocolError(adapterDiagnostic.protocolRequestInvalid,
      'Input export requires unique predicate and precondition source hashes', 'inputExportRequest.sources');
  }
  const verifiedSources = sources.map(source => ({ module: source.module, sha256: source.sha256 }));
  return { sources: verifiedSources, maxInvocations: request.maxInvocations as number,
    maxBytes: request.maxBytes as number, maxArrayElements: request.maxArrayElements as number };
}
