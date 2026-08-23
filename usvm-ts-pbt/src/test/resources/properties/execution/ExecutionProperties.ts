export function alwaysTrue(_value: number): boolean {
    return true;
}

export function sumIsCommutative(left: number, right: number): boolean {
    return left + right === right + left;
}

export function isNegative(value: number): boolean {
    return value < 0;
}

export async function asyncAlwaysTrue(_value: number): Promise<boolean> {
    return true;
}

export async function asyncIsOne(value: number): Promise<boolean> {
    return value === 1;
}

export function isNotSeven(value: number): boolean {
    return value !== 7;
}

export async function neverCompletes(_value: number): Promise<boolean> {
    await new Promise<never>(() => undefined);
    return true;
}
