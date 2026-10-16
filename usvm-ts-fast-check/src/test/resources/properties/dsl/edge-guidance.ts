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

export function withoutElse(value: number): number {
    let result = 1;
    if (value > 0) {
        result = 1;
    }
    return result;
}

export function hidden(value: number): number {
    if (value > 0) {
        return nested(value);
    } else {
        return 2;
    }
}
