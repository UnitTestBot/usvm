class And {
    andForTwoBoolValues(a: boolean, b: boolean): number {
        if (a && b) {
            return 1
        }

        if (a) {
            return 2
        }

        if (b) {
            return 3
        }

        return 4
    }



    andForUnknownTypes(a, b): number {
        if (a) {
           return 2
        }

        if (b) {
            return 3
        }

        return 4
    }
}