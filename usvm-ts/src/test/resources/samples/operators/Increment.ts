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

    postIncrementBoolean(): number {
        let value: any = true;
        const result = value++;
        return result * 10 + value;
    }

    postDecrementNull(): number {
        let value: any = null;
        const result = value--;
        return result * 10 + value;
    }

    postIncrementField(): number {
        const box = { value: 1 };
        const result = box.value++;
        return result * 10 + box.value;
    }
}
