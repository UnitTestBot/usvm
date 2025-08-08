// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Division {
    divNumberAndNumber(a: number, b: number): number {
        let res = a / b;

        if (a == 12 && b == 3) return res; // 12/3 = 4
        if (a == 7.5 && b == -2.5) return res; // 7.5/-2.5 = -3.0

        if (a == Infinity) {
            if (b == Infinity) return res; // Inf/Inf = NaN
            if (b == -Infinity) return res; // Inf/-Inf = NaN
            if (b != b) return res; // Inf/NaN = NaN
            if (b == 0) return res; // Inf/0 = Infinity
            // if (b == -0) return res; // Inf/-0 = -Infinity
            if (b > 0) return res; // Inf/x = Inf
            if (b < 0) return res; // Inf/-x = -Inf
        }

        if (a == -Infinity) {
            if (b == Infinity) return res; // -Inf/Inf = NaN
            if (b == -Infinity) return res; // -Inf/-Inf = NaN
            if (b != b) return res; // -Inf/NaN = NaN
            if (b == 0) return res; // -Inf/0 = -Infinity
            // if (b == -0) return res; // -Inf/-0 = Infinity
            if (b > 0) return res; // -Inf/x = -Inf
            if (b < 0) return res; // -Inf/-x = Inf
        }

        if (a != a) {
            if (b == Infinity) return res; // NaN/Inf = NaN
            if (b == -Infinity) return res; // NaN/-Inf = NaN
            if (b != b) return res; // NaN/NaN = NaN
            if (b == 0) return res; // NaN/0 = NaN
            // if (b == -0) return res; // NaN/-0 = NaN
            if (b > 0) return res; // NaN/x = NaN
            if (b < 0) return res; // NaN/-x = NaN
        }

        if (a == 0) {
            if (b == Infinity) return res; // 0/Inf = 0
            if (b == -Infinity) return res; // 0/-Inf = -0
            if (b != b) return res; // 0/NaN = NaN
            if (b == 0) return res; // 0/0 = NaN
            // if (b == -0) return res; // 0/-0 = NaN
            if (b > 0) return res; // 0/x = 0
            if (b < 0) return res; // 0/-x = -0
        }

        // if (a == -0) {
        //     if (b == Infinity) return res; // -0/Inf = -0
        //     if (b == -Infinity) return res; // -0/-Inf = 0
        //     if (b != b) return res; // -0/NaN = NaN
        //     if (b == 0) return res; // -0/0 = NaN
        //     if (b == -0) return res; // -0/-0 = NaN
        //     if (b > 0) return res; // -0/x = -0
        //     if (b < 0) return res; // -0/-x = -0
        // }

        if (a > 0) {
            if (b == Infinity) return res; // x/Inf = 0
            if (b == -Infinity) return res; // x/-Inf = -0
            if (b != b) return res; // x/NaN = NaN
            if (b == 0) return res; // x/0 = Infinity
            // if (b == -0) return res; // x/-0 = -Infinity
            if (b > 0) return res; // x/y = non-negative (zero, pos, inf)
            if (b < 0) return res; // x/-y = non-positive (zero, neg, -inf)
        }

        if (a < 0) {
            if (b == Infinity) return res; // -x/Inf = -0
            if (b == -Infinity) return res; // -x/-Inf = 0
            if (b != b) return res; // -x/NaN = NaN
            if (b == 0) return res; // -x/0 = -Infinity
            // if (b == -0) return res; // -x/-0 = Infinity
            if (b > 0) return res; // -x/y = non-positive (zero, neg, -inf)
            if (b < 0) return res; // -x/-y = non-negative (zero, pos, inf)
        }

        // unreachable
    }

    divBooleanAndBoolean(a: boolean, b: boolean): number {
        let res = a / b;

        if (a && b) return res; // true/true = 1
        if (a && !b) return res; // true/false = Infinity
        if (!a && b) return res; // false/true = 0
        if (!a && !b) return res; // false/false = NaN

        // unreachable
    }

    divNumberAndBoolean(a: number, b: boolean): number {
        let res = a / b;

        if (a == Infinity) {
            if (b) return res; // Inf/true = Inf
            if (!b) return res; // Inf/false = Inf
        }

        if (a == -Infinity) {
            if (b) return res; // -Inf/true = -Inf
            if (!b) return res; // -Inf/false = -Inf
        }

        if (a != a) {
            if (b) return res; // NaN/true = NaN
            if (!b) return res; // NaN/false = NaN
        }

        if (a == 0) {
            if (b) return res; // 0/true = 0
            if (!b) return res; // 0/false = NaN
        }

        // if (a == -0) {
        //     if (b) return res; // -0/true = -0
        //     if (!b) return res; // -0/false = NaN
        // }

        if (a > 0) {
            if (b) return res; // x/true = x
            if (!b) return res; // x/false = Infinity
        }

        if (a < 0) {
            if (b) return res; // -x/true = -x
            if (!b) return res; // -x/false = -Infinity
        }

        // unreachable
    }

    unknownDivision(a, b): number {
        let res = a / b;

        if (a === undefined || b === undefined) {
            return res;
        }

        if (res == 4) {
            return res;
        }

        if (res == Infinity) {
            return res;
        }

        if (res == -Infinity) {
            return res;
        }

        if (res != res) {
            return res;
        }

        return res;
    }
}
