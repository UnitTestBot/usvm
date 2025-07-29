// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,BadExpressionStatementJS

class Arg {
    numberArg(a: number): number {
        if (Number.isNaN(a) == true) return a;
        if (a == 0) return a;
        if (a == 1) return a;
        return a;
    }
}
