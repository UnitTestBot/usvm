// @ts-nocheck

class StoredFunctionCall {
    private callback: (value: number) => number;

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

    static inspect(input: InputRecord): number {
        if (input.score > 10) return 1;
        return 0;
    }
}

class InputRecord {
    score: number = 0;
}
