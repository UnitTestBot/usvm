class StaticMethods {
    static noArguments(): number {
        return 42
    }

    static singleArgument(a: number): number {
        return a
    }

    static manyArguments(a: number, b: number, c: number, d: number): number {
        return d
    }
}
