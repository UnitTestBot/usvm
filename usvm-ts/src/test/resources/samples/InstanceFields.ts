// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class InstanceFields {
    returnSingleField(x: { a: number }): number {
        return x.a
    }

    dispatchOverField(x: { a: number }): number {
        if (x.a == 1) return 1
        if (x.a == 2) return 2
        return 100
    }

    returnSumOfTwoFields(x: { a: number, b: number }): number {
        return x.a + x.b
    }

    assignField(x: { a: number }): number {
        x.a = 10
        return x.a
    }
}
