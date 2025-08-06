// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Or {
    orOfBooleanAndBoolean(a: boolean, b: boolean): number {
        const res = a || b;
        if (a === true && b === true && res === true) return 1;
        if (a === true && b === false && res === true) return 2;
        if (a === false && b === true && res === true) return 3;
        if (a === false && b === false && res === false) return 4;
        return 0;
    }

    orOfNumberAndNumber(a: number, b: number): number {
        const res = a || b;
        if (!Number.isNaN(a) && a !== 0) { // a is truthy
            if (res === a) return 1;
        }
        if (Number.isNaN(a)) { // a is falsy (NaN)
            if (!Number.isNaN(b) && b !== 0 && res === b) return 2; // b is truthy
            if (Number.isNaN(b) && Number.isNaN(res)) return 3; // b is falsy (NaN)
            if (b === 0 && res === 0) return 4; // b is falsy (0)
        }
        if (a === 0) { // a is falsy (0)
            if (!Number.isNaN(b) && b !== 0 && res === b) return 5; // b is truthy
            if (Number.isNaN(b) && Number.isNaN(res)) return 6; // b is falsy (NaN)
            if (b === 0 && res === 0) return 7; // b is falsy (0)
        }
        return 0;
    }
}
