// @ts-nocheck

class StoredFunctionCall {
    private callback: (value: number) => number;
    private offset: number = 4;

    constructor() {
        this.callback = (value: number) => value + 1;
    }

    call(value: number): number {
        return this.callback(value);
    }

    static constructorCallback(): number {
        const subject = new StoredFunctionCall();
        return subject.call(1);
    }

    static regularFunction(): number {
        const subject = new StoredFunctionCall();
        subject.callback = function (value: number): number {
            return this.offset + value;
        };
        return subject.call(1);
    }
}
