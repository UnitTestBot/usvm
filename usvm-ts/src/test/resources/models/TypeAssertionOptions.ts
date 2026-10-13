class OriginalAssertionValue {
    original: number = 1;
}

class OtherAssertionValue {
    other: number = 2;
}

export class TypeAssertionOptions {
    static incompatibleReference(): number {
        const value = new OriginalAssertionValue();
        const asserted = value as OtherAssertionValue;
        return asserted ? 7 : 8;
    }

    static numericAssertion(): boolean {
        const value = 42;
        const asserted = value as unknown as string;
        return typeof asserted === "number" && asserted === 42;
    }

    static booleanAssertion(): boolean {
        const value = true;
        const asserted = <number><unknown>value;
        return typeof asserted === "boolean" && asserted === true;
    }
}
