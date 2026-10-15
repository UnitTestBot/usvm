export function ordered(left: number, right: number): boolean {
    return left <= right;
}

export function reflexive(left: number, right: number): boolean {
    return left === left;
}

export function nonNegative(left: number, right: number): boolean {
    return left >= 0;
}

export function independent(left: number, right: number): boolean {
    return left !== 1;
}
