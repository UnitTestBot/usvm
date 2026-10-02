// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class NumericBoundarySemantics {
    typedArrayCannotStoreMissingValue(): number {
        const source = [11];
        const destination = [22];
        destination[0] = source[2];
        return destination[0];
    }

    bitwiseNaN(): number {
        return (0 / 0) >> 1;
    }

    bitwiseInfinity(): number {
        return (1 / 0) >> 1;
    }

    bitwiseUint32Wrap(): number {
        return 4294967297 | 0;
    }

    bitwiseFraction(): number {
        return 1.9 | 0;
    }

    bitwiseNegativeFraction(): number {
        return -1.9 | 0;
    }

    bitwiseNotNaN(): number {
        return ~(0 / 0);
    }

    bitwiseAndInfinity(): number {
        return (1 / 0) & 7;
    }

    bitwiseXorInfinity(): number {
        return (1 / 0) ^ 7;
    }

    leftShiftMasksCount(): number {
        return 1 << 33;
    }

    rightShiftTruncates(): number {
        return -3.9 >> 1;
    }

    unsignedRightShift(): number {
        return -1.9 >>> 0;
    }

    bitwiseNegativeZero(): number {
        return -0 | 0;
    }

    missingNumericArrayProperties(): number {
        const values = [11, 22];
        let result = 0;
        if (values[0 / 0] === undefined) result += 1;
        if (values[1 / 0] === undefined) result += 2;
        if (values[0.9] === undefined) result += 4;
        if (values[-1] === undefined) result += 8;
        if (values[2] === undefined) result += 16;
        if (values[4294967297] === undefined) result += 32;
        return result;
    }

    negativeZeroArrayIndex(): number {
        const values = [11, 22];
        return values[-0];
    }

    fractionalArrayWrite(): number {
        const values = [11, 22];
        values[0.9] = 7;
        return values[1];
    }

    negativeArrayWrite(): number {
        const values = [11, 22];
        values[-1] = 7;
        return values[1];
    }

    outOfRangeArrayWrite(): number {
        const values = [11, 22];
        values[2] = 7;
        return values[1];
    }

    negativeZeroArrayWrite(): number {
        const values = [11, 22];
        values[-0] = 7;
        return values[0];
    }

    chainedArrayRead(): number {
        const values = [11, 22];
        const indices = [0, 1];
        return values[indices[1]];
    }

    chainedMissingArrayRead() {
        const values = [11, 22];
        const indices = [0, 1];
        return values[indices[2]];
    }

    chainedMissingArrayWrite(): number {
        const values = [11, 22];
        const indices = [0, 1];
        values[indices[2]] = 7;
        return values[0];
    }

    stringArrayIndexRead(): number {
        const values = [11, 22];
        return values["1"];
    }

    objectArrayIndexRead() {
        const values = [11, 22];
        return values[{}];
    }

    stringZeroIndex(): string {
        return "A😀B"[0];
    }

    stringNegativeZeroIndex(): string {
        return "A😀B"[-0];
    }

    stringHighSurrogateIndex(): string {
        return "A😀B"[1];
    }

    stringLowSurrogateIndex(): string {
        return "A😀B"[2];
    }

    missingNumericStringProperties(): number {
        const value = "abc";
        let result = 0;
        if (value[0.9] === undefined) result += 1;
        if (value[0 / 0] === undefined) result += 2;
        if (value[1 / 0] === undefined) result += 4;
        if (value[4294967297] === undefined) result += 8;
        if (value[3] === undefined) result += 16;
        return result;
    }

    newArrayInfinity(): number {
        return new Array<number>(1 / 0).length;
    }

    newArrayUint32Overflow(): number {
        return new Array<number>(4294967296).length;
    }

    newArrayFraction(): number {
        return new Array<number>(1.9).length;
    }

    newArrayBeyondModelCapacity(): number {
        return new Array<number>(17).length;
    }

    newArrayNegativeZero(): number {
        return new Array<number>(-0).length;
    }

    assignInvalidLength(): number {
        const values = [11, 22];
        values.length = 1 / 0;
        return values.length;
    }

    assignLengthBeyondModelCapacity(): number {
        const values = [11, 22];
        values.length = 17;
        return values.length;
    }

    assignNegativeZeroLength(): number {
        const values = [11, 22];
        values.length = -0;
        return values.length;
    }
}
