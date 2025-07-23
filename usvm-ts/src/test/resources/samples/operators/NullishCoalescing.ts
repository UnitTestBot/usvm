// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class NullishCoalescing {
    testNullishCoalescing(a: any): number {
        let res = a ?? "default";

        if (a === null && res === "default") return 1; // null is nullish
        if (a === undefined && res === "default") return 2; // undefined is nullish
        if (a === false && res === false) return 3; // false is NOT nullish
        if (a === 0 && res === 0) return 4; // 0 is NOT nullish
        if (a === "" && res === "") return 5; // empty string is NOT nullish

        return 100;
    }

    testNullishChaining(): number {
        let a = null;
        let b = undefined;
        let c = "value";

        let res = a ?? b ?? c;

        if (res === "value") return 1;
        return 0;
    }

    testNullishWithObjects(): number {
        let obj = null;
        let defaultObj = { x: 42 };

        let res = obj ?? defaultObj;

        if (res === defaultObj && res.x === 42) return 1;
        return 0;
    }
}
