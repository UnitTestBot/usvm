declare class StringModelPrimitives {
    static codeUnitAt(value: string, index: number): number;
}

declare class ArrayModelPrimitives {
    static grow(values: number[], length: number): void;
}

export class ForeignPrimitiveHelpers {
    foreignString(): number {
        return StringModelPrimitives.codeUnitAt("ab", 0);
    }

    foreignArray(): number {
        const values = [1];
        ArrayModelPrimitives.grow(values, 2);
        return values.length;
    }
}
