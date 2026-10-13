declare class ExternalString {
    static value(): string;
    static literal(): "known";
    static stringChoice(): "left" | "right";
    static finiteChoice(): "known" | 7 | true | null | undefined;
    static booleanChoice(): "known" | boolean;
    static numberLiteral(): 7;
    static booleanLiteral(): true;
    static nullLiteral(): null;
    static undefinedLiteral(): undefined;
}

export class FreshStringMock {
    static length(): number {
        return ExternalString.value().length;
    }

    static concat(): string {
        return ExternalString.value() + "!";
    }

    static concatWithinBound(): string {
        const value = ExternalString.value();
        if (value.length >= 8) return "!";
        return value + "!";
    }

    static branchOnLength(): number {
        const value = ExternalString.value();
        return value.length === 0 ? 0 : 1;
    }

    static literal(): string {
        return ExternalString.literal();
    }

    static stringUnion(): number {
        const value = ExternalString.stringChoice();
        if (value === "left") return 1;
        if (value === "right") return 2;
        return -1;
    }

    static finiteUnion(): number {
        const value = ExternalString.finiteChoice();
        if (typeof value === "string") return value === "known" ? 1 : -1;
        if (typeof value === "number") return value === 7 ? 2 : -1;
        if (typeof value === "boolean") return value === true ? 3 : -1;
        if (value === null) return 4;
        if (value === undefined) return 5;
        return -1;
    }

    static booleanUnion(): number {
        const value = ExternalString.booleanChoice();
        if (value === "known") return 1;
        if (value === true) return 2;
        if (value === false) return 3;
        return -1;
    }

    static exactPrimitiveLiterals(): number {
        if (ExternalString.numberLiteral() !== 7) return -1;
        if (ExternalString.booleanLiteral() !== true) return -1;
        if (ExternalString.nullLiteral() !== null) return -1;
        if (ExternalString.undefinedLiteral() !== undefined) return -1;
        return 1;
    }
}
