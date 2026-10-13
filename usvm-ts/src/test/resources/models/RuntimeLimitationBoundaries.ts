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
}
