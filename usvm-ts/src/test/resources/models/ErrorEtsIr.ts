class ForeignFields {
    name: number = 17;
    message: boolean = true;
}

export class ErrorEtsIr {
    static overwrittenPrimitiveKinds(): boolean {
        const error: any = new Error("original");
        error.message = 7;
        error.name = false;
        return error.message === 7 && typeof error.message === "number"
            && error.name === false && typeof error.name === "boolean";
    }

    static overwrittenReferenceKinds(): boolean {
        const error: any = new Error("original");
        error.message = null;
        error.name = undefined;
        return error.message === null && error.name === undefined;
    }

    static overwrittenObjectAlias(): boolean {
        const error: any = new Error("original");
        const payload = { marker: 7 };
        error.message = payload;
        return error.message === payload && error.message.marker === 7;
    }

    static symbolicMessage(message: string): boolean {
        const error = new Error(message);
        if (message.length === 0) return error.message === message && error.name === "Error";
        return error.message === message && typeof error.message === "string" && error.name === "Error";
    }

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
