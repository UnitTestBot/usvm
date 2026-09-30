import assert from 'node:assert/strict';
import fc from 'fast-check';
import {
    correctUniqueProperty,
    identityUniqueProperty,
} from './uniqueArray.ts';
import {
    absoluteValue,
    absoluteValueProperty,
    constantZeroProperty,
    correctAdvanceProperty,
    falseFloatRoundTrip,
    mutantAdvanceProperty,
} from './numericRelations.ts';
import { originalChunkOracle } from './esToolkitChunk.ts';

const boundedArray = fc.array(fc.integer({ min: 0, max: 10 }), { maxLength: 8 });
const boundedInteger = fc.integer({ min: -16, max: 16 });
const settings = { seed: 20260930, numRuns: 500 };

assert.equal(fc.check(fc.property(boundedArray, correctUniqueProperty), settings).failed, false);
assert.equal(identityUniqueProperty([0, 0]), false);

assert.equal(
    fc.check(fc.property(boundedArray, fc.integer({ min: 1, max: 8 }), originalChunkOracle), settings).failed,
    false,
);

assert.equal(fc.check(fc.property(boundedInteger, correctAdvanceProperty), settings).failed, false);
assert.equal(mutantAdvanceProperty(7), false);
assert.equal(mutantAdvanceProperty(6), true);

assert.equal(fc.check(fc.property(boundedInteger, absoluteValueProperty), settings).failed, false);
assert.notEqual(absoluteValue(-1), -1);
assert.equal(fc.check(fc.property(boundedInteger, constantZeroProperty), settings).failed, false);

assert.equal(falseFloatRoundTrip(1.8), false);

console.log('development v1 oracle and witness validation passed');
