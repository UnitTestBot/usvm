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

export function always(left: number, right: number): boolean {
    return true;
}

export function sparseAdmission(left: number, right: number): boolean {
    const cache: number[] = [];
    cache[9] = left;
    return right === right;
}

export function entryMutation(left: number, right: number): boolean {
    left = right;
    return left === right;
}

export function throwing(left: number, right: number): boolean {
    if (left > right) throw new Error('original fault');
    return true;
}
