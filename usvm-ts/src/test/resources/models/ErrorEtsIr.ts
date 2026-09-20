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

    static callbackMessage(): string {
        return new Error((() => "x") as any).message;
    }
}
