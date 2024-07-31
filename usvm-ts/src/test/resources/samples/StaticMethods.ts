class StaticMethods {
    static noArguments(): number {
        return 42
    }

    static singleArgument(a: number): number {
        if (a == 1) {
            return 100
        }
        return 0
    }

    static manyArguments(a: number, b: number, c: number, d: number): number {
        if (a == 1) return a
        if (b == 2) return b
        if (c == 3) return c
        if (d == 4) return d

        return 100
    }
}
