export function add(a: number, b: number): number {
    return a + b;
}

export function brokenAdd(a: number, b: number): number {
    return a + b + 1;
}

export function divide(a: number, b: number): number {
    return a / b;
}

export function identity(value: number): number {
    return value;
}

export function unreachable(_value: number): number {
    throw new Error('Short-circuited call was evaluated');
}
