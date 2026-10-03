class StringArrayIsolation {
    independentArrayLength(value: string, array: number[]): number {
        if (value.length !== 1 || array.length !== 1) return 0;

        array.length = 0;
        return value.length === 0 ? 1 : 2;
    }
}
