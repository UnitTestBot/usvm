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

  if (generator.kind !== 'array-index' || generator.arrayInputIndex !== 0 || generator.indexInputIndex !== 1) {
    throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Unsupported joint generator', 'manifest.generator');
  }

  const array = projectDomain(manifest.inputs[0]?.domain, 'manifest.inputs[0].domain');

  return array.chain((value) => {
    if (!Array.isArray(value) || value.length === 0) {
      throw protocolError(adapterDiagnostic.protocolManifestInvalid, 'Array-index generator requires a nonempty array', 'manifest.generator');
    }

    return fc.integer({ min: 0, max: value.length - 1 }).map((index): JsConcreteValue[] => [value, index]);
  });
}
