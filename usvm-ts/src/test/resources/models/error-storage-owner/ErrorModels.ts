export class ErrorValue {
    __usvmErrorName: string = "initial name";
    __usvmErrorMessage: string = "initial message";

    ordinarySelfFields(): string {
        this.__usvmErrorName = "user name";
        this.__usvmErrorMessage = "user message";
        return this.__usvmErrorName + "|" + this.__usvmErrorMessage;
    }
    ordinarySelfFieldsThroughAny(): boolean {
        this.__usvmErrorName = "user name";
        this.__usvmErrorMessage = "user message";
        const alias: any = this;
        return alias.__usvmErrorName === "user name" && alias.__usvmErrorMessage === "user message";
    }
}
