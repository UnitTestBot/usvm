import assert from 'node:assert/strict';
import test from 'node:test';
import fc from 'fast-check';
import {
  projectDomain,
  projectionCapability,
} from '../src/project-domain.mjs';

test('bounded integers use a real fast-check arbitrary', () => {
  const samples = sample({ kind: 'integer', min: -3, max: 7 });
  assert.ok(samples.every((value) => Number.isInteger(value) && value >= -3 && value <= 7));
});

test('strings are arbitrary UTF-16 code-unit sequences with declared lengths', () => {
  const samples = sample({ kind: 'string', minLength: 2, maxLength: 4 });
  assert.ok(samples.every((value) => typeof value === 'string' && value.length >= 2 && value.length <= 4));
});

test('unbounded numbers include ECMAScript special values', () => {
  const samples = sample(
    {
      kind: 'number',
      min: { value: 'negative-infinity' },
      max: { value: 'positive-infinity' },
      allowNaN: true,
    },
    500,
  );

  assert.ok(samples.some(Number.isNaN));
  assert.ok(samples.includes(Number.NEGATIVE_INFINITY));
  assert.ok(samples.includes(Number.POSITIVE_INFINITY));
  assert.ok(samples.some((value) => Object.is(value, -0)));
});

test('bounded numbers exclude NaN and values outside their inclusive bounds', () => {
  const samples = sample({
    kind: 'number',
    min: taggedNumber(-1.5),
    max: taggedNumber(2.5),
    allowNaN: false,
  });
  assert.ok(samples.every((value) => !Number.isNaN(value) && value >= -1.5 && value <= 2.5));
});

for (const [name, domain, predicate] of [
  ['boolean', { kind: 'boolean' }, (value) => typeof value === 'boolean'],
  [
    'constant -0',
    { kind: 'constant', value: { kind: 'number', value: 'finite', bits: '8000000000000000' } },
    (value) => Object.is(value, -0),
  ],
  [
    'optional undefined',
    { kind: 'optional', value: { kind: 'integer', min: -2, max: 2 }, nil: { kind: 'undefined' } },
    (value) => value === undefined || (Number.isInteger(value) && value >= -2 && value <= 2),
  ],
  [
    'optional null',
    { kind: 'optional', value: { kind: 'boolean' }, nil: { kind: 'null' } },
    (value) => value === null || typeof value === 'boolean',
  ],
  [
    'tuple',
    { kind: 'tuple', elements: [{ kind: 'boolean' }, { kind: 'integer', min: 0, max: 3 }] },
    (value) => Array.isArray(value) && value.length === 2 && typeof value[0] === 'boolean',
  ],
  [
    'nested array',
    {
      kind: 'array',
      element: { kind: 'array', element: { kind: 'integer', min: 0, max: 3 }, minLength: 1, maxLength: 2 },
      minLength: 1,
      maxLength: 4,
    },
    (value) => Array.isArray(value)
      && value.length >= 1
      && value.length <= 4
      && value.every((inner) => inner.length >= 1 && inner.length <= 2),
  ],
]) {
  test(`${name} projects to values satisfying the common domain`, () => {
    assert.ok(sample(domain).every(predicate));
  });
}

test('fast-check capability is exact for supported recursive domains', () => {
  assert.deepEqual(
    projectionCapability({
      kind: 'array',
      element: { kind: 'tuple', elements: [{ kind: 'boolean' }, { kind: 'string', minLength: 0, maxLength: 3 }] },
      minLength: 0,
      maxLength: 2,
    }),
    {
      backendId: 'fast-check',
      backendVersion: '4.9.0',
      level: 'exact',
      diagnostics: [],
    },
  );
});

test('unknown domain kinds are rejected and reported as unsupported', () => {
  assert.throws(() => projectDomain({ kind: 'object' }), /domain\.kind\.unknown/);
  assert.deepEqual(
    projectionCapability({ kind: 'object' }, 'inputs[0].domain'),
    {
      backendId: 'fast-check',
      backendVersion: '4.9.0',
      level: 'unsupported',
      diagnostics: [{
        code: 'domain.kind.unknown',
        message: 'Unknown property domain kind: object',
        path: 'inputs[0].domain',
      }],
    },
  );
});

function sample(domain, numRuns = 100) {
  return fc.sample(projectDomain(domain), { seed: 42, numRuns });
}

function taggedNumber(value) {
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  view.setFloat64(0, value, false);
  return { value: 'finite', bits: view.getBigUint64(0, false).toString(16).padStart(16, '0') };
}
