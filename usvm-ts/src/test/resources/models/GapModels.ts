// @ts-nocheck
export class GapModels {
    joinDefault(values: string[]): string { return values.join(undefined); }
    joinBooleans(values: boolean[]): string { return values.join("-"); }
    parseFloatCallable(): number { const f = () => 1; return Number.parseFloat(f as any); }
    joinCallable(): string { const f = () => 1; return "ab".split("").join(f as any); }
    splitCallable(): string[] { const f = () => 1; return "ab".split(f as any); }
    reduceThrows(values: number[]): number { return values.reduce((a, b) => { throw b; }, 0); }
    reduceInitialEmpty(values: number[]): number { return values.reduce((a, b) => { throw b; }, 12); }
    reduceDeleteThrows(values: number[]): number {
        return values.reduce((a, b, i, array) => { delete array[0]; throw array[0]; }, 0);
    }

    joinCallableBranch(index: number): string {
        if (index !== 0 && index !== 1) return "";
        const values = "a,b".split(",");
        values[index] = (() => 1) as any;
        return values.join(",");
    }
    twoSplits(): string {
        const first = "ab".split("");
        const second = "cd".split("");
        return first.join("") + second.join("");
    }

    reverseString(): string {
        return "abc".split("").reverse().join("");
    }

    splitEdges(): boolean {
        const fields = ",a,,".split(",");
        return fields.length === 4 && fields[0] === "" && fields[1] === "a" && fields[3] === "" &&
            "abc".split().join() === "abc" && "abc".split(undefined).join() === "abc" &&
            "".split("").length === 0 && "".split(",").length === 1 &&
            "abc".split("", 0).length === 0 && "abc".split("", 2).join("-") === "a-b" &&
            "abc".split("", 4294967297).join("") === "a" &&
            "😀".split("").length === 2 && "😀".split("").join("") === "😀";
    }

    reduceMutation(values: number[]): number {
        const multiplier = 2;
        const result = values.reduce((acc, current, index, array) => {
            if (index === 0) array[1] = 10;
            return acc + current * multiplier + index;
        }, 4);
        return result + values[1] * 4;
    }

    reduceNoInitial(values: number[]): number {
        return values.reduce((acc, value) => acc + value);
    }

    reduceUndefined(values: number[]): number {
        return values.reduce((acc, value) => acc === undefined ? 8 : value, undefined);
    }

    reduceShrink(values: number[]): number {
        return values.reduce((acc, value, index, array) => {
            array.length = 0;
            return acc + value;
        }, 0);
    }

    parseFloatEdges(): boolean {
        return Number.parseFloat("  -1.25rem") === -1.25 &&
            Number.parseFloat(".5e+2px") === 50 && Number.parseFloat("12e-foo") === 12 &&
            Number.parseFloat("0x10") === 0 && Number.parseFloat("1.2.3") === 1.2 &&
            Number.parseFloat("\uFEFFInfinity!") === Infinity &&
            1 / Number.parseFloat("-0.0") === -Infinity &&
            Number.isNaN(Number.parseFloat("nope")) && Number.isNaN(Number.parseFloat("+.")) &&
            Number.parseFloat("0.100000000000001") === 0.100000000000001;
    }

    parseFloatUnsupported(): number {
        return Number.parseFloat("12345678901234567");
    }
}
