class Add {
    addBoolAndBool(a: boolean, b: boolean): number {
        res = a + b;

        if (res == 0 && (a || b)) return -1 // unreachable
        if (res < 0 || res > 2) return -1 // unreachable
        if (res == 1 && a && b) return -1 // unreachable
        if (res == 2 && !(a && b)) return -1 // unreachable

        if (res == 0) return 1 // false false
        if (res == 1 && !a) return 2 // false true
        if (res == 1 && !b) return 3 // true false
        if (res == 2) return 4 // true true

        return -1 // unreachable
    }

    addBoolAndNumber(a: boolean, b: number): number {
        res = a + b;

        if (res == 0 && a) return 1 // true -1
        if (res == 0 && !a) return 2 // false 0

        if (res == 6 && a) return 3

        if (b != b) return res // _ NaN

        return 4
    }

    addNumberAndNumber(a: number, b: number): number {
        res = a + b;

        if (a != a) return res
        if (b != b) return res

        if (res == 1.1) return res

        return 0
    }

    addNumberAndUndefined(a: number): number {
        res = a + undefined;

        if (res == 0) return -1 // unreachable

        return res
    }

    addNumberAndNull(a: number): number {
        res = a + null;

        if (res != a) return res

        return res
    }

    addUnknownValues(a, b) {
        res = a + b;

        if (res != res) return res

        if (res == 7) return res

        if (a === null && b === null) return res

        if (a === undefined || b === undefined) return res

        return 42
    }
}