export function magic(x: number): number {
    if (x * 2 === 98764) {
        return 42;
    }
    if (x > 0) {
        return 1;
    }
    return 0;
}

export function nested(a: number, b: number): number {
    if (a + b === 100) {
        if (a * 2 === b) {
            return 1;
        }
        return 2;
    }
    return 3;
}

export function interval(x: number): number {
    if (x < -10) {
        return -1;
    }
    if (x > 10) {
        return 1;
    }
    return 0;
}

export function conjunction(x: number): number {
    if (x >= 7 && x <= 9) {
        return 1;
    }
    return 0;
}

export function quadratic(x: number): number {
    if (x * x === 144) {
        return 1;
    }
    return 0;
}
