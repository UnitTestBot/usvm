// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Remainder {
    remTruncationExamples(): boolean {
        return 7 % 4 === 3
            && 7 % -4 === 3
            && -7 % 4 === -3
            && -7 % -4 === -3;
    }

    remExceptionalValues(): boolean {
        return 1 / (0 % 4) === Infinity
            && 1 / (-0 % 4) === -Infinity
            && 5 % Infinity === 5
            && Number.isNaN(5 % 0)
            && Number.isNaN(Infinity % 3)
            && Number.isNaN((0 / 0) % 3);
    }

    remFiniteExtremes(): boolean {
        return 1e308 % 1.5e308 === 1e308
            && -1e308 % 1.5e308 === -1e308
            && 1e308 % 5e-324 === 0
            && 5e-324 % 1e308 === 5e-324;
    }

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
