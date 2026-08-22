import assert from 'node:assert/strict';
import test from 'node:test';
import fc from 'fast-check';
import {
  projectDomain,
  projectionCapability,
} from '../src/project-domain.js';

test('bounded integers use a real fast-check arbitrary', () => {
  const samples = sample({ kind: 'integer', min: -3, max: 7 });
  assert.ok(samples.every(
    (value) => typeof value === 'number' && Number.isInteger(value) && value >= -3 && value <= 7,
  ));
});

test('strings are arbitrary UTF-16 code-unit sequences with declared lengths', () => {
  const samples = sample({ kind: 'string', minLength: 2, maxLength: 4 });
  assert.ok(samples.every(
    (value) => typeof value === 'string' && value.length >= 2 && value.length <= 4,
  ));
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

  assert.ok(samples.some((value) => typeof value === 'number' && Number.isNaN(value)));
  assert.ok(samples.includes(Number.NEGATIVE_INFINITY));
  assert.ok(samples.includes(Number.POSITIVE_INFINITY));
  assert.ok(samples.some((value) => typeof value === 'number' && Object.is(value, -0)));
});

test('bounded numbers exclude NaN and values outside their inclusive bounds', () => {
  const samples = sample({
    kind: 'number',
    min: taggedNumber(-1.5),
    max: taggedNumber(2.5),
    allowNaN: false,
  });
  assert.ok(samples.every(
    (value) => typeof value === 'number' && !Number.isNaN(value) && value >= -1.5 && value <= 2.5,
  ));
});

test('singleton infinity ranges project without an empty finite arbitrary', () => {
  const bounds: Array<readonly [unknown, number]> = [
    [{ value: 'negative-infinity' }, Number.NEGATIVE_INFINITY],
    [{ value: 'positive-infinity' }, Number.POSITIVE_INFINITY],
  ];
  for (const [bound, expected] of bounds) {
    const samples = sample({
      kind: 'number',
      min: bound,
      max: bound,
      allowNaN: false,
    });
    assert.ok(samples.every((value) => value === expected));
  }
});

interface ProjectionCase {
  name: string;
  domain: unknown;
  matches: (value: unknown) => boolean;
}

const projectionCases: ProjectionCase[] = [
  {
    name: 'boolean',
    domain: { kind: 'boolean' },
    matches: (value) => typeof value === 'boolean',
  },
  {
    name: 'constant -0',
    domain: { kind: 'constant', value: { kind: 'number', value: 'finite', bits: '8000000000000000' } },
    matches: (value) => Object.is(value, -0),
  },
  {
    name: 'optional undefined',
    domain: { kind: 'optional', value: { kind: 'integer', min: -2, max: 2 }, nil: { kind: 'undefined' } },
    matches: (value) => value === undefined
      || (typeof value === 'number' && Number.isInteger(value) && value >= -2 && value <= 2),
  },
  {
    name: 'optional null',
    domain: { kind: 'optional', value: { kind: 'boolean' }, nil: { kind: 'null' } },
    matches: (value) => value === null || typeof value === 'boolean',
  },
  {
    name: 'tuple',
    domain: { kind: 'tuple', elements: [{ kind: 'boolean' }, { kind: 'integer', min: 0, max: 3 }] },
    matches: (value) => Array.isArray(value)
      && value.length === 2
      && typeof value[0] === 'boolean',
  },
  {
    name: 'nested array',
    domain: {
      kind: 'array',
      element: { kind: 'array', element: { kind: 'integer', min: 0, max: 3 }, minLength: 1, maxLength: 2 },
      minLength: 1,
      maxLength: 4,
    },
    matches: (value) => Array.isArray(value)
      && value.length >= 1
      && value.length <= 4
      && value.every((inner: unknown) => Array.isArray(inner) && inner.length >= 1 && inner.length <= 2),
  },
];

for (const { name, domain, matches } of projectionCases) {
  test(`${name} projects to values satisfying the common domain`, () => {
    assert.ok(sample(domain).every(matches));
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

function sample(domain: unknown, numRuns = 100): unknown[] {
  return fc.sample(projectDomain(domain), { seed: 42, numRuns });
}

function taggedNumber(value: number): { value: 'finite'; bits: string } {
  const buffer = new ArrayBuffer(8);
  const view = new DataView(buffer);
  view.setFloat64(0, value, false);
  return { value: 'finite', bits: view.getBigUint64(0, false).toString(16).padStart(16, '0') };
}
