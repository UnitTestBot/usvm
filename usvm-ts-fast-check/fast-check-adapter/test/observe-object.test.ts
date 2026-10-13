import assert from 'node:assert/strict';
import test from 'node:test';
import { encodeJsValue } from '../src/js-value.js';
import { ObservationRecorder, type ObservationRequest } from '../src/observe-property.js';
import { stringifyProtocolJson } from '../src/protocol-json.js';

const request: ObservationRequest = {
  points: [], sources: [], maxInvocations: 1, maxPointsPerInvocation: 1,
  maxArrayElements: 32, maxBytes: 4096,
};

function capture(value: unknown, limits = request) {
  const recorder = new ObservationRecorder('objects', limits);

  const returned = recorder.run([value], 'unknown', () => value);

  assert.equal(returned, value);
  assert.ok(Buffer.byteLength(stringifyProtocolJson(recorder.result), 'utf8') <= limits.maxBytes);

  return recorder.result.invocations[0]?.input;
}

test('object observations preserve nested special values before mutation', () => {
  const value = { x: -0, nested: { values: [Number.NaN, undefined, '\ud800'] } };
  const expected = encodeJsValue([value]);

  const observed = capture(value);
  value.nested.values[0] = 42;

  assert.deepEqual(observed, { status: 'captured', value: expected });
});

test('object observations refuse accessors and proxies without running their hooks', () => {
  let hookCalls = 0;
  const accessor = Object.defineProperty({}, 'x', {
    enumerable: true, get: () => { hookCalls += 1; throw new Error('getter'); },
  });
  const proxy = new Proxy({}, {
    ownKeys: () => { hookCalls += 1; throw new Error('proxy'); },
    getPrototypeOf: () => { hookCalls += 1; throw new Error('proxy'); },
  });

  assert.deepEqual(capture(accessor), { status: 'unsupported', reason: 'accessor' });
  assert.deepEqual(capture(proxy), { status: 'unsupported', reason: 'proxy' });
  assert.equal(hookCalls, 0);
});

test('object observations reject lossy keys, prototypes, aliases, and cycles', () => {
  const shared = { x: 1 };
  const cycle: { self?: unknown } = {};
  cycle.self = cycle;
  const hidden = Object.defineProperty({}, 'x', { value: 1 });
  const symbol = { [Symbol('x')]: 1 };
  const unsafe = Object.fromEntries([['__proto__', 1]]);
  const cases: Array<[unknown, string]> = [
    [{ a: shared, b: shared }, 'alias-or-cycle'],
    [cycle, 'alias-or-cycle'],
    [hidden, 'object-hidden-property'],
    [symbol, 'object-hidden-property'],
    [unsafe, 'object-property-name'],
    [Object.create(null), 'object-prototype'],
    [new (class C { x = 1; })(), 'object-prototype'],
  ];

  for (const [value, reason] of cases) {
    assert.deepEqual(capture(value), { status: 'unsupported', reason });
  }
});

test('object capture accounts for escaped field names and stops before later values', () => {
  const limits = { ...request, maxBytes: 1024 };
  const oversizedName = Object.fromEntries([['\u0000'.repeat(1000), 1]]);
  let getterCalls = 0;
  const oversizedValue = Object.defineProperty({ first: ['x'.repeat(262_144)] }, 'later', {
    enumerable: true, get: () => { getterCalls += 1; throw new Error('getter'); },
  });

  for (const value of [oversizedName, oversizedValue]) {
    assert.deepEqual(capture(value, limits), { status: 'truncated', reason: 'byte-budget' });
  }
  assert.equal(getterCalls, 0);
});

test('object observations enforce field count and mixed collection depth', () => {
  const manyFields = Object.fromEntries(Array.from({ length: 33 }, (_, index) => [`x${index}`, 1]));
  const deepValue = { a: [{ b: { c: 1 } }] };

  assert.deepEqual(capture(manyFields), { status: 'unsupported', reason: 'object-field-limit' });
  assert.deepEqual(capture(deepValue), { status: 'unsupported', reason: 'depth-limit' });
});

test('object observation serialization ignores inherited toJSON', () => {
  const original = Object.getOwnPropertyDescriptor(Object.prototype, 'toJSON');
  let calls = 0;
  try {
    Object.defineProperty(Object.prototype, 'toJSON', {
      configurable: true,
      value: () => { calls += 1; throw new Error('toJSON'); },
    });

    const observed = capture({ x: { y: -0 } });

    assert.equal(observed?.status, 'captured');
    assert.equal(calls, 0);
  } finally {
    if (original === undefined) delete (Object.prototype as { toJSON?: unknown }).toJSON;
    else Object.defineProperty(Object.prototype, 'toJSON', original);
  }
});
