// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class ArrayElement {}

export class ArrayPopIntrinsic {
    emptyArray(): number | undefined {
        const values: number[] = [];
        return values.pop();
    }

    nonEmptyArray(): number {
        const values = [10, 20, 30];
        return values.pop()! + values.length;
    }

    aliasedElement(): number {
        const element = new ArrayElement();
        const values: ArrayElement[] = [element];
        if (values.pop() === element) {
            return 42;
        }
        return 0;
    }

    symbolicReferenceArray(values: ArrayElement[]): number {
        values.pop();
        return 45;
    }

    symbolicNumberArray(values: number[]): number {
        values.pop();
        return 46;
    }

    symbolicUnknownArray(values: any[]): number {
        values.pop();
        return 47;
    }

    allocatedReferenceArrayWithSymbolicWrite(index: number, value: any): number {
        if (index !== 1) {
            return 0;
        }

        const values: ArrayElement[] = [new ArrayElement(), new ArrayElement()];
        values[index] = value;
        const popped: any = values.pop();
        if (typeof popped === "number") {
            return 45;
        }

        return 0;
    }

    popWithArguments(): number {
        const values = [1];
        values.pop(0);
        return 48;
    }

    symbolicReferenceArrayPreservesFakeValue(values: ArrayElement[], value: any): number {
        if (values.length !== 1) {
            return 0;
        }

        values[0] = value;
        const popped: any = values.pop();
        if (typeof popped === "number") {
            return 44;
        }

        return 0;
    }
}
