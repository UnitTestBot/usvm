declare class ExternalString {
    static value(): string;
    static literal(): "known";
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
}
