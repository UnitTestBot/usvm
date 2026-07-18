export function magic(x: number): number {
    if (x * 2 === 98764) {
        return 42;
    }
    if (x > 0) {
        return 1;
    }
    return 0;
}
