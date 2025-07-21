// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class VoidOperator {
    testVoidOperator(a: number): number {
        let res = void a; // void always returns undefined

        if (res === undefined) return 1;
        return 0;
    }

    testVoidWithSideEffect(): number {
        let counter = 0;
        let res = void (counter = 5); // void operator evaluates the expression but ignores its value

        if (res === undefined && counter == 5) return 1;
        return 0;
    }
}
