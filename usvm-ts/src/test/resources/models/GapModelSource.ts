declare class ArrayModelPrimitives {
    static elementString(value: boolean): string;
}

export class GapModelSource {
    private numbers(): number[] {
        // The cast deliberately changes the declared type; runtime storage and overrides must survive it.
        const values = "a,b,c".split(",") as unknown as number[];
        values[0] = 1;
        values[1] = 2;
        values[2] = 3;
        return values;
    }

    splitEdges(): boolean {
        const fields = ",a,,".split(",");
        return fields.length === 4 && fields[0] === "" && fields[1] === "a" && fields[3] === "" &&
            "abc".split().join() === "abc" && "abc".split(undefined).join() === "abc" &&
            "".split("").length === 0 && "".split(",").length === 1 &&
            "abc".split("", 0).length === 0 && "abc".split("", 2).join("-") === "a-b" &&
            "abc".split("", 4294967297).join("") === "a" &&
            "abcdefghijklmnop".split().length === 1 && "abcdefghijklmnop".split("", 0).length === 0 &&
            "😀".split("").length === 2 && "😀".split("").join("") === "😀";
    }

    composedMutations(): string {
        return "abc".split("").reverse().join("");
    }

    independentArrays(): string {
        const first = "ab".split("");
        const second = "cd".split("");
        return first.join("") + second.join("");
    }

    joinRuntimeKinds(): string {
        const values = "a,b,c,d".split(",") as any[];
        values[0] = null;
        values[1] = undefined;
        values[2] = true;
        values[3] = false;
        return values.join("-");
    }

    symbolicBooleans(first: boolean, second: boolean, both: boolean): string {
        const values = "a,b".split(",", both ? 2 : 1) as unknown as boolean[];
        values[0] = first;
        if (values.length > 1) values[1] = second;
        return values.join("-");
    }

    symbolicNullish(flag: boolean): string {
        const values = "a,b".split(",") as any[];
        values[0] = flag ? true : null;
        values[1] = flag ? undefined : false;
        return values.join("|");
    }

    reduceMutation(): number {
        const values = this.numbers();
        const multiplier = 2;
        const result = values.reduce((acc, current, index, array) => {
            if (index === 0) array[1] = 10;
            return acc + current * multiplier + index;
        }, 4);
        return result + values[1] * 4;
    }

    reduceOmitted(): number {
        return this.numbers().reduce((acc, value) => acc + value);
    }

    reduceUndefined(): number {
        return "a".split("").reduce((acc, value) => acc === undefined ? 8 : 9, undefined);
    }

    reduceAliases(): boolean {
        const values = this.numbers();
        const seed = { total: 0 };
        const result = values.reduce((acc, value, index, array) => {
            if (array !== values) throw 99;
            acc.total += value;
            return acc;
        }, seed);
        return result === seed && seed.total === 6;
    }

    reduceEmptyError(): boolean {
        try {
            "".split("").reduce((left, right) => left + right);
        } catch (error) {
            return error.name === "TypeError" && error.message === "Reduce of empty array with no initial value";
        }
        return false;
    }

    reduceInitialEmpty(): number {
        return "".split("").reduce((acc, value) => { throw value; }, 12);
    }

    reduceThrows(): number {
        try {
            "a".split("").reduce((acc, value) => { throw 7; }, 0);
        } catch (error) {
            return error;
        }
        return -1;
    }

    ignoredArguments(): boolean {
        let calls = 0;
        const joined = "a-b".split("-", 2, calls++).join("/", calls++);
        const reduced = this.numbers().reduce((left, right) => left + right, 0, calls++);
        return joined === "a/b" && reduced === 6 && calls === 3;
    }

    unrelatedPrivateHelper(): string {
        return ArrayModelPrimitives.elementString(true);
    }

    joinCallable(): string {
        return "a".split("").join((() => 1) as any);
    }

    splitCallable(): string[] {
        return "a".split((() => 1) as any);
    }

    joinNumbers(): string {
        return this.numbers().join(",");
    }

    splitLong(): string[] {
        return "abcdefghijklmnop".split("");
    }

    reduceShrink(): number {
        return this.numbers().reduce((acc, value, index, array) => {
            array.length = 0;
            return acc + value;
        }, 0);
    }

    reduceDeleteThrows(): number {
        return this.numbers().reduce((acc, value, index, array) => {
            delete array[0];
            throw array[0];
        }, 0);
    }
}
