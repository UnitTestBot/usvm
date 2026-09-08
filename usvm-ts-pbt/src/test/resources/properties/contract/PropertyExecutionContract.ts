export function alwaysTrue(_value: number): boolean {
    return true;
}

export function truePrecondition(_value: number): boolean {
    return true;
}

export function falsePrecondition(_value: number): boolean {
    return false;
}

export function throwingPrecondition(_value: number): boolean {
    throw 'precondition exploded';
}

export function nonBooleanPrecondition(_value: number): number {
    return 1;
}

export function falsePredicate(_value: number): boolean {
    return false;
}

export function throwingPredicate(_value: number): boolean {
    throw 'predicate exploded';
}

export function assertionPredicate(_value: number): boolean {
    throw 'AssertionError: contract assertion';
}

export function nonBooleanPredicate(_value: number): number {
    return 1;
}

export function literalFalsePredicate(_value: number): false {
    return false;
}

export function literalTruePredicate(_value: number): true {
    return true;
}

export function neverPredicate(_value: number): never {
    throw 'never predicate exploded';
}

export function catchesExpectedException(_value: number): boolean {
    try {
        throw 'expected';
    } catch (error: unknown) {
        return error === 'expected';
    }
}

export function recognizesSpecialValues(
    missing: undefined,
    empty: null,
    negativeZero: number,
    notANumber: number,
    positiveInfinity: number,
    negativeInfinity: number,
): boolean {
    return missing === undefined
        && empty === null
        && Object.is(negativeZero, -0)
        && Number.isNaN(notANumber)
        && positiveInfinity === Number.POSITIVE_INFINITY
        && negativeInfinity === Number.NEGATIVE_INFINITY;
}

export function preservesNestedArrayAlias(values: number[][]): boolean {
    return values.length === 2 && values[0] === values[1];
}

export function isolatesPredicateMutation(value: number[]): boolean {
    const pristine = value.length === 1 && value[0] === 1;
    value[0] = 2;

    return pristine;
}

export function mutatesAndFails(value: number[]): boolean {
    value[0] = 999;

    return false;
}
