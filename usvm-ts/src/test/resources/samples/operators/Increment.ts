// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Increment {
    preIncrement(): number {
        let value = 1;
        const result = ++value;
        return result * 10 + value;
    }

    postIncrement(): number {
        let value = 1;
        const result = value++;
        return result * 10 + value;
    }

    preDecrement(): number {
        let value = 1;
        const result = --value;
        return result * 10 + value;
    }

    postDecrement(): number {
        let value = 1;
        const result = value--;
        return result * 10 + value;
    }
}
