export function positive(value: number): number {
    if (value > 0) {
        return 1;
    } else {
        return 0;
    }
}

export function negative(value: number): number {
    if (value < 0) {
        return 1;
    } else {
        return 0;
    }
}
