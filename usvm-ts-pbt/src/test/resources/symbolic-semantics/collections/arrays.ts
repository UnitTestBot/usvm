import * as util from "./util";

export type EqualsFunction = (a: unknown, b: unknown) => boolean;

export function directAdd(a: number, b: number): number {
    return a + b;
}

export function callAddDecision(a: number, b: number): boolean {
    if (directAdd.call(undefined, a, b) > a) return true;
    return false;
}

export function callAddConstant(): number {
    return directAdd.call(undefined, 2, 3);
}

export function indexOf(array: unknown[], item: unknown, equalsFunction?: EqualsFunction): number {
    const equals = equalsFunction || util.defaultEquals;
    const length = array.length;
    for (let i = 0; i < length; i++) {
        if (equals(array[i], item)) return i;
    }
    return -1;
}

export function equals(array1: unknown[], array2: unknown[], equalsFunction?: EqualsFunction): boolean {
    const equalsFn = equalsFunction || util.defaultEquals;
    if (array1.length !== array2.length) return false;
    const length = array1.length;
    for (let i = 0; i < length; i++) {
        if (!equalsFn(array1[i], array2[i])) return false;
    }
    return true;
}

export function forEach(array: unknown[], callback: (value: unknown) => boolean | void): void {
    for (const value of array) {
        if (callback(value) === false) return;
    }
}

export function iteratorDoneValue(array: unknown[]): unknown {
    for (const value of array) {
        void value;
        return 1;
    }
    return undefined;
}
