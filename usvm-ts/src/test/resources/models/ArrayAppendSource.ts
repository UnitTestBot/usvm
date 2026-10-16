export class ArrayAppendSource {
    static appendToEmpty(): number {
        const values: number[] = [];
        values[0] = 7;
        values[values.length] = 11;
        return values.length === 2 && values[0] === 7 && values[1] === 11 ? 1 : 0;
    }

    static appendThroughMixedAlias(): number {
        const values = [7];
        const alias: any[] = values;
        alias[alias.length] = null;
        alias[alias.length] = true;
        return values.length === 3 && alias[0] === 7 && alias[1] === null && alias[2] === true ? 1 : 0;
    }

    static overwriteAtCapacity(): number {
        const values = [1, 2, 3];
        values[0] = 7;
        return values.length === 3 && values[0] === 7 && values[2] === 3 ? 1 : 0;
    }

    static appendAfterTruncate(): number {
        const values = [1, 2, 3];
        values.length = 1;
        values[values.length] = 7;
        return values.length === 2 && values[1] === 7 && values[2] === undefined ? 1 : 0;
    }

    static appendBeyondCapacity(): number {
        const values = [1, 2, 3];
        values[values.length] = 7;
        return values.length;
    }

    static sparseGrowth(): number {
        const values = [1];
        values[2] = 7;
        return values.length === 3 && values[0] === 1 && values[1] === undefined && values[2] === 7 ? 1 : 0;
    }
}
