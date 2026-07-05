// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class HybridSamples {
    // Random testing covers everything except the magic branch;
    // the targeted symbolic phase must reach it. The condition is arithmetic
    // (solution x = 49382) so that constant mining cannot guess it directly.
    magic(x: number): number {
        if (x * 2 === 98764) {
            return 42;
        }
        if (x > 0) {
            return 1;
        }
        return 0;
    }

    // PBT should find the crash and shrink the inputs.
    crashy(a: number[], i: number): number {
        if (i >= 0 && i < a.length) {
            return a[i];
        }
        throw new Error("index out of bounds");
    }

    // The hint-sensitive case: with an untyped parameter the engine builds a
    // fake object; the observed-type profile { NUMBER } should prune the search.
    // Deliberately arithmetic-only: usvm-ts string support is too weak for
    // `typeof x === "number"`-style guards.
    anyParam(x): number {
        if (x * 2 === 84) {
            return 1;
        }
        return 2;
    }

    // Three untyped parameters: without hints the engine explores up to 3^3
    // discriminator combinations of the fake objects; with { NUMBER } hints
    // for all three, exactly one.
    manyUntyped(a, b, c): number {
        if (a + b + c === 30000) {
            if (a * 2 === b && b * 3 === c) {
                return 1;
            }
            return 2;
        }
        return 3;
    }
}
