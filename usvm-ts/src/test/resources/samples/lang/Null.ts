// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Null {
    isNull(x): number {
        if (x === null) return 1;
        return 2;
    }

    isNullOrUndefined(x): number {
        if (x == null) {
            if (x === null) return 1;
            if (x === undefined) return 2;
            return -1; // unreachable
        }
        return 3; // not null or undefined
    }
}
