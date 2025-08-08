// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Neg {
    negateNumber(a: number): number {
        let res = -a;
        if (Number.isNaN(a)) return res; // -NaN == NaN
        if (a == 0) return res; // -0 == 0
        if (a > 0) return res; // -(x>0) == (x<0)
        if (a < 0) return res; // -(x<0) == (x>0)
        // unreachable
    }

    negateBoolean(a: boolean): number {
        let res = -a;
        if (a) return res; // -true == -1
        if (!a) return res; // -false == -0
        // unreachable
    }

    negateUndefined(): number {
        let x = undefined;
        return -x; // -undefined == NaN
    }

    negateNull(): number {
        let x = null;
        return -x; // -null == -0
    }

    negateString(a: string): number {
        let res = -a;

        if (a === "") return res; // -"" == -0
        if (a === "0") return res; // -"0" == -0
        if (a === "1") return res; // -"1" == -1
        if (a === "-42") return res; // -"-42" == 42
        if (a === "NaN") return res; // -"NaN" == NaN
        if (a === "Infinity") return res; // -"Infinity" == -Infinity
        if (a === "-Infinity") return res; // -"Infinity" == Infinity

        if (a === "hello") return res; // -"hello" == NaN
        if (a === "true") return res; // -"true" == NaN
        if (a === "false") return res; // -"false" == NaN
        if (a === "undefined") return res; // -"undefined" == NaN
        if (a === "null") return res; // -"null" == NaN
        if (a === "(42)") return res; // -"(42)" == NaN

        if (a === "{}") return res; // -"{}" == NaN
        if (a === "{foo: 42}") return res; // -"{foo: 42}" == NaN
        if (a === "[]") return res; // -"[]" == NaN
        if (a === "[42]") return res; // -"[42]" == 42

        return res; // can be either NaN or a number
    }
}
