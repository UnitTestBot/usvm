declare class ExternalAssume {
    static always(): number;
    static never(): number;
    static gated(condition: boolean): number;
    static positive(value: number): number;
}

declare class TsModelPrimitives {
    static assume(condition: boolean): void;
}

export class SourceAssumeCalls {
    always(): number { return ExternalAssume.always(); }
    never(): number { return ExternalAssume.never(); }
    gated(condition: boolean): number { return ExternalAssume.gated(condition); }
    positive(value: number): number { return ExternalAssume.positive(value); }

    foreign(): number {
        TsModelPrimitives.assume(false);
        return 9;
    }
}
