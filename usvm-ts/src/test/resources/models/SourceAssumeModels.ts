declare class TsModelPrimitives {
    static assume(condition: boolean): void;
}

export class SourceAssumeModels {
    static always(): number {
        TsModelPrimitives.assume(true);
        return 7;
    }

    static never(): number {
        TsModelPrimitives.assume(false);
        return 7;
    }

    static gated(condition: boolean): number {
        TsModelPrimitives.assume(condition);
        return condition ? 1 : 0;
    }

    static positive(value: number): number {
        TsModelPrimitives.assume(value > 0);
        return value;
    }
}
