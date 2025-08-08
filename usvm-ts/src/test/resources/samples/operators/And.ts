// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class And {
    andOfBooleanAndBoolean(a: boolean, b: boolean): number {
        const res = a && b;
        if (a === false && b === false && res === false) return 1;
        if (a === false && b === true && res === false) return 2;
        if (a === true && b === false && res === false) return 3;
        if (a === true && b === true && res === true) return 4;
        return 0;
    }

    // (a)   (b)  (a && b)
    //  x     y      x
    //  x    NaN     x
    //  x    0.0     x
    // NaN    y     NaN
    // NaN   NaN    NaN
    // NaN   0.0    NaN
    // 0.0    y     0.0
    // 0.0   NaN    0.0
    // 0.0   0.0    0.0
    andOfNumberAndNumber(a: number, b: number): number {
        const res = a && b;
        if (a) { // a is truthy, res is b
            if (b) { // b is truthy
                if (res === b) return 1; // res is also b
            } else if (Number.isNaN(b)) { // b is falsy (NaN)
                if (Number.isNaN(res)) return 2; // res is also NaN
            } else if (b === 0) { // b is falsy (0)
                if (res === 0) return 3; // res is also 0
            }
        } else if (Number.isNaN(a)) { // a is falsy (NaN), res is also NaN
            if (b) {
                if (Number.isNaN(res)) return 4;
            } else if (Number.isNaN(b)) {
                if (Number.isNaN(res)) return 5;
            } else if (b === 0) {
                if (Number.isNaN(res)) return 6;
            }
        } else if (a === 0) { // a is falsy (0), res is also 0
            if (b) {
                if (res === 0) return 7;
            } else if (Number.isNaN(b)) {
                if (res === 0) return 8;
            } else if (b === 0) {
                if (res === 0) return 9;
            }
        }
        return 0;
    }

    //   (a)   (b)  (a && b)
    //  true    x      x
    //  true   NaN    NaN
    //  true   0.0    0.0
    // false    x    false
    // false   NaN   false
    // false   0.0   false
    andOfBooleanAndNumber(a: boolean, b: number): number {
        const res = a && b;
        if (a) { // a is truthy (true), res is b
            if (b) { // b is truthy
                if (res === b) return 1; // res is also b
            } else if (Number.isNaN(b)) { // b is falsy (NaN)
                if (Number.isNaN(res)) return 2; // res is also NaN
            } else if (b === 0) { // b is falsy (0)
                if (res === 0) return 3; // res is also 0
            }
        } else { // a is falsy (false), res is also false
            if (b) {
                if (res === false) return 4;
            } else if (Number.isNaN(b)) {
                if (res === false) return 5;
            } else if (b === 0) {
                if (res === false) return 6;
            }
        }
        return 0;
    }

    // (a)    (b)   (a && b)
    //  x     true    true
    //  x    false   false
    // NaN    true    NaN
    // NaN   false    NaN
    // 0.0    true    0.0
    // 0.0   false    0.0
    andOfNumberAndBoolean(a: number, b: boolean): number {
        const res = a && b;
        if (a) { // a is truthy, res is b
            if (b) {
                if (res === true) return 1;
            } else {
                if (res === false) return 2;
            }
        } else if (Number.isNaN(a)) { // a is falsy (NaN), res is also NaN
            if (b) {
                if (Number.isNaN(res)) return 3;
            } else {
                if (Number.isNaN(res)) return 4;
            }
        } else if (a === 0) { // a is falsy (0), res is also 0
            if (b) {
                if (res === 0) return 5;
            } else {
                if (res === 0) return 6;
            }
        }
        return 0;
    }

    andOfObjectAndObject(a: object, b: object): number {
        const res = a && b;
        if (a) { // a is truthy, res is b
            if (b) {
                if (res === b) return 1;
            } else {
                if (res === b) return 2;
            }
        } else { // a is falsy, res is a
            if (b) {
                if (res === a) return 3;
            } else {
                if (res === a) return 4;
            }
        }
        return 0;
    }

    andOfUnknown(a: any, b: any): number {
        const res = a && b;
        if (a) { // a is truthy, res is b
            if (b) {
                if (res === b) return 1;
            } else if (Number.isNaN(b)) {
                if (Number.isNaN(res)) return 2;
            } else if (b === 0) {
                if (res === 0) return 3;
            } else if (b === false) {
                if (res === false) return 4;
            }
            // TODO: handle other falsy values
        } else if (Number.isNaN(a)) { // a is falsy (NaN), res is also NaN
            if (b) {
                if (Number.isNaN(res)) return 11;
            } else if (Number.isNaN(b)) {
                if (Number.isNaN(res)) return 12;
            } else if (b === 0) {
                if (Number.isNaN(res)) return 13;
            } else if (b === false) {
                if (Number.isNaN(res)) return 14;
            }
            // TODO: handle other falsy values
        } else if (a === 0) { // a is falsy (0), res is also 0
            if (b) {
                if (res === 0) return 21;
            } else if (Number.isNaN(b)) {
                if (res === 0) return 22;
            } else if (b === 0) {
                if (res === 0) return 23;
            } else if (b === false) {
                if (res === 0) return 24;
            }
            // TODO: handle other falsy values
        }
        return 0;
    }
}
