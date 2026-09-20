export function nestedLowering(value: number): number {
    if (value < 0) {
        throw new Error('negative');
    }

    return Math.round(value) / 2;
}

export function separateBranches(value: number): number {
    if (value < 0) {
        return -value;
    } else {
        return value;
    }
}
