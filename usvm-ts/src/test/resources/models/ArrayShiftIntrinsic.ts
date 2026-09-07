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
        const element = new ArrayElement();
        const values: ArrayElement[] = [element];
        if (values.shift() === element) {
            return 42;
        }

        return 0;
    }

    symbolicNumberArray(values: number[]): number {
        values.shift();
        return 46;
    }

    symbolicUnknownArray(values: any[]): number {
        values.shift();
        return 47;
    }

    shiftWithArguments(): number {
        const values = [1];
        values.shift(0);
        return 48;
    }
}
