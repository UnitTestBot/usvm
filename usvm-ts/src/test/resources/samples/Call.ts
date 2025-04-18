// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Call {
    simpleCall(): number {
        return this.f()
    }

    f(): number {
        return 42
    }

    fib(n: number): number {
        if (n != n) return 0
        if (n < 0) return -1
        if (n > 10) return -100
        if (n == 0) return 1
        if (n == 1) return 1
        return this.fib(n - 1) + this.fib(n - 2)
    }
}
