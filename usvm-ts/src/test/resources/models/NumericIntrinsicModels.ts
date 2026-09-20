export function absNegativeZero(): number {
    return Math.abs(-0);
}

export function absNaN(): number {
    return Math.abs(0 / 0);
}

export function absNegativeInfinity(): number {
    return Math.abs(-1 / 0);
}

export function minSignedZero(): number {
    return Math.min(0, -0);
}

export function minNaN(): number {
    return Math.min(1, 0 / 0, 2);
}

export function minNoArguments(): number {
    return Math.min();
}

export function maxSignedZero(): number {
    return Math.max(-0, 0);
}

export function maxInfinity(): number {
    return Math.max(-1 / 0, 1 / 0, 42);
}

export function maxNoArguments(): number {
    return Math.max();
}

export function roundNegativeHalf(): number {
    return Math.round(-0.5);
}

export function roundPositiveHalf(): number {
    return Math.round(0.5);
}

export function roundNegativeOneHalf(): number {
    return Math.round(-1.5);
}

export function roundNaN(): number {
    return Math.round(0 / 0);
}

export function ceilNegativeFraction(): number {
    return Math.ceil(-0.25);
}

export function ceilInfinity(): number {
    return Math.ceil(1 / 0);
}

export function absNoArguments(): number {
    // @ts-expect-error Arity test deliberately omits the first argument.
    return Math.abs();
}

export function absExtraArgument(): number {
    // @ts-expect-error Arity test deliberately supplies an extra argument.
    return Math.abs(-2, true);
}

export function integerPositiveZero(): boolean {
    return Number.isInteger(0);
}

export function integerNegativeZero(): boolean {
    return Number.isInteger(-0);
}

export function integerFraction(): boolean {
    return Number.isInteger(1.5);
}

export function integerNaN(): boolean {
    return Number.isInteger(0 / 0);
}

export function integerInfinity(): boolean {
    return Number.isInteger(1 / 0);
}

export function integerLargeBinary64(): boolean {
    return Number.isInteger(9007199254740992);
}

export function integerBoolean(): boolean {
    return Number.isInteger(true);
}

export function integerNoArguments(): boolean {
    // @ts-expect-error Arity test deliberately omits the first argument.
    return Number.isInteger();
}

export function integerExtraArgument(): boolean {
    // @ts-expect-error Arity test deliberately supplies an extra argument.
    return Number.isInteger(2, true);
}

export function symbolicAbs(value: number): number {
    return Math.abs(value) < 0 ? 0 : 1;
}

export function symbolicInteger(value: number): number {
    return Number.isInteger(value) ? 1 : 0;
}

export function unsupportedAbsDomain(): number {
    // @ts-expect-error Domain fallback deliberately supplies a non-number.
    return Math.abs(true);
}

export function floorNegativeFraction(): number {
    return Math.floor(-1.25);
}

export function floorNegativeZero(): number {
    return Math.floor(-0);
}

export function floorInfinity(): number {
    return Math.floor(1 / 0);
}

export function truncNegativeFraction(): number {
    return Math.trunc(-1.75);
}

export function truncNegativeSmall(): number {
    return Math.trunc(-0.25);
}

export function truncNaN(): number {
    return Math.trunc(0 / 0);
}

export function sqrtFour(): number {
    return Math.sqrt(4);
}

export function sqrtNegative(): number {
    return Math.sqrt(-1);
}

export function sqrtNegativeZero(): number {
    return Math.sqrt(-0);
}

export function sqrtInfinity(): number {
    return Math.sqrt(1 / 0);
}

export function finiteNumber(): boolean {
    return Number.isFinite(42);
}

export function finiteNaN(): boolean {
    return Number.isFinite(0 / 0);
}

export function finiteInfinity(): boolean {
    return Number.isFinite(1 / 0);
}

export function finiteBoolean(): boolean {
    return Number.isFinite(true);
}

export function nanNaN(): boolean {
    return Number.isNaN(0 / 0);
}

export function nanNumber(): boolean {
    return Number.isNaN(42);
}

export function nanBoolean(): boolean {
    return Number.isNaN(true);
}

export function safeIntegerMaximum(): boolean {
    return Number.isSafeInteger(9007199254740991);
}

export function safeIntegerAboveMaximum(): boolean {
    return Number.isSafeInteger(9007199254740992);
}

export function safeIntegerFraction(): boolean {
    return Number.isSafeInteger(1.5);
}

export function safeIntegerInfinity(): boolean {
    return Number.isSafeInteger(1 / 0);
}

export function safeIntegerBoolean(): boolean {
    return Number.isSafeInteger(true);
}
