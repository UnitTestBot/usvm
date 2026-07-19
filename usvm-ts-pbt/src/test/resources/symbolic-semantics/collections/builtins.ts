export function arrayIsArray(subject: unknown): boolean {
    if (Array.isArray(subject)) return true;
    return false;
}

export function objectToStringTag(subject: unknown): string {
    return Object.prototype.toString.call(subject);
}

export function objectHasOwn(subject: object, key: string): boolean {
    return Object.prototype.hasOwnProperty.call(subject, key);
}

export function propertyIn(subject: object, key: string): boolean {
    return key in subject;
}

export function mapGet(subject: Map<unknown, unknown>, key: unknown): unknown {
    return subject.get(key);
}

export function mathAbs(subject: number): number {
    return Math.abs(subject);
}

export function mathRoundBelowHalf(): number {
    return Math.round(0.49999999999999994);
}

export function mathRoundLargeInteger(): number {
    return Math.round(4503599627370497);
}

export function mathRoundNegativeZero(): number {
    return Math.round(-0.49999999999999994);
}

export function numberIsInteger(subject: unknown): boolean {
    if (Number.isInteger(subject)) return true;
    return false;
}

export function allocatedArrayLength(size: number): number {
    return new Array(size).length;
}
