export class ArraySparseGrowthSource {
    static numbers(): number {
        const values: number[] = [];
        values[2] = 7;
        return values.length === 3 && values[0] === undefined && values[1] === undefined && values[2] === 7 ? 1 : 0;
    }

    static booleans(): number {
        const values: boolean[] = [];
        values[2] = false;
        return values.length === 3 && values[0] === undefined && values[1] === undefined && values[2] === false ? 1 : 0;
    }

    static references(): number {
        const cache: number[][] = [];
        const item = [7, 11];
        cache[2] = item;
        return cache.length === 3 && cache[0] === undefined && cache[1] === undefined && cache[2] === item ? 1 : 0;
    }

    static widenedAlias(): number {
        const values: number[] = [];
        const alias: any[] = values;
        alias[2] = true;
        return values.length === 3 && values[0] === undefined && alias[1] === undefined && alias[2] === true ? 1 : 0;
    }

    static unresolvedValues(): number {
        const values: unknown[] = [];
        values[2] = undefined;
        values[3] = true;
        return values.length === 4 && values[0] === undefined && values[1] === undefined &&
            values[2] === undefined && values[3] === true ? 1 : 0;
    }

    static shrinkThenRegrow(): number {
        const values = [10, 20, 30, 40];
        values.length = 1;
        values[3] = 7;
        return values.length === 4 && values[0] === 10 && values[1] === undefined &&
            values[2] === undefined && values[3] === 7 ? 1 : 0;
    }

    static movesAndPops(): number {
        const values: number[] = [];
        values[2] = 7;
        if (values.pop() !== 7 || values.pop() !== undefined) return 0;
        values.push(11);
        values.reverse();
        values.unshift(13);
        if (values.shift() !== 13) return 0;
        const copy = [...values];
        return copy.length === 2 && copy[0] === 11 && copy[1] === undefined ? 1 : 0;
    }

    static fillGaps(): number {
        const values: number[] = [];
        values[2] = 7;
        values.fill(9);
        return values[0] === 9 && values[1] === 9 && values[2] === 9 ? 1 : 0;
    }

    static symbolicIndex(index: number): number {
        if (index !== 2 && index !== 3) return 0;
        const values = [7];
        values[index] = 11;
        return values.length === index + 1 && values[0] === 7 && values[1] === undefined &&
            values[index] === 11 ? 1 : 2;
    }

    static beyondCapacity(): number {
        const values: number[] = [];
        values[6] = 7;
        return values.length;
    }

    static denseOnlyConsumer(): number {
        const values: number[] = [];
        values[2] = 7;
        return values.map(value => value + 1).length;
    }

    static denseProofAfterSparseGrowth(): string {
        const values = "a,b,c".split(",");
        values.reverse();
        const alias: any[] = values;
        alias.length = 1;
        alias[4] = "x";
        alias.length = 3;
        return values.join(",");
    }
}
