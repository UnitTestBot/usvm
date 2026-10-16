export function sparseWrite(index: number): number {
    const cache: number[] = [];
    cache[index] = 123;
    return cache[index];
}

export function supportedOrSparse(index: number): number {
    if (index === 0) {
        return 1;
    }

    return sparseWrite(index);
}
