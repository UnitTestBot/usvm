export class RuntimeLimitationBoundaries {
    static regularExpressionLiteral(): boolean {
        return /value/.test("value");
    }

    static unknownStorageOrSupportedSibling(flag: boolean, values: unknown): number {
        if (flag) return 7;
        return (values as any)[0];
    }

    static ordinaryLengthField(): number {
        const record = { length: 17 };
        return record.length;
    }
}
