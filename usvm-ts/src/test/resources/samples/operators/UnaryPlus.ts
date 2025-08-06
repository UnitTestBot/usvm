// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class UnaryPlus {
    testUnaryPlusAny(a: any): number {
        return +a;
    }

    testUnaryPlusBoolean(a: boolean): number {
        let res = +a;

        if (a === true && res === 1) return 1;
        if (a === false && res === 0) return 2;
        return 0;
    }

    testUnaryPlusNumber(a: number): number {
        let res = +a;

        if (res === a) return 1;
        if (Number.isNaN(a) && Number.isNaN(res)) return 2;
        return 0;
    }

    testUnaryPlusNull(): number {
        let res = +null;

        if (res === 0) return 1;
        return 0;
    }

    testUnaryPlusUndefined(): number {
        let res = +undefined;

        if (Number.isNaN(res)) return 1;
        return 0;
    }

    testUnaryPlusString42(): number {
        let res = +"42";

        if (res === 42) return 1;
        return 0;
    }

    testUnaryPlusString(s: string): number {
        let res = +s;

        if (s === "42" && res === 42) return 1;
        if (s === "0" && res === 0) return 2;
        if (s === "" && res === 0) return 3;
        if (s === "abc" && Number.isNaN(res)) return 4;
        if (s === "NaN" && Number.isNaN(res)) return 5;
        if (s === "Infinity" && res === Infinity) return 6;
        if (s === "-Infinity" && res === -Infinity) return 7;
        if (s === "1e+100" && res === 1e+100) return 8;
        if (s === "1e-100" && res === 1e-100) return 9;
        if (s === "1e+1000" && res === Infinity) return 10;
        if (s === "1e-1000" && res === 0) return 11;
        // Note: Number.MAX_VALUE is 1.7976931348623157e+308
        if (s === "1.7976931348623157e+308" && res === 1.7976931348623157e+308) return 12;
        if (s === "2e308" && res === Infinity) return 13;
        // Note: Number.MIN_VALUE is 5e-324
        if (s === "5e-324" && res === 5e-324) return 14;
        if (s === "1e-324" && res === 0) return 15;

        return 100;
    }

    testUnaryPlusObject(): number {
        let obj = {};
        let res = +obj;

        if (Number.isNaN(res)) return 1;
        return 0;
    }
}
