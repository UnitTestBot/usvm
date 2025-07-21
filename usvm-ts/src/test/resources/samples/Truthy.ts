// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Truthy {
    arrayTruthy(): number {
        if (![]) return -1; // unreachable
        if (![0]) return -2; // unreachable
        if (![1]) return -3; // unreachable
        return 1;
    }

    unknownFalsy(a: unknown): number {
        if (!a) {
            if (a === null) return 1;
            if (a === undefined) return 2;
            if (a === false) return 3;
            if (Number.isNaN(a)) return 4;
            if (a === 0) return 5;
            // if (a === -0) return 6; // -0 is not distinguishable from 0 in JavaScript
            // if (a === 0n) return 7; // TODO: support bigint
            if (a === '') return 8;

            return -1; // unreachable
        }
        return 0; // else, the value is truthy
    }
}
