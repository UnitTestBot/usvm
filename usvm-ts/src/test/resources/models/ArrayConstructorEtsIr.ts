// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class ArrayConstructorEtsIr {
    callableArrayCreatesHolesAndAcceptsWrites(): number {
        const values = Array(3);
        const startsWithUndefined = values[0] === undefined;
        values[0] = 4;
        values[1] = 5;
        values[2] = 6;

        return (startsWithUndefined ? 1 : 0) * 10000
            + values.length * 1000
            + values[0] * 100
            + values[1] * 10
            + values[2];
    }

    callableArraySupportsNestedAssignment(): number {
        const result: number[][] = Array(2);
        result[0] = [4, 5];
        result[1] = [6];

        return result.length * 100 + result[0][1] * 10 + result[1][0];
    }

    negativeLength(): any[] {
        return Array(-1);
    }

    fractionalLength(): any[] {
        return Array(1.5);
    }

    nanLength(): any[] {
        return Array(NaN);
    }

    infiniteLength(): any[] {
        return Array(Infinity);
    }

    oversizedLength(): any[] {
        return Array(17);
    }

    fullFillInitializesSparseNumberArray(): number {
        const values = new Array(3).fill(7);
        return values.length * 1000 + values[0] * 100 + values[1] * 10 + values[2];
    }

    fullFillInitializesSparseBooleanArray(): number {
        const values = new Array<boolean>(3).fill(true);
        return values.length * 1000
            + (values[0] ? 1 : 0) * 100
            + (values[1] ? 1 : 0) * 10
            + (values[2] ? 1 : 0);
    }

    shadowedArrayIsNotModeled(): number {
        const Array = (arrayLength?: number): any[] => [arrayLength];
        return Array(2)[0];
    }
}
