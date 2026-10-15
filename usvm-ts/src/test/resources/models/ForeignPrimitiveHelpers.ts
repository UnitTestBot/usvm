declare class StringModelPrimitives {
    static codeUnitAt(value: string, index: number): number;
    static isWhitespace(codeUnit: number): boolean;
    static copyNormalizedRange(value: string, from: number, to: number): string;
}

declare class ArrayModelPrimitives {
    static grow(values: number[], length: number): void;
}

export class ForeignPrimitiveHelpers {
    foreignString(): number {
        return StringModelPrimitives.codeUnitAt("ab", 0);
    }

    foreignWhitespace(): boolean {
        return StringModelPrimitives.isWhitespace(32);
    }

    foreignStringRange(): string {
        return StringModelPrimitives.copyNormalizedRange("ab", 0, 1);
    }

    foreignArray(): number {
        const values = [1];
        ArrayModelPrimitives.grow(values, 2);
        return values.length;
    }
}
