import fc from 'fast-check';
import { adapterDiagnostic } from './diagnostics.js';
import type { PropertyManifestWire } from './execute-property.js';
import { decodeJsValue, type JsConcreteValue, type TaggedJsValue, protocolError } from './js-value.js';
import type { ReturnedInputMetadata } from './observe-property.js';
import { buildPropertyArbitrary } from './property-arbitrary.js';

export interface ReturnedInputSeed {
  id: string;
  inputs: TaggedJsValue[];
  sourceRunId: string;
  sourceInvocationId: number;
}

export interface ReturnedInputGeneration {
  seeds: ReturnedInputSeed[];
  radius: number;
}

/** Reject unsupported support instead of replacing a joint or non-integer generator. */
export function validateReturnedInputGeneration(value: unknown, manifest: PropertyManifestWire): ReturnedInputGeneration {
  const request = asRecord(value);
  const validRequest = request !== undefined && Array.isArray(request.seeds)
    && request.seeds.length >= 1 && request.seeds.length <= MAX_SEEDS
    && Number.isInteger(request.radius) && (request.radius as number) >= 1 && (request.radius as number) <= MAX_RADIUS;
  if (!validRequest) throw invalid('Expected 1..8 returned seeds and integer radius 1..1024');

  const integerSupport = manifest.generator === undefined
    && manifest.inputs.length >= 1 && manifest.inputs.length <= MAX_INPUTS
    && manifest.inputs.every((input) => asRecord(input.domain)?.kind === 'integer');
  if (!integerSupport) {
    throw protocolError(adapterDiagnostic.returnedGenerationUnsupported,
      'Returned generation supports only independent integer inputs', 'returnedInputGeneration');
  }

  // Reuse ordinary projection validation before reading the supported integer bounds.
  buildPropertyArbitrary(manifest);
  const ids = new Set<string>();
  const seeds = (request.seeds as unknown[]).map((value): ReturnedInputSeed => {
    const seed = asRecord(value);
    const validSeed = seed !== undefined && identifier(seed.id) && identifier(seed.sourceRunId)
      && Number.isSafeInteger(seed.sourceInvocationId) && (seed.sourceInvocationId as number) >= 0
      && Array.isArray(seed.inputs) && seed.inputs.length === manifest.inputs.length;
    if (!validSeed) throw invalid('A returned seed requires identity, source invocation and the original input arity');
    if (ids.has(seed.id as string)) throw invalid('Returned seed IDs must be unique');
    ids.add(seed.id as string);

    const inputs = seed.inputs as TaggedJsValue[];
    inputs.forEach((tagged, index) => {
      const number = decodeJsValue(tagged, `returnedInputGeneration.seeds.inputs[${index}]`);
      const domain = asRecord(manifest.inputs[index]?.domain)!;
      const inSupport = typeof number === 'number' && Number.isInteger(number) && !Object.is(number, -0)
        && number >= (domain.min as number) && number <= (domain.max as number);
      if (!inSupport) throw invalid('Returned seed is outside the original integer support');
    });

    return { id: seed.id as string, inputs, sourceRunId: seed.sourceRunId as string,
      sourceInvocationId: seed.sourceInvocationId as number };
  });

  return { seeds, radius: request.radius as number };
}

/** Native oneof/tuple/integer supply generation and shrinking; the original branch retains half the probability. */
export function buildReturnedInputGeneration(manifest: PropertyManifestWire, request?: ReturnedInputGeneration): {
  arbitrary: fc.Arbitrary<JsConcreteValue[]>;
  metadataOf(values: JsConcreteValue[]): ReturnedInputMetadata | undefined;
} {
  const original = buildPropertyArbitrary(manifest);
  const metadata = new WeakMap<JsConcreteValue[], ReturnedInputMetadata>();
  if (request === undefined) return { arbitrary: original, metadataOf: () => undefined };

  const returned = request.seeds.map((seed) => {
    const numbers = seed.inputs.map((value) => decodeJsValue(value) as number);
    const components = numbers.map((number, index) => {
      const domain = asRecord(manifest.inputs[index]?.domain)!;
      const min = Math.max(domain.min as number, number - request.radius);
      const max = Math.min(domain.max as number, number + request.radius);

      return fc.integer({ min, max });
    });

    return fc.tuple(...components).map((values): JsConcreteValue[] => {
      const exactSeed = values.every((number, index) => Object.is(number, numbers[index]));
      metadata.set(values, {
        origin: exactSeed ? 'returned-seed' : 'returned-neighborhood',
        returnedInput: { seedId: seed.id, sourceRunId: seed.sourceRunId, sourceInvocationId: seed.sourceInvocationId },
      });

      return values;
    });
  });

  return { arbitrary: fc.oneof(original, fc.oneof(...returned)), metadataOf: (values) => metadata.get(values) };
}

function identifier(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= MAX_IDENTIFIER_LENGTH;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : undefined;
}

function invalid(message: string): Error {
  return protocolError(adapterDiagnostic.returnedGenerationInvalid, message, 'returnedInputGeneration');
}

const MAX_SEEDS = 8;
const MAX_INPUTS = 8;
const MAX_RADIUS = 1024;
const MAX_IDENTIFIER_LENGTH = 128;
