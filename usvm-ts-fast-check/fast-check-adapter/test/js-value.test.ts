import assert from 'node:assert/strict';
import test from 'node:test';
import {
  decodeJsValue,
  encodeJsValue,
} from '../src/js-value.js';
import type { JsConcreteValue } from '../src/js-value.js';

interface RoundTripCase {
  tagged: unknown;
  matches: (value: JsConcreteValue) => boolean;
}

test('tagged JavaScript primitives round trip without losing semantics', () => {
  const cases: RoundTripCase[] = [
    { tagged: { kind: 'undefined' }, matches: (value) => value === undefined },
    { tagged: { kind: 'null' }, matches: (value) => value === null },
    { tagged: { kind: 'boolean', value: true }, matches: (value) => value === true },
    { tagged: { kind: 'string', value: 'text' }, matches: (value) => value === 'text' },
    {
      tagged: { kind: 'number', value: 'finite', bits: '0000000000000000' },
      matches: (value) => Object.is(value, 0),
    },
    {
      tagged: { kind: 'number', value: 'finite', bits: '8000000000000000' },
      matches: (value) => Object.is(value, -0),
    },
    { tagged: { kind: 'number', value: 'nan' }, matches: Number.isNaN },
    {
      tagged: { kind: 'number', value: 'positive-infinity' },
      matches: (value) => value === Number.POSITIVE_INFINITY,
    },
    {
      tagged: { kind: 'number', value: 'negative-infinity' },
      matches: (value) => value === Number.NEGATIVE_INFINITY,
    },
    {
      tagged: {
        kind: 'array',
        elements: [{ kind: 'undefined' }, { kind: 'number', value: 'finite', bits: '8000000000000000' }],
      },
      matches: (value) => Array.isArray(value)
        && value[0] === undefined
        && Object.is(value[1], -0),
    },
  ];

  for (const { tagged, matches } of cases) {
    const decoded = decodeJsValue(tagged);
    assert.ok(matches(decoded), `decoded value does not match ${JSON.stringify(tagged)}`);
    assert.deepEqual(encodeJsValue(decoded), tagged);
  }
});

test('tagged finite numbers require exactly sixteen lowercase hexadecimal digits', () => {
  for (const bits of [undefined, '0', '000000000000000G', '800000000000000A']) {
    assert.throws(
      () => decodeJsValue({ kind: 'number', value: 'finite', bits }),
      /js-number\.encoding\.invalid/,
    );
  }
});

test('unknown tagged value kinds are rejected explicitly', () => {
  assert.throws(() => decodeJsValue({ kind: 'symbol' }), /js-value\.kind\.unknown/);
});
