// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Optional {
    nullableArgument(x: number | null): number {
        let value: number = 42;
        if (x === undefined) return 0;
        if (x !== null) {
            if (x === 1) return x; // 1
            value = x;
            if (x === 2) return value; // 2
            return 10;
        }
        return 0;
    }
}
