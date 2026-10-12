export class StringIndexing {
    literalUtf16(): boolean {
        const value = "A\u0000\u03a9\ud83d\ude00";
        return value[0] === "A" && value[1] === "\u0000" && value[2] === "\u03a9" &&
            value[3] === "\ud83d" && value[4] === "\ude00";
    }

    invalidIndexes(): boolean {
        const value = "ab";
        return value[-1] === undefined && value[2] === undefined && value[0.5] === undefined &&
            value[NaN] === undefined && value[Infinity] === undefined && value[-Infinity] === undefined &&
            value[4294967296] === undefined && value[-0] === "a";
    }

    stringKeys(): boolean {
        const value = "ab";
        return value["1"] === "b" && value["01"] === undefined && value["-0"] === undefined &&
            value["1.0"] === undefined && value["length"] === 2;
    }

    symbolicIndex(index: number): number {
        const character = "aba"[index];
        if (character === "a") {
            return 1;
        }
        if (character === "b") {
            return 2;
        }
        return 3;
    }

    symbolicString(value: string): number {
        if (value.length === 0) {
            return value[0] === undefined ? 0 : -1;
        }
        return value[0] === "a" ? 1 : 2;
    }

    symbolicCharAt(value: string, index: number): number {
        if (value.length > 2) {
            return 0;
        }
        return value.charAt(index) === "a" ? 1 : 2;
    }

    symbolicSlice(value: string): string {
        if (value.length !== 2) {
            return "";
        }
        return value.slice(0, 1);
    }

    symbolicSearch(value: string): number {
        if (value.length > 2) {
            return -2;
        }
        return value.indexOf("a");
    }

    symbolicPattern(value: string): number {
        if (value.length > 1) {
            return -2;
        }
        return "aba".indexOf(value);
    }

    symbolicCase(value: string): string {
        if (value.length !== 1) {
            return "";
        }

        const codeUnit = value.charCodeAt(0);
        if (codeUnit > 127) {
            return "";
        }

        return value.toUpperCase() + value.toLowerCase();
    }
}
