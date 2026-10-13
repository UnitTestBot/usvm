// Adapted from fast-check ArrayArbitrary.spec.ts at 85eeab9e87c9d37e66cc7819260e3df1e72305ae (MIT).
// Only this one Vitest assertion is shimmed; the callback body below matches the upstream property.
export class AssertionError extends Error {
    override name = 'AssertionError';
}

export function expect<T>(value: T[]): { toHaveLength(expected: number): void } {
    return {
        toHaveLength(expected: number): void {
            if (value.length !== expected) throw new AssertionError(`Expected length ${expected}, received ${value.length}`);
        },
    };
}

export function originalUniqueAssertion(arr: number[]): void {
    const removeDuplicates = (values: number[]) => [...values];
    const filtered = removeDuplicates(arr);
    expect(filtered).toHaveLength(new Set(filtered).size);
}

export function originalUniqueOracle(values: number[]): boolean {
    originalUniqueAssertion(values);

    return true;
}

export function correctUniqueOracle(values: number[]): boolean {
    const filtered = [...new Set(values)];

    expect(filtered).toHaveLength(new Set(filtered).size);

    return true;
}
