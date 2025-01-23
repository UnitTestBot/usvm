class And {
    andOfBooleanAndBoolean(a: boolean, b: boolean): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    andOfNumberAndNumber(a: number, b: number): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    andOfBooleanAndNumber(a: boolean, b: number): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    andOfNumberAndBoolean(a: number, b: boolean): number {
        if (a && b) return 1
        if (a) return 2
        if ((a != a) && b) return 3.5
        if (b) return 3
        if ((a != a) && !b) return 4.5
        return 4
    }

    andOfObjectAndObject(a: object, b: object): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    andOfUnknown(a, b): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    truthyUnknown(a, b): number {
        if (a) return 1
        if (b) return 2
        return 99
    }
}
