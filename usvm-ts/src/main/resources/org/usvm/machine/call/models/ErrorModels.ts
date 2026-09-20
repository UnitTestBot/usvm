export class ErrorValue {
    // Keep model storage out of unresolved user-property lookup.
    __usvmErrorName: string;
    __usvmErrorMessage: string;
}

export class ErrorModels {
    static construct(receiver: ErrorValue, message: string): ErrorValue {
        receiver.__usvmErrorName = "Error";
        receiver.__usvmErrorMessage = message;
        return receiver;
    }
}
