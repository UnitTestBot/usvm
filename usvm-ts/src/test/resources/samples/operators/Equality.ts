// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Equality {
    eqBoolWithBool(a: boolean): number {
        if (a == true) return 1;
        if (a == false) return 2;
        return -1; // unreachable
    }

    eqNumberWithNumber(a: number): number {
        if (a != a) return 1; // NaN
        if (a == 42) return 2; // 42
        return 3; // other, not NaN or 42
    }

    eqNumberWithBool(a: number): number {
        if (a == true) return 1; // 1
        if (a == false) return 2; // 0
        return 3; // other, not 0 or 1
    }

    eqBoolWithNumber(a: boolean): number {
        if (a == 0) return 1; // false
        if (a == 1) return 2; // true
        return -1; // unreachable
    }

    eqStringWithString(a: string): number {
        if (a == "123") return 1; // "123"
        return 2; // other, not "123"
    }

    eqBigintWithBigint(a: bigint): number {
        if (a == 42n) return 1; // 42n
        return 2; // other, not 42n
    }

    eqObjectWithObject(a: object): number {
        if (a == { b: 0 }) return -2; // unreachable, since RHS is allocated
        if (a == a) return 1;
        return -1; // unreachable
    }

    eqArrayWithArray(a: any[]): number {
        if (a == []) return -1; // unreachable
        if (a == a) return 1;
        return -1; // unreachable
    }

    eqArrayWithBoolean(): number {
        if ([] == true) return -1; // unreachable
        if ([0] == true) return -1; // unreachable
        if ([1] == false) return -1; // unreachable
        if ([42] == false) return -1; // unreachable
        return 0;
    }
}
