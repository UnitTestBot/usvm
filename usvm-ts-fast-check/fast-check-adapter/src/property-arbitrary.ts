import fc from 'fast-check';
import { adapterDiagnostic } from './diagnostics.js';
import type { JsConcreteValue } from './js-value.js';
import { protocolError } from './js-value.js';
import type { PropertyManifestWire } from './execute-property.js';
import { projectDomain } from './project-domain.js';

/** Constructs declared joint support; it does not interpret predicate or precondition behavior. */
export function buildPropertyArbitrary(manifest: PropertyManifestWire): fc.Arbitrary<JsConcreteValue[]> {
  const generator = manifest.generator;
  if (generator === undefined) {
    return fc.tuple(...manifest.inputs.map((input, index) =>
      projectDomain(input.domain, `manifest.inputs[${index}].domain`)));
  }

  validateJointGenerator(manifest);

  const array = projectDomain(manifest.inputs[0]?.domain, 'manifest.inputs[0].domain');

  return array.chain((value) => {
    if (!Array.isArray(value) || value.length === 0) {
      throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Array-index generator requires a nonempty array', 'manifest.generator');
    }

    return fc.integer({ min: 0, max: value.length - 1 }).map((index): JsConcreteValue[] => [value, index]);
  });
}

/** Checks the declared domains before a dependent arbitrary can sample outside either one. */
export function validateJointGenerator(manifest: PropertyManifestWire): void {
  const generator = manifest.generator;
  if (generator === undefined) return;

  const arrayDomain = manifest.inputs[0]?.domain;
  const indexDomain = manifest.inputs[1]?.domain;
  const array = asRecord(arrayDomain);
  const index = asRecord(indexDomain);
  const valid = generator.kind === 'array-index'
    && generator.arrayInputIndex === 0 && generator.indexInputIndex === 1
    && manifest.inputs.length === 2
    && array?.kind === 'array' && Number.isInteger(array.minLength) && Number.isInteger(array.maxLength)
    && (array.minLength as number) >= 1 && (array.maxLength as number) <= 32
    && (array.minLength as number) <= (array.maxLength as number)
    && index?.kind === 'integer' && index.min === 0
    && index.max === (array.maxLength as number) - 1;
  if (!valid) {
    throw protocolError(
      adapterDiagnostic.protocolManifestInvalid,
      'Array-index generator requires a nonempty bounded array and index domain 0..maxLength-1',
      'manifest.generator',
    );
  }
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}
