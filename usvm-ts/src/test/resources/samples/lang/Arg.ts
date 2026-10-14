// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,BadExpressionStatementJS

class Arg {
    numberArg(a: number): number {
        if (Number.isNaN(a) == true) return a;
        if (a == 0) return a;
        if (a == 1) return a;
        return a;
    }

    shadowInfinity(Infinity: number): number {
        if (Infinity === 3) return Infinity;
        if (Infinity === -2) return Infinity;
        return Infinity;
    }

    shadowNaN(NaN: number): number {
        if (NaN === 4) return NaN;
        if (NaN === -5) return NaN;
        return NaN;
    }

    builtinInfinity(): number {
        return Infinity;
    }

    builtinNaN(): number {
        return NaN;
    }
}
