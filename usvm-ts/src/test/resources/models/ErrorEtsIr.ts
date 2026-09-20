class OtherWithNumericName {
    name: number = 17;
}

export class ErrorEtsIr {
    static name(): string {
        return new Error("expected message").name;
    }

    static message(): string {
        return new Error("expected message").message;
    }

    static throwError(): number {
        throw new Error("expected message");
    }

    static castUserObjectName(): number {
        const value = new OtherWithNumericName();
        return (value as unknown as Error).name as unknown as number;
    }
}
