// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Equality {
    eqBoolWithBool(a: boolean): number {
        if (a == true) return 1
        if (a == false) return 2
        return -1 // unreachable
    }

    eqNumberWithNumber(a: number): number {
        if (a != a) return 1
        if (a == 42) return 2
        return 3
    }

    eqStringWithString(a: string): number {
        if (a == "123") return 1
        return 2
    }

    eqBigintWithBigint(a: bigint): number {
        if (a == 42n) return 1
        return 2
    }

    eqObjectWithObject(a: object): number {
        if (a == {b: 0}) return -1 // unreachable
        if (a == a) return 1
        return -1 // unreachable
    }

    eqArrayWithArray(a: any[]): number {
        if (a == []) return -1 // unreachable
        if (a == a) return 1
        return -1 // unreachable
    }

    eqArrayWithBoolean(): number {
        if ([] == true) return -1 // unreachable
        if ([0] == true) return -1 // unreachable
        if ([1] == false) return -1 // unreachable
        if ([42] == false) return -1 // unreachable
        return 0
    }
}
