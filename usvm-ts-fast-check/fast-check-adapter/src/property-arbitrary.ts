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

/** Tracks fast-check's own generate/shrink calls by value identity, including cloned values. */
export class PhaseTrackingArbitrary extends fc.Arbitrary<JsConcreteValue[]> {
  private readonly phases = new WeakMap<JsConcreteValue[], 'generation' | 'shrink'>();
  private readonly explicit = new WeakSet<JsConcreteValue[]>();

  constructor(private readonly delegate: fc.Arbitrary<JsConcreteValue[]>) {
    super();
  }

  markExplicit(values: JsConcreteValue[]): void {
    this.explicit.add(values);
  }

  phaseOf(values: JsConcreteValue[]): 'generation' | 'shrink' | 'explicit' | 'unknown' {
    return this.phases.get(values) ?? (this.explicit.has(values) ? 'explicit' : 'unknown');
  }

  generate(mrng: fc.Random, biasFactor: number | undefined): fc.Value<JsConcreteValue[]> {
    return this.tag(this.delegate.generate(mrng, biasFactor), 'generation');
  }

  canShrinkWithoutContext(value: unknown): value is JsConcreteValue[] {
    return this.delegate.canShrinkWithoutContext(value);
  }

  shrink(value: JsConcreteValue[], context: unknown): fc.Stream<fc.Value<JsConcreteValue[]>> {
    return this.delegate.shrink(value, context).map((entry) => this.tag(entry, 'shrink'));
  }

  private tag(entry: fc.Value<JsConcreteValue[]>, phase: 'generation' | 'shrink'): fc.Value<JsConcreteValue[]> {
    return new fc.Value(entry.value_, entry.context, () => {
      const values = entry.value;
      this.phases.set(values, phase);

      return values;
    });
  }
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
