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

    static exercise(value: number): number {
        const subject = new StoredFunctionCall();
        return subject.call(value);
    }

    static regularFunction(): number {
        const subject = new StoredFunctionCall();
        subject.callback = function (value: number): number {
            return this.offset + value;
        };
        return subject.call(1);
    }

    static inheritedField(subject: DerivedStoredFunctionCall): number {
        subject.callback = () => 7;
        return subject.call();
    }

    static inspect(input: InputRecord): number {
        if (input.score > 10) return 1;
        return 0;
    }
}

class InputRecord {
    score: number = 0;
}

class BaseStoredFunctionCall {
    callback: () => number;

    constructor() {
        this.callback = () => 7;
    }
}

class DerivedStoredFunctionCall extends BaseStoredFunctionCall {
    call(): number {
        return this.callback();
    }
}
