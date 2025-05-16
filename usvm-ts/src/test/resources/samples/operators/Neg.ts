// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Neg {
    negateNumber(x: number): number {
        let y = -x
        if (x != x) return y // -NaN == NaN
        if (x == 0) return y // -0 == 0
        if (x > 0) return y // -(x>0) == (x<0)
        if (x < 0) return y // -(x<0) == (x>0)
        // unreachable
    }

    negateBoolean(x: boolean): number {
        let y = -x
        if (x) return y // -true == -1
        if (!x) return y // -false == -0
        // unreachable
    }

    negateUndefined(): number {
        let x = undefined
        return -x // -undefined == NaN
    }
}
