class ForeignFields {
    name: number = 17;
    message: boolean = true;
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

    static overwrittenName(): string {
        const error = new Error("expected message");
        error.name = "CustomError";
        return error.name;
    }

    static overwrittenMessage(): string {
        const error = new Error("before");
        error.message = "after";
        return error.message;
    }

    static anyErrorMessage(): string {
        const error: any = new Error("aliased message");
        return error.message;
    }

    static anyForeignNameComparison(): number {
        const value: any = new ForeignFields();
        return value.name === 17 ? 1 : -1;
    }

    static anyForeignMessageComparison(): number {
        const value: any = new ForeignFields();
        return value.message === true ? 1 : -1;
    }

    static callbackMessage(): string {
        return new Error((() => "x") as any).message;
    }
}
