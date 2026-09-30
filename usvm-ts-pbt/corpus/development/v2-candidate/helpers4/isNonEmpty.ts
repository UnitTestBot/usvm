/**
 * This file is part of helpers4.
 * Copyright (C) 2025 baxyz
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

/**
 * Checks if an array is non-empty (has at least one element).
 * `null` and `undefined` are treated as empty arrays and return `false`.
 * @param value - The array to check
 * @returns `true` if the array has at least one element; `false` for empty, `null`, or `undefined`
 * @example
 * isNonEmpty([1, 2, 3]) // => true
 * isNonEmpty([])        // => false
 * isNonEmpty(null)      // => false
 * isNonEmpty(undefined) // => false
 * @since 2.0.3
 */
export function isNonEmpty<T>(value: readonly T[] | null | undefined): value is readonly [T, ...T[]] {
  return value != null && value.length > 0;
}
