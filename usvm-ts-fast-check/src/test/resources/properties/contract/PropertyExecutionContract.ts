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

export function throwingOpaquePrecondition(_value: number): boolean {
    throw Object.create(null);
}

export async function asyncThrowingOpaquePrecondition(value: number): Promise<boolean> {
    return throwingOpaquePrecondition(value);
}

export function throwingUnprintableErrorPrecondition(_value: number): boolean {
    const error = new Error();
    Object.defineProperty(error, 'message', {
        get() { throw new Error('message getter failed'); },
    });

    throw error;
}

export async function asyncThrowingUnprintableErrorPrecondition(value: number): Promise<boolean> {
    return throwingUnprintableErrorPrecondition(value);
}

export function throwingUnprintableNamePrecondition(_value: number): boolean {
    const error = new Error();
    Object.defineProperty(error, 'name', { value: Object.create(null) });

    throw error;
}

export function nonBooleanPrecondition(_value: number): number {
    return 1;
}

export function throwingWhenPositivePrecondition(value: number): boolean {
    if (value > 0) {
        throw 'positive precondition';
    }

    return true;
}

export async function asyncThrowingWhenPositivePrecondition(value: number): Promise<boolean> {
    if (value > 0) {
        throw 'positive async precondition';
    }

    return true;
}

export function falsePredicate(_value: number): boolean {
    return false;
}

export function throwingPredicate(_value: number): boolean {
    throw 'predicate exploded';
}

export function throwingTimeoutMessagePredicate(_value: number): boolean {
    throw new Error('Property timeout: exceeded limit of 20 milliseconds');
}

export async function asyncThrowingTimeoutMessagePredicate(value: number): Promise<boolean> {
    return throwingTimeoutMessagePredicate(value);
}

export function assertionPredicate(_value: number): boolean {
    throw 'AssertionError: contract assertion';
}

export function nonBooleanPredicate(_value: number): number {
    return 1;
}

export function nonBooleanWhenPositivePredicate(value: number): number | boolean {
    return value > 0 ? 42 : false;
}

export async function asyncNonBooleanWhenPositivePredicate(value: number): Promise<number | boolean> {
    return value > 0 ? 42 : false;
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
