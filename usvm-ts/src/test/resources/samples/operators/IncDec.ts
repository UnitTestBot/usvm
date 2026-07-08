// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class IncDec {
    preIncrement(a: number): number {
        let x = a;
        ++x;
        if (Number.isNaN(a)) return x; // NaN + 1 == NaN
        if (a == 0) return x; // ++0 == 1
        if (a > 0) return x;
        if (a < 0) return x;
        // unreachable
    }

    // NOTE: a `const old = x++` case is deliberately absent: ArkAnalyzer
    // (neo/2025-09-03) lowers it as `x := x + 1; old := x`, i.e. `old` receives
    // the *new* value — a frontend lowering bug that no engine semantics can fix.

    preDecrement(a: number): number {
        let x = a;
        --x;
        if (Number.isNaN(a)) return x; // NaN - 1 == NaN
        if (a == 0) return x; // --0 == -1
        if (a > 0) return x;
        if (a < 0) return x;
        // unreachable
    }

    decrementLoop(n: number): number {
        let count = 0;
        let x = n;
        while (x > 0 && count < 3) {
            x--;
            count++;
        }
        // Each loop depth is a distinct branch so that full coverage requires
        // the engine to actually unroll the decrementing loop.
        if (count == 0) return count;
        if (count == 3) return count;
        return count; // 1 or 2 iterations
    }
}
