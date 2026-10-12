export class ErrorModelBackingBoundary {
    publicFields(): string {
        const error = new Error("before");
        error.name = "CustomError";
        error.message = "after";
        return error.name + "|" + error.message;
    }

    allocatedError(): Error {
        return new Error("original");
    }
}
