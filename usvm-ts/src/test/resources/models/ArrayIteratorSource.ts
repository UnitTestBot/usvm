export class ArrayIteratorSource {
    static literalSpread(): number {
        const values = [7, 11];
        const copy = [3, ...values, 13];
        return copy.length === 4 && copy[0] === 3 && copy[1] === 7 && copy[2] === 11 && copy[3] === 13 ? 1 : 0;
    }

    static destructure(): number {
        const [first, , ...rest] = [7, 11, 13, 17];
        return first === 7 && rest.length === 2 && rest[0] === 13 && rest[1] === 17 ? 1 : 0;
    }

    static preserveKindsAndAliases(): number {
        const object = { value: 7 };
        const typed = [object];
        const mixed: any[] = typed;
        mixed[mixed.length] = null;
        mixed[mixed.length] = true;
        const copy = [...mixed];
        copy[0].value = 11;
        return object.value === 11 && copy[1] === null && copy[2] === true ? 1 : 0;
    }

    static orderedValue(order: number[], value: number): number {
        order[order.length] = value;
        return value;
    }

    static evaluationOrder(): number {
        const order: number[] = [];
        const values = [ArrayIteratorSource.orderedValue(order, 1), ...[ArrayIteratorSource.orderedValue(order, 2)],
            ArrayIteratorSource.orderedValue(order, 3)];
        return order.length === 3 && order[0] === 1 && order[1] === 2 && order[2] === 3 && values[1] === 2 ? 1 : 0;
    }

    static liveLength(): number {
        const values = [1];
        let total = 0;
        for (const value of values) {
            total += value;
            if (values.length === 1) values[values.length] = 2;
        }
        return total === 3 ? 1 : 0;
    }

    static shortenedLength(): number {
        const values = [1, 2, 3];
        let total = 0;
        for (const value of values) {
            total += value;
            values.length = 1;
        }
        return total === 1 ? 1 : 0;
    }

    static exhaustedDestructuring(): number {
        const values: number[] = [];
        const [first = ArrayIteratorSource.orderedValue(values, 7), second] = values;
        return first === 7 && second === undefined && values.length === 1 ? 1 : 0;
    }

    static symbolicInput(values: number[]): number {
        if (values.length !== 2 || values[0] !== 7 || values[1] !== 11) return 0;
        const copy = [...values];
        return copy.length === 2 && copy[0] === 7 && copy[1] === 11 ? 1 : 2;
    }

    static reversedMixedAlias(): number {
        const values = [7, 11, 13];
        const alias: any[] = values;
        alias[1] = true;
        alias[2] = null;
        values.reverse();
        const copy = [...alias];
        return copy.length === 3 && copy[0] === null && copy[1] === true && copy[2] === 7 ? 1 : 0;
    }

    static unshiftedMixedAlias(): number {
        const values = [7, 11];
        const alias: any[] = values;
        alias[1] = null;
        alias.unshift(true);
        const copy = [...alias];
        return copy.length === 3 && copy[0] === true && copy[1] === 7 && copy[2] === null ? 1 : 0;
    }

    static shiftedMixedAlias(): number {
        const values = [7, 11, 13];
        const alias: any[] = values;
        alias[1] = null;
        alias[2] = true;
        const first = alias.shift();
        const copy = [...alias];
        return first === 7 && copy.length === 2 && copy[0] === null && copy[1] === true ? 1 : 0;
    }

    static shiftedSparseArray(): number {
        const values = [1, , 3];
        values.shift();
        return [...values].length;
    }

    static reversedSparseArray(): number {
        const values = [1, , 3];
        values.reverse();
        return [...values].length;
    }

    static unshiftedSparseArray(): number {
        const values = [1, , 3];
        values.unshift(7);
        return [...values].length;
    }

    static stringNamedLookalike(): number {
        const values: any = { "Symbol.iterator"() { return [7]; } };
        return [...values].length;
    }

    static sparseArray(): number {
        const values = [1, , 3];
        return [...values].length;
    }

    static deletedSlot(): number {
        const values = [1, 2];
        delete values[0];
        return [...values].length;
    }

    static customSymbolOverride(): number {
        const values: any = [1, 2];
        values[Symbol.iterator] = () => ({ next: () => ({ done: true }) });
        return [...values].length;
    }
}
