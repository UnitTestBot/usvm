/** Branchless perturbation at seven; the user property checks parity, not exact output. */
export function advanceCorrect(input: number): number {
    return input + 1;
}

export function advanceMutantSeven(input: number): number {
    return input + 1 + Math.trunc(1 / (Math.abs(input - 7) + 1));
}

export function changesParity(input: number, result: number): boolean {
    return result % 2 !== input % 2;
}

export function correctAdvanceProperty(input: number): boolean {
    return changesParity(input, advanceCorrect(input));
}

export function mutantAdvanceProperty(input: number): boolean {
    return changesParity(input, advanceMutantSeven(input));
}

/** A passing oracle despite refutation of the sample-derived output === input hypothesis. */
export function absoluteValue(input: number): number {
    return Math.abs(input);
}

export function absoluteValueProperty(input: number): boolean {
    return absoluteValue(input) >= 0;
}

/** A deliberately unproductive relation: every legal input has the same output. */
export function constantZero(_input: number): number {
    return 0;
}

export function constantZeroProperty(input: number): boolean {
    return constantZero(input) === 0;
}

/** Excluded false specification: floating-point division need not round-trip exactly. */
export function falseFloatRoundTrip(input: number): boolean {
    return (input / 3) * 3 === input;
}
