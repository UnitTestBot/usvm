class Remainder {
    twoNumbersRemainder(a: number, b: number): number {
        res = a % b;

        if (a != a || b != b) {
            return res // NaN
        }

        if (a == Infinity || a == -Infinity) {
            return res // NaN
        }

        if (a == 0 && b == 0) {
            return res // NaN
        }

        if (b == 0) {
            return res // NaN
        }

        if (b == Infinity || b == -Infinity) {
            return res // a
        }

        if (a == 0) {
            return res // a
        }

        if (res == 4) {
            return res
        }

        return res
    }

    booleanRemainder(a: boolean, b: boolean): number {
        res = a % b;

        if (res == 0) {
            return res
        }

        return res
    }

    mixedRemainder(a: number, b: boolean): number {
        res = a % b;
        if (res == 4) {
            return res
        }

        if (res != res) {
            return res
        }

        return res
    }

    unknownRemainder(a, b): number {
        res = a % b;

        if (res == 4) {
            return res;
        }

        if (res != res) {
            return res;
        }

        return res;
    }
}
