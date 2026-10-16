export function admitted(left: number, right: number): boolean {
    return left % 2 === 0 && right >= 0;
}

export function holds(left: number, right: number): boolean {
    return left >= 0 && right <= 1000;
}

export function failsNearby(left: number, right: number): boolean {
    return left !== 498;
}
