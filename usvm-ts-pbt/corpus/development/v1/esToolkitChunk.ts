/** MIT licensed es-toolkit implementation; source and revision are pinned in manifest.json. */
export function chunk(values: readonly number[], size: number): number[][] {
    if (!Number.isInteger(size) || size <= 0) {
        throw new Error('Size must be an integer greater than zero.');
    }

    const chunkLength = Math.ceil(values.length / size);
    const result: number[][] = Array(chunkLength);

    for (let index = 0; index < chunkLength; index++) {
        const start = index * size;
        const end = start + size;

        result[index] = values.slice(start, end);
    }

    return result;
}

/** The three original assertions from es-toolkit's generated-input test. */
export function originalChunkOracle(values: number[], size: number): boolean {
    const chunks = chunk(values, size);
    const flattened = chunks.flat();
    const preservesOrder = flattened.length === values.length &&
        flattened.every((value, index) => value === values[index]);
    const fullExceptLast = chunks.every((item, index) => item.length === size || index === chunks.length - 1);
    const nonemptyBounded = chunks.every(item => item.length > 0 && item.length <= size);

    return preservesOrder && fullExceptLast && nonemptyBounded;
}
