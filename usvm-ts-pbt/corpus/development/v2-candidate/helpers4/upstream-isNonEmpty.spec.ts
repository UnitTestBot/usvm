/**
 * This file is part of helpers4.
 * Copyright (C) 2025 baxyz
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

import * as fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { isNonEmpty } from './isNonEmpty';

describe('isNonEmpty — property-based', () => {
  it('is always true for arrays with at least one element', () => {
    fc.assert(
      fc.property(fc.array(fc.anything(), { minLength: 1 }), (arr) => {
        expect(isNonEmpty(arr)).toBe(true);
      }),
    );
  });

  it('is always false for empty arrays', () => {
    expect(isNonEmpty([])).toBe(false);
  });
});

describe('isNonEmpty — contracts', () => {
  it('first element is accessible without undefined when isNonEmpty is true', () => {
    fc.assert(
      fc.property(fc.array(fc.integer(), { minLength: 1 }), (arr) => {
        if (isNonEmpty(arr)) {
          expect(arr[0]).toBeDefined();
        }
      }),
    );
  });
});
