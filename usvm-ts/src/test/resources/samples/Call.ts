class Call {
    fib(n: number): number {
        if (n < 0) return -1
        if (n == 0) return 1
        if (n == 1) return 1
        return this.fib(n - 1) + this.fib(n - 2)
    }

    f(): number {
        return 42
    }

    simpleCall(): number {
        return this.f()
    }
}

