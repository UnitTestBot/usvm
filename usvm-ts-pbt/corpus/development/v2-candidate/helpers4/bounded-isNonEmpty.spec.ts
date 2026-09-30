import * as fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { isNonEmpty } from './isNonEmpty';

describe('bounded core subset of the original isNonEmpty property', () => {
  it('is always true for dense integer arrays with at least one element', () => {
    const boundedNumericArray = fc.array(fc.integer({ min: -16, max: 16 }), {
      minLength: 1,
      maxLength: 8,
    });

    fc.assert(
      fc.property(boundedNumericArray, (arr) => {
        expect(isNonEmpty(arr)).toBe(true);
      }),
      { seed: 20260930, numRuns: 500 },
    );
  });
});
