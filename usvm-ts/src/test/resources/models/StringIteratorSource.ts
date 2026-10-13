export class StringIteratorSource {
    static mixedUtf16(): number {
        const values = [..."A\ud83d\ude00\ud800B\udc00\u0000"];
        return values.length === 6 && values[0] === "A" && values[1] === "\ud83d\ude00" &&
            values[2] === "\ud800" && values[3] === "B" && values[4] === "\udc00" && values[5] === "\u0000" ? 1 : 0;
    }

    static reversedSurrogates(): number {
        const values = [..."\udc00\ud800"];
        return values.length === 2 && values[0] === "\udc00" && values[1] === "\ud800" ? 1 : 0;
    }

    static destructuring(): number {
        const [first, second, ...rest] = "\ud83d\ude00A\ud800";
        return first === "\ud83d\ude00" && second === "A" && rest.length === 1 && rest[0] === "\ud800" ? 1 : 0;
    }

    static exhaustedDestructuring(): number {
        const [first, second = "end", third] = "\ud83d\ude00";
        return first === "\ud83d\ude00" && second === "end" && third === undefined ? 1 : 0;
    }

    static reboundSource(): number {
        let source = "A\ud83d\ude00";
        let count = 0;
        let units = 0;
        for (const value of source) {
            count++;
            units += value.length;
            source = "different";
        }
        return count === 2 && units === 3 ? 1 : 0;
    }

    static shadowedSymbol(): number {
        const Symbol = { iterator: "wrong" };
        const values = [..."\ud83d\ude00"];
        return Symbol.iterator === "wrong" && values.length === 1 && values[0].length === 2 ? 1 : 0;
    }

    static symbolicWidth(value: string): number {
        if (value.length > 2) return -1;
        const values = [...value];
        if (values.length === 0) return 0;
        if (values.length === 1) return values[0].length;
        return 3;
    }

    static symbolicFirst(value: string): string {
        if (value.length !== 2) return "";
        const [first] = value;
        return first;
    }
}
