/** Adapted from fast-check's ArrayArbitrary.spec.ts biasIts property; see manifest.json. */
export function removeDuplicates(values: number[]): number[] {
    return [...new Set(values)];
}

/** The upstream suite deliberately uses this faulty implementation to test generation bias. */
export function removeDuplicatesIdentity(values: number[]): number[] {
    return [...values];
}

/** Original oracle: expect(filtered).toHaveLength(new Set(filtered).size). */
export function hasNoDuplicates(filtered: number[]): boolean {
    return filtered.length === new Set(filtered).size;
}

export function correctUniqueProperty(values: number[]): boolean {
    return hasNoDuplicates(removeDuplicates(values));
}

export function identityUniqueProperty(values: number[]): boolean {
    return hasNoDuplicates(removeDuplicatesIdentity(values));
}
