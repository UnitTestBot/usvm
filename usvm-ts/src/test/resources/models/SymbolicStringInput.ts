class SymbolicStringFieldInput {
    value: string = "";
}

class EmptyLiteralStringFieldInput {
    value: "" = "";
}

class NonemptyLiteralStringFieldInput {
    value: "A\u0000\uD83D\uDE00" = "A\u0000\uD83D\uDE00";
}

class LongLiteralStringFieldInput {
    value: "abcde" = "abcde";
}

class SymbolicStringInput {
    identity(value: string): string {
        return value;
    }

    lengthOne(value: string): number {
        if (value.length === 0) return 0;

        return value.length === 1 ? 1 : 0;
    }

    literal(): string {
        return "A\u0000\u03a9\uD83D\uDE00";
    }

    literalLength(): number {
        return "A\u0000\u03a9\uD83D\uDE00".length;
    }

    independentArrayLength(value: string, array: number[]): number {
        if (value.length !== 1 || array.length !== 1) return 0;

        array.length = 0;
        return value.length === 0 ? 1 : 2;
    }

    lengthIs10001(value: string): number {
        return value.length === 10001 ? 1 : 0;
    }

    truthy(value: string): boolean {
        return !!value;
    }

    conditional(value: string): number {
        if (value) return 1;
        return 0;
    }

    emptyLiteralIsFalsy(): boolean {
        return !!"";
    }

    nullCodeUnitIsTruthy(): boolean {
        return !!"\u0000";
    }

    surrogatePairIsTruthy(): boolean {
        return !!"\uD83D\uDE00";
    }

    fieldConditional(input: SymbolicStringFieldInput): number {
        if (input.value) return 1;
        return 0;
    }

    fieldNegation(input: SymbolicStringFieldInput): number {
        if (!input.value) return 0;
        return 1;
    }

    fieldAnd(input: SymbolicStringFieldInput): number {
        if (input.value && true) return 1;
        return 0;
    }

    fieldOr(input: SymbolicStringFieldInput): number {
        if (input.value || false) return 1;
        return 0;
    }

    fieldEqualsEmpty(input: SymbolicStringFieldInput): number {
        return input.value === "" ? 1 : 0;
    }

    emptyLiteralField(input: EmptyLiteralStringFieldInput): number {
        if (input.value) return 1;
        return 0;
    }

    nonemptyLiteralField(input: NonemptyLiteralStringFieldInput): number {
        if (input.value && input.value === "A\u0000\uD83D\uDE00") return 1;
        return 0;
    }

    longLiteralField(input: LongLiteralStringFieldInput): number {
        return input.value ? 1 : 0;
    }

    writtenNonEmptyField(input: SymbolicStringFieldInput): number {
        input.value = "x";
        if (input.value) return 1;
        return 0;
    }

    writtenEmptyField(input: SymbolicStringFieldInput): number {
        input.value = "";
        if (input.value) return 1;
        return 0;
    }

    conditionallyWrittenField(input: SymbolicStringFieldInput, overwrite: boolean): number {
        if (overwrite) input.value = "";
        if (input.value) return 1;
        return 0;
    }

    anyStringLength(value: any): number {
        if (typeof value !== "string") return 0;

        return value.length === 1 ? 1 : 2;
    }
}
