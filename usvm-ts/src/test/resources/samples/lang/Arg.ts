// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,BadExpressionStatementJS

class Arg {
    numberArg(a: number): number {
        void Number.isNaN(a);
        void a == 0;
        void a == 1;
        return a; // 4 cases: NaN, 0, 1, other number
    }
}
