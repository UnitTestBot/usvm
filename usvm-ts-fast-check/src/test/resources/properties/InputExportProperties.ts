export function holds(value: number): boolean {
    return value === value;
}

export function arrayHolds(values: string[]): boolean {
    return values.length >= 0;
}

export function textHolds(value: string): boolean {
    return value.length >= 0;
}

export function mutatingPrecondition(values: number[]): boolean {
    values.push(99);
    return true;
}

export function mutatingPredicate(values: number[]): boolean {
    const admitted = values[values.length - 1] === 99;
    values.push(100);
    return admitted;
}

export async function neverCompletes(value: number): Promise<boolean> {
    return new Promise(() => {});
}
