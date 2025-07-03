// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Division {
    twoNumbersDivision(a: number, b: number): number {
        let res = a / b;

        if (res == 4) {
            return res;
        }

        if (res == Infinity) {
            return res;
        }

        if (res == -Infinity) {
            return res;
        }

        if (res != res) {
            return res;
        }

        return res;
    }

    booleanDivision(a: boolean, b: boolean): number {
        let res = a / b;

        if (res == 0) {
            return res;
        }

        return res;
    }

    mixedDivision(a: number, b: boolean): number {
        let res = a / b;

        if (res == 4) {
            return res;
        }

        if (res == Infinity) {
            return res;
        }

        if (res == -Infinity) {
            return res;
        }

        if (res != res) {
            return res;
        }

        return res;
    }

    unknownDivision(a, b): number {
        let res = a / b;

        if (a === undefined || b === undefined) {
            return res;
        }

        if (res == 4) {
            return res;
        }

        if (res == Infinity) {
            return res;
        }

        if (res == -Infinity) {
            return res;
        }

        if (res != res) {
            return res;
        }

        return res;
    }
}
