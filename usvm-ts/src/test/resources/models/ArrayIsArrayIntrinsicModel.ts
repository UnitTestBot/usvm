export function scalarValuesAreNotArrays(): boolean {
    return !Array.isArray(42)
        && !Array.isArray(true)
        && !Array.isArray('value')
        && !Array.isArray(undefined)
        && !Array.isArray(null);
}

export function ordinaryArraysAreArrays(): boolean {
    return Array.isArray([]) && Array.isArray([1, 2, 3]);
}

export function stringArrayIsArray(value: string[]): boolean {
    return Array.isArray(value);
}

export function nestedArrayIsArray(value: number[][]): boolean {
    return Array.isArray(value);
}

export function missingArgumentIsNotArray(): boolean {
    // @ts-expect-error Arity test deliberately omits the first argument.
    return !Array.isArray();
}

export function nullParameterIsNotArray(value: null): boolean {
    return Array.isArray(value);
}

export function undefinedParameterIsNotArray(value: undefined): boolean {
    return Array.isArray(value);
}

declare const FakeArray: any;

export function nonBuiltinIsArrayFallsBack(): boolean {
    return FakeArray.isArray([]);
}

export function nullishAnyArrayFallback(value: any): boolean {
    return Array.isArray(value ?? []);
}

export function sourceShadowKeepsItsImplementation(): boolean {
    const Array = { isArray: (_value: unknown): boolean => false };
    return Array.isArray([]);
}
