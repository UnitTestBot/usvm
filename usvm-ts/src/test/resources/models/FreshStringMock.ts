declare class ExternalString {
    static value(): string;
}

export class FreshStringMock {
    static length(): number {
        return ExternalString.value().length;
    }

    static concat(): string {
        return ExternalString.value() + "!";
    }
}
