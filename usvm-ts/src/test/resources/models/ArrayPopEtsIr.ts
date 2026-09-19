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

    widenedReceiver(): number {
        const values = [10, 20, 30];
        const alias: any[] = values;
        return alias.pop() + values.length;
    }

    wrappedReceiver(): number {
        const values = [10, 20, 30];
        const alias: any = values;
        return alias.pop() + values.length;
    }

    sequentialPops(): number {
        const values = [10, 20, 30];
        const first = values.pop()!;
        const second = values.pop()!;
        return first + second + values.length;
    }

    shrinkThroughWidenedAlias(): number {
        const values = [10, 20, 30];
        const alias: any[] = values;
        alias.length = 1;
        return values.length * 10 + alias.length;
    }

    shrinkThroughWrappedAlias(): number {
        const values = [10, 20, 30];
        const alias: any = values;
        alias.length = 1;
        return values.length * 10 + alias.length;
    }

    negativeZeroLength(): number {
        const values = [10];
        values.length = -0;
        return values.length;
    }

    shrinkFromAny(length: any): number {
        if (length !== 1) return -1;
        const values = [10, 20];
        values.length = length;
        return values.length;
    }

    shrinkFromSymbolicAnyArray(lengths: any[]): number {
        if (lengths.length !== 1 || lengths[0] !== 1) return -1;
        const values = [10, 20];
        values.length = lengths.pop();
        return values.length;
    }

    shrinkFromNumber(length: number): number {
        if (length !== 1) return -1;
        const values = [10, 20];
        values.length = length;
        return values.length;
    }

    shrinkFromConcreteAnyArray(): number {
        const lengths: any[] = [1];
        const values = [10, 20];
        values.length = lengths.pop();
        return values.length;
    }

    shrinkFromUnconstrainedAny(length: any): number {
        const values = [10, 20];
        values.length = length;
        return values.length;
    }

    unsupportedLengthValue(): number {
        const values = [10];
        values.length = "0";
        return values.length;
    }

    popThenGrow(): number {
        const values = [10, 20];
        values.pop();
        values.length = 2;
        return 50;
    }

    growFreshArray(): number {
        const values: number[] = [];
        values.length = 1;
        return 51;
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
