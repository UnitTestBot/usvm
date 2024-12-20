class TypeCoercion {

    argWithConst(a: number): number {
        // @ts-ignore
        if (a == true) return 1
        return 0
    }

    argWithArg(a: boolean, b: number): number {
        // @ts-ignore
        if (a + b == 10.0) {
            return 1
        } else {
            return 0
        }
    }

    unreachableByType(a: number, b: boolean): number {
        // @ts-ignore
        if (a == b) {
            /*
                1. a == 1, b == true
                2. a == 0, b == false

                No branch can enter this if statement
             */
            if (a && !b) {
                return 0
            } else {
                return 1
            }
        }

        return 2
    }

    transitiveCoercion(a: number, b: boolean, c: number): number {
        // @ts-ignore
        if (a == b) {
            if (c && (a == c)) {
                return 1
            } else {
                return 2
            }
        }

        return 3
    }

    transitiveCoercionNoTypes(a, b, c): number {
        // @ts-ignore
        if (a == b) {
            if (c != 0 && c != 1) {
                if (c && (a == c)) {
                    return 1
                } else {
                    return 2
                }
            } else {
                return 4
            }
        }

        return 3
    }
}
