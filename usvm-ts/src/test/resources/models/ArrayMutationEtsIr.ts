// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class ArrayMutationEtsIr {
    pushSupportedArities(): number {
        const values: number[] = [];
        const zero = values.push();
        const one = values.push(4);
        const three = values.push(5, 6, 7);
        return zero * 10000 + one * 1000 + three * 100 + values[0] * 10 + values[3];
    }

    pushTooManyArguments(): number {
        const values: number[] = [];
        return values.push(1, 2, 3, 4);
    }

    fillNegativeFractionAndInfinity(values: number[]): number {
        values.fill(9, -2.8, Infinity);
        return values[0] * 1000 + values[1] * 100 + values[2] * 10 + values[3];
    }

    fillNaNAndFraction(values: number[]): number {
        values.fill(7, NaN, 1.9);
        return values[0] * 1000 + values[1] * 100 + values[2] * 10 + values[3];
    }

    reverseDense(values: number[]): number {
        const result = values.reverse();
        return result[0] * 1000 + result[1] * 100 + result[2] * 10 + result[3];
    }

    unshiftDense(values: number[]): number {
        const length = values.unshift(7, 8);
        return length * 10000 + values[0] * 1000 + values[1] * 100 + values[2] * 10 + values[3];
    }

    sliceDense(values: number[]): number {
        const result = values.slice(-3.8, Infinity);
        return result.length * 10000
            + result[0] * 1000
            + result[1] * 100
            + result[2] * 10
            + values.length;
    }

    concatDense(values: number[], other: number[]): number {
        const result = values.concat(other);
        return result.length * 10000
            + result[0] * 1000
            + result[1] * 100
            + result[2] * 10
            + result[3];
    }

    sparseReverseUsesResidual(): number[] {
        return new Array<number>(2).reverse();
    }
}
