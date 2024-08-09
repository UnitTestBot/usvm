class SimpleClass {
    noArguments(): number {
        return 42
    }

    singleArgument(a: number): number {
        return a
    }

    manyArguments(a: number, b: number, c: number): number {
        if (a == 1) return a
        if (b == 2) return b
        if (c == 3) return c

        return 100
    }

    thisArgument(): SimpleClass {
        return this
    }
}
