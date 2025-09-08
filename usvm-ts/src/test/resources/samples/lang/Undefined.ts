// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Undefined {
    isUndefined(x): number {
        if (x === undefined) return 1;
        return 2;
    }

    isUndefinedOrNull(x): number {
        if (x == undefined) {
            if (x === undefined) return 1;
            if (x === null) return 2;
            return -1; // unreachable
        }
        return 3; // not null or undefined
    }
}
