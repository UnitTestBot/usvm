// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class ArrayElement {}

export class ArrayShiftIntrinsic {
    unknownValue(value: any): any {
        return value;
    }

    emptyArray(): number | undefined {
        const values: number[] = [];
        return values.shift();
    }

    nonEmptyArray(): number {
        const values = [10, 20, 30];
        return values.shift()! + values[0] + values.length;
    }

    aliasedElement(): number {
        const first = new ArrayElement();
        const second = new ArrayElement();
        const values: ArrayElement[] = [first, second];
        if (values.shift() === first && values[0] === second && values.length === 1) {
            return 42;
        }

        return 0;
    }

    symbolicNumberArray(values: number[]): number {
        values.shift();
        return 46;
    }

    symbolicUnknownArray(values: any[]): number {
        if (values.length < 2) {
            return 0;
        }

        const oldLength = values.length;
        const firstType = typeof values[0];
        const secondType = typeof values[1];
        const removedType = typeof values.shift();
        if (removedType === firstType && typeof values[0] === secondType && values.length === oldLength - 1) {
            return 47;
        }

        return 1;
    }

    mixedUnknownArray(): number {
        const element = new ArrayElement();
        const values: any[] = [10, true, element];
        const removed = values.shift();
        if (removed === 10 && values[0] === true && values[1] === element && values.length === 2) {
            return 49;
        }

        return 0;
    }

    emptyUnknownArray(): any {
        const values: any[] = [];
        return values.shift();
    }

    shiftWithArguments(): number {
        const values = [1];
        values.shift(0);
        return 48;
    }

    readBeforeShift(values: any[]): number {
        if (values.length !== 2) {
            return 0;
        }

        values[0] = 10;
        values.shift();
        return values[0] === 20 ? 1 : 2;
    }

    writeThroughSymbolicIndex(values: any[], index: number): any {
        if (values.length !== 2 || index !== 0) {
            return 0;
        }

        values[0] = 10;
        values[index] = 20;
        return values[0];
    }

    shiftedWrittenUnknownArray(values: any[]): any[] {
        if (values.length !== 2) {
            return [];
        }

        values[0] = 10;
        values[1] = 20;
        values.shift();
        return values;
    }

    numberArrayThroughAnyAlias(): number {
        const original: number[] = [10, 20];
        const values: any[] = original;
        return values.shift() === 10 && original[0] === 20 && values.length === 1 ? 1 : 0;
    }

    booleanArrayThroughUnknownAlias(): number {
        const original: boolean[] = [true, false];
        const values: unknown[] = original;
        return values.shift() === true && original[0] === false && values.length === 1 ? 1 : 0;
    }

    conditionalFakeElement(index: number): number {
        if (index !== 0 && index !== 1) return 0;
        const values: any[] = [10, true];
        values[index] = 20;
        const removed = values.shift();
        if (index === 0) return removed === 20 && values[0] === true ? 1 : -1;
        return removed === 10 && values[0] === 20 ? 1 : -1;
    }

    conditionalArray(flag: boolean): number {
        const first: any[] = [10, true];
        const second: any[] = [false, 20];
        const values: any[] = flag ? first : second;
        const removed = values.shift();
        if (flag) return removed === 10 && values[0] === true && first.length === 1 && second.length === 2 ? 1 : 0;
        return removed === false && values[0] === 20 && second.length === 1 && first.length === 2 ? 1 : 0;
    }

    conditionalEmptyArray(flag: boolean): number {
        const first: any[] = [10, true];
        const second: any[] = [];
        const values: any[] = flag ? first : second;
        const removed = values.shift();
        if (flag) return removed === 10 && values[0] === true && first.length === 1 && second.length === 0 ? 1 : 0;
        return removed === undefined && first.length === 2 && second.length === 0 ? 1 : 0;
    }

    pushAfterShift(): number {
        const element = new ArrayElement();
        const values: any[] = [10, true, element];
        const first = values.shift();
        values.push(null);
        return first === 10 && values.shift() === true && values.shift() === element &&
            values.shift() === null && values.shift() === undefined && values.length === 0 ? 1 : 0;
    }
}
