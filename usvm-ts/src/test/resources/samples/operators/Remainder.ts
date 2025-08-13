// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Remainder {
    // number % number
    remNumberAndNumber(a: number, b: number): number {
        let res = a % b;

        if (Number.isNaN(a)) return res; // NaN
        if (Number.isNaN(b)) return res; // NaN
        if (a == Infinity) return res; // NaN
        if (a == -Infinity) return res; // NaN
        if (b == Infinity) return res; // a
        if (b == -Infinity) return res; // a
        if (b == 0) return res; // NaN
        if (a == 0) return res; // a
        if (b == 1) return res; // fractional part of a
        if (b == -1) return res; // fractional part of a

        if (a == 7 && b == 4) return res; // 3
        if (a == 7 && b == -4) return res; // 3
        if (a == -7 && b == 4) return res; // -3
        if (a == -7 && b == -4) return res; // -3

        return res;
    }

    // boolean % boolean
    remBooleanAndBoolean(a: boolean, b: boolean): number {
        let res = a % b;

        if (a && b) return res; // 0
        if (a && !b) return res; // NaN
        if (!a && b) return res; // 0
        if (!a && !b) return res; // NaN

        // unreachable
    }

    // number % boolean
    remNumberAndBoolean(a: number, b: boolean): number {
        let res = a % b;

        if (Number.isNaN(a)) {
            if (b === true) return res; // NaN
            if (b === false) return res; // NaN
        }
        if (b === false) return res; // NaN
        if (b === true) {
            if (a === Infinity) return res; // NaN
            if (a === -Infinity) return res; // NaN
            if (a === 0) return res; // 0
            if (a > 0) return res; // 0
            if (a < 0) return res; // -0
        }

        // unreachable
    }

    // any % any
    remUnknown(a, b): number {
        let res = a % b;

        if (res === 4) return res;
        if (res != res) return res;

        return res;
    }
}
