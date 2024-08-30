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
}
