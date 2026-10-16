import assert from 'node:assert/strict';
import test from 'node:test';
import fc from 'fast-check';
import { encodeJsValue, ProtocolError, type JsConcreteValue } from '../src/js-value.js';
import type { PropertyManifestWire } from '../src/execute-property.js';
import { PhaseTrackingArbitrary } from '../src/property-arbitrary.js';
import { buildReturnedInputGeneration, validateReturnedInputGeneration } from '../src/returned-input-generation.js';

const manifest = (min = 0, max = 100): PropertyManifestWire => ({
  propertyId: 'test.returned',
  inputs: [{ name: 'value', domain: { kind: 'integer', min, max } }],
  predicate: { module: 'property.ts', exportName: 'holds', executionKind: 'sync' },
});
const request = (seed = 8) => ({
  radius: 2,
  seeds: [{ id: 'returned-1', inputs: [encodeJsValue(seed)], sourceRunId: 'source-run', sourceInvocationId: 3 }],
});

test('ordinary generation is never relabelled by equality with a returned seed', () => {
  const property = manifest(0, 0);
  const generation = buildReturnedInputGeneration(property, validateReturnedInputGeneration(request(0), property));
  const tracker = new PhaseTrackingArbitrary(generation.arbitrary);

  const values: JsConcreteValue[][] = [];

  fc.assert(fc.property(tracker, (value) => {
    assert.equal(tracker.phaseOf(value), 'generation');
    values.push(value);

    return true;
  }), { seed: 42, numRuns: 40 });

  assert.ok(values.every((value) => value[0] === 0));
  assert.ok(values.some((value) => generation.metadataOf(value) === undefined));
  assert.ok(values.some((value) => generation.metadataOf(value)?.origin === 'returned-seed'));
});

test('fast-check Value clone reads preserve the selected generation lineage and phase', () => {
  const property = manifest();
  const generation = buildReturnedInputGeneration(property, validateReturnedInputGeneration(request(), property));
  const tracker = new PhaseTrackingArbitrary(generation.arbitrary);
  const entries: Array<fc.Value<JsConcreteValue[]>> = [];
  const generate = tracker.generate.bind(tracker);
  tracker.generate = (random, bias) => {
    const entry = generate(random, bias);
    entries.push(entry);

    return entry;
  };

  fc.sample(tracker, { seed: 42, numRuns: 40 });

  for (const entry of entries) {
    assert.equal(entry.hasToBeCloned, true);
    const first = entry.value;
    const second = entry.value;
    assert.deepEqual(first, second);
    assert.deepEqual(generation.metadataOf(first), generation.metadataOf(second));
    assert.equal(tracker.phaseOf(first), 'generation');
    assert.equal(tracker.phaseOf(second), 'generation');
  }
  assert.ok(entries.some((entry) => generation.metadataOf(entry.value)?.returnedInput.seedId === 'returned-1'));
  assert.ok(entries.some((entry) => generation.metadataOf(entry.value) === undefined));
});

test('native generation and shrinking retain branch lineage and bounded support', () => {
  const property = manifest();
  const generation = buildReturnedInputGeneration(property, validateReturnedInputGeneration(request(), property));
  const feedbackOnly = generation.arbitrary.filter((values) => generation.metadataOf(values) !== undefined);
  const tracker = new PhaseTrackingArbitrary(feedbackOnly);
  const evidence: Array<{ phase: string; origin: string | undefined; input: number }> = [];

  const result = fc.check(fc.property(tracker, (values) => {
    const metadata = generation.metadataOf(values);
    const input = values[0] as number;
    assert.equal(metadata?.returnedInput.seedId, 'returned-1');
    assert.equal(metadata?.returnedInput.sourceRunId, 'source-run');
    assert.equal(metadata?.returnedInput.sourceInvocationId, 3);
    assert.ok(input >= 6 && input <= 10);
    evidence.push({ phase: tracker.phaseOf(values), origin: metadata?.origin, input });

    return input < 9;
  }), { seed: 42, numRuns: 32 });

  assert.equal(result.failed, true);
  assert.ok(evidence.some((entry) => entry.phase === 'generation' && entry.origin === 'returned-neighborhood'));
  assert.ok(evidence.some((entry) => entry.phase === 'shrink' && entry.origin === 'returned-neighborhood'));
  assert.ok(evidence.every((entry) => entry.origin !== undefined));
});

test('integer neighborhoods clip at both signed 32-bit support boundaries without overflow', () => {
  for (const seed of [-2147483648, 2147483647]) {
    const property = manifest(-2147483648, 2147483647);
    const generation = buildReturnedInputGeneration(property,
      validateReturnedInputGeneration({ ...request(seed), radius: 1024 }, property));
    const feedbackOnly = generation.arbitrary.filter((values) => generation.metadataOf(values) !== undefined);

    const values = fc.sample(feedbackOnly, { seed: 42, numRuns: 32 });

    assert.ok(values.every(([value]) => typeof value === 'number'
      && Number.isInteger(value) && Math.abs(value - seed) <= 1024
      && value >= -2147483648 && value <= 2147483647));
    assert.ok(values.some(([value]) => value !== seed));
  }
});

test('unsupported joint and binary64 domains cannot silently replace their original generator', () => {
  const number = manifest();
  number.inputs[0]!.domain = { kind: 'number', min: encodeJsValue(0), max: encodeJsValue(100), allowNaN: false };
  const joint = manifest();
  joint.generator = { kind: 'array-index', id: 'joint', arrayInputIndex: 0, indexInputIndex: 1 };

  const empty = manifest();
  empty.inputs = [];
  const oversized = manifest();
  oversized.inputs = Array.from({ length: 9 }, (_, index) => ({
    name: `input${index}`, domain: { kind: 'integer', min: 0, max: 100 },
  }));

  for (const property of [number, joint, empty, oversized]) {
    assert.throws(() => validateReturnedInputGeneration(request(), property),
      (error: unknown) => error instanceof ProtocolError && error.code === 'generation.returned.unsupported');
  }
});

test('seed IDs bounds support arity and radius are validated at the native boundary', () => {
  const property = manifest();
  const seed = request().seeds[0]!;
  const invalid = [
    { ...request(), radius: 0 },
    { ...request(), radius: 1025 },
    { ...request(), radius: 1.5 },
    { ...request(), seeds: [] },
    { ...request(), seeds: [seed, seed] },
    { ...request(), seeds: [{ ...seed, sourceInvocationId: -1 }] },
    { ...request(), seeds: [{ ...seed, inputs: [] }] },
    request(-1),
    request(101),
    request(-0),
    request(Number.NaN),
    request(Number.POSITIVE_INFINITY),
  ];

  for (const value of invalid) {
    assert.throws(() => validateReturnedInputGeneration(value, property),
      (error: unknown) => error instanceof ProtocolError && error.code === 'generation.returned.invalid');
  }
});
