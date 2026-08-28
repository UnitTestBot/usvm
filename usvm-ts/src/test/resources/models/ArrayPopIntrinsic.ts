// noinspection JSUnusedGlobalSymbols

class ArrayElement {}

export class ArrayPopIntrinsic {
    emptyArray(): number | undefined {
        const values: number[] = [];
        return values.pop();
    }

    nonEmptyArray(): number {
        const values = [10, 20, 30];
        return values.pop()! + values.length;
    }

    aliasedElement(): number {
        const element = new ArrayElement();
        const values: ArrayElement[] = [element];
        if (values.pop() === element) {
            return 42;
        }
        return 0;
    }
}
