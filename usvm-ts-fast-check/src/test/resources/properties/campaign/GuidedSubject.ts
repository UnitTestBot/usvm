export function branch(value: number): number {
    if (value > 0) {
        return 1;
    } else {
        return 2;
    }
}

export function nested(value: number): number {
    if (value > 0) {
        if (value === 42) {
            return 3;
        } else {
            return 1;
        }
    } else {
        return 2;
    }
}
