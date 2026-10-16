export class RuntimeLimitationBoundaries {
    static regularExpressionLiteral(): boolean {
        return /value|other/.test("value");
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

    static unsupportedNumericConversionOrSupportedSibling(useObject: boolean): number {
        const values: unknown[] = [7];
        if (useObject) values[0] = { value: 42 };

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

    static unsupportedNumericBinaryConversionOrSupportedSibling(useObject: boolean): number {
        const values: unknown[] = [7];
        if (useObject) values[0] = { value: 42 };

        return values[0] - 1;
    }

    static dynamicStringAdditionOrSupportedNumericSibling(useString: boolean): number {
        const values: any[] = [7];
        if (useString) values[0] = "1";

        return values[0] + 2;
    }
}
