// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class ArrayElement {}

export class ArrayPopEtsIr {
    unknownValue(value: unknown): unknown {
        return value;
    }

    emptyArray(): number | undefined {
        const values: number[] = [];
        return values.pop();
    }

    nonEmptyArray(): number {
        const values = [10, 20, 30];
        return values.pop()! + values.length;
    }

    referenceArray(): number {
        const values: ArrayElement[] = [new ArrayElement()];
        values.pop();
        return 42;
    }

    symbolicNumberArray(values: number[]): number {
        values.pop();
        return 46;
    }

    symbolicUnknownArray(values: any[]): number {
        values.pop();
        return 47;
    }

    unknownReceiver(value: any): number {
        value.pop();
        return 48;
    }

    popWithArguments(): number {
        const values = [1];
        values.pop(0);
        return 49;
    }
}
