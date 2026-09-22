class Error {
    name: string = "ShadowError";
    message: string;

    constructor(message: string) {
        this.message = message;
    }
}

export class UserDefinedErrorEtsIr {
    static name(): string {
        return new Error("shadow message").name;
    }
}
