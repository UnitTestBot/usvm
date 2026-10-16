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

    denseSearchAfterWrite(): boolean {
        const values = "a,b".split(",");
        values[0] = "x";
        return values.indexOf(undefined) === -1 && values.lastIndexOf(undefined) === -1;
    }

    denseSearchAfterUnrelatedWrite(): boolean {
        const values = "a,b".split(",");
        const other = "c,d".split(",");
        other[0] = "x";
        return values.indexOf(undefined) === -1 && values.lastIndexOf(undefined) === -1;
    }

    denseSearchAfterBoxedWrite(): boolean {
        const values = "a,b".split(",") as any[];
        values[0] = undefined;
        return values.indexOf(undefined) === 0 && values.lastIndexOf(undefined) === 0 && values.includes(undefined);
    }

    searchAfterSlotDeletion(): number {
        const values = "a,b".split(",");
        values.reverse();
        delete values[1];
        return values.indexOf(undefined);
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

    private joinNumberValue(value: number): string {
        const values = "a".split("") as unknown as number[];
        values[0] = value;
        return values.join();
    }

    numericJoinSafeIntegerEdges(): string {
        const values = "a,b,c,d,e,f,g,h".split(",") as unknown as number[];
        values[0] = -9007199254740991;
        values[1] = -1000000001;
        values[2] = -10;
        values[3] = -1;
        values[4] = -0;
        values[5] = 0;
        values[6] = 10;
        values[7] = 9007199254740991;
        return values.join("|");
    }

    numericJoinSpecials(): string {
        const values = "a,b,c,d,e".split(",") as unknown as number[];
        values[0] = NaN;
        values[1] = Infinity;
        values[2] = -Infinity;
        values[3] = -0;
        values[4] = 0;
        return values.join("|");
    }

    numericJoinConcreteDecimals(): string {
        const values = "a,b,c,d,e,f".split(",") as unknown as number[];
        values[0] = 0.1;
        values[1] = 1e-7;
        values[2] = 1e-6;
        values[3] = 1e20;
        values[4] = 1e21;
        values[5] = 1000000000000000100;
        return values.join("|");
    }

    numericJoinSymbolicInteger(value: number): string {
        if (!Number.isInteger(value) || value < -12 || value > 12) return "outside";
        return this.joinNumberValue(value);
    }

    numericJoinSymbolicSpecial(value: number): string {
        if (Number.isFinite(value)) return "finite";
        return this.joinNumberValue(value);
    }

    numericJoinRuntimeKind(flag: boolean): string {
        const values = "a".split("") as any[];
        values[0] = flag ? -42 : false;
        return values.join();
    }

    numericJoinSymbolicFraction(value: number): string {
        if (value > 0 && value < 1) return this.joinNumberValue(value);
        return "outside";
    }

    numericJoinSymbolicUnsafeInteger(value: number): string {
        if (value > 9007199254740991 && value <= 1e16) return this.joinNumberValue(value);
        return "outside";
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

    splitReverseKeepsDenseValues(): boolean {
        const values = "a,b".split(",");
        values.reverse();
        return values[0] === "b" && values[1] === "a";
    }

    reduceAfterDenseSlotDeletion(): number {
        const values = "a,b".split(",");
        values.reverse();
        delete values[1];
        return values.reduce((count, _value) => count + 1, 0);
    }

    sliceAfterDenseSlotDeletion(): string[] {
        const values = "a,b".split(",");
        values.reverse();
        delete values[1];
        return values.slice();
    }
}
