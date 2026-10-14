export class RuntimeLimitationBoundaries {
    static regularExpressionLiteral(): boolean {
        return /value/.test("value");
    }

    static unsupportedConversionOrSupportedSibling(flag: boolean, value: { field: number }): string {
        if (flag) return "supported";
        return "prefix" + value;
    }

    static ordinaryNumericKeyField(): number {
        const value = { 0: 17 };
        return value[0];
    }

    static ordinaryLengthField(): number {
        const record = { length: 17 };
        return record.length;
    }

    static unsupportedNumericConversionOrSupportedSibling(useString: boolean): number {
        const values: unknown[] = [7];
        if (useString) values[0] = "42";

        return +values[0];
    }

    static modeledNumericConversions(): boolean {
        const values: unknown[] = [true, false, 7, null, undefined];

        return +values[0] === 1
            && +values[1] === 0
            && +values[2] === 7
            && +values[3] === 0
            && Number.isNaN(+values[4])
            && +"" === 0
            && +"7" === 7
            && +" " === 0
            && Number.isNaN(+"x");
    }

    static unsupportedNumericBinaryConversionOrSupportedSibling(useString: boolean): number {
        const values: unknown[] = [7];
        if (useString) values[0] = "42";

        return values[0] - 1;
    }
}
