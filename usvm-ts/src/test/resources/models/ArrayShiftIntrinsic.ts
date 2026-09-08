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
}
