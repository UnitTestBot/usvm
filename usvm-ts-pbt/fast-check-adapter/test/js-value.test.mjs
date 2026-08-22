import assert from 'node:assert/strict';
import test from 'node:test';
import {
  decodeJsValue,
  encodeJsValue,
} from '../src/js-value.mjs';

test('tagged JavaScript primitives round trip without losing semantics', () => {
  const cases = [
    [{ kind: 'undefined' }, (value) => value === undefined],
    [{ kind: 'null' }, (value) => value === null],
    [{ kind: 'boolean', value: true }, (value) => value === true],
    [{ kind: 'string', value: 'text' }, (value) => value === 'text'],
    [{ kind: 'number', value: 'finite', bits: '0000000000000000' }, (value) => Object.is(value, 0)],
    [{ kind: 'number', value: 'finite', bits: '8000000000000000' }, (value) => Object.is(value, -0)],
    [{ kind: 'number', value: 'nan' }, Number.isNaN],
    [{ kind: 'number', value: 'positive-infinity' }, (value) => value === Number.POSITIVE_INFINITY],
    [{ kind: 'number', value: 'negative-infinity' }, (value) => value === Number.NEGATIVE_INFINITY],
  ];

  for (const [tagged, predicate] of cases) {
    const decoded = decodeJsValue(tagged);
    assert.ok(predicate(decoded), `decoded value does not match ${JSON.stringify(tagged)}`);
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
