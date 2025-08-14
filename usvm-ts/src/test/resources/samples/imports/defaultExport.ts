// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export default class DefaultExportedClass {
    private message: string;

    constructor(message: string = "default") {
        this.message = message;
    }

    getMessage(): string {
        return this.message;
    }

    setMessage(message: string): void {
        this.message = message;
    }
}

// Named export alongside default export
export const namedValue = 42;
