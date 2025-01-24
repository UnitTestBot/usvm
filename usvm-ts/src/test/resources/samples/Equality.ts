class Equality {
    eqBoolWithBool(a: boolean): number {
        if (a == true) {
            return 1
        }
        return -1
    }

    eqNumberWithNumber(a: number): number {
        if (a == 1.1) {
            return 1
        }

        if (a != a) {
            return 2
        }

        return 3
    }

    eqStringWithString(a: string): number {
        if (a == "123") {
            return 1
        }

        return 2
    }

    eqBigintWithBigint(a: bigint): number {
        if (a == 42n) {
            return 1
        }

        if (a == 9999999999999999999999999999999999999n) {
            return 2
        }

        return -1
    }

    eqObjectWithObject(a: object): number {
        if (a == { b: 0}) {
            return -1
        }

        let x = a
        if (x == a) {
            return 1
        }

        return 2
    }

    truthyTypes() {
        if ([] == true) {
            return -1 // unreachable
        }

        if ([] == ![]) {
            if (![] == false) {
                return 1
            }
        }

        return -1 // unreachable
    }

    eqWithItself(a: number): number {
        if (a != a) {
            return 1
        }

        return 2
    }
}