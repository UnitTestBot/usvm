export class ErrorValue {
    name: string;
    message: string;
}

export class ErrorModels {
    static construct(receiver: ErrorValue, message: string): ErrorValue {
        receiver.name = "Error";
        receiver.message = message;
        return receiver;
    }
}
