// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export class SequenceEtsIr {
    arrayIndexOfUsesStrictEqualityAndOffsets(): number {
        const values = [NaN, 2, 3, 2];
        return values.indexOf(NaN) * 1000
            + values.indexOf(2, 2) * 100
            + values.indexOf(3, -Infinity) * 10
            + values.indexOf(2, Infinity);
    }

    arrayIncludesUsesSameValueZero(): boolean {
        return [NaN].includes(NaN);
    }

    numericDefaultSearchFallsBack(): boolean {
        return [0].includes(0);
    }

    arrayOffsetsAreNormalized(): number {
        const values = [1, 2, 3, 2];
        return values.indexOf(2, 1.9) * 100
            + values.indexOf(2, -1.9) * 10
            + (values.includes(1, NaN) ? 1 : 0);
    }

    emptyArraysDoNotMatch(): boolean {
        return ![].includes(1) && [].indexOf(1) === -1;
    }

    arrayExplicitUndefinedOffset(): number {
        return [1].indexOf(1, undefined);
    }

    arrayLastIndexOfHandlesOffsets(): number {
        const values = [1, 2, 1];
        return values.lastIndexOf(1) * 100
            + values.lastIndexOf(1, -2) * 10
            + values.lastIndexOf(1, -Infinity);
    }

    arrayLastIndexOfExplicitUndefined(): number {
        return [1, 2, 1].lastIndexOf(1, undefined);
    }

    arrayLastIndexOfBeforeStart(): number {
        return [1].lastIndexOf(1, -2);
    }

    arraySearchReturnsPositiveZero(): number {
        const first = [1].indexOf(1, -0);
        const last = [1].lastIndexOf(1, -0.9);
        let result = 0;
        if (1 / first === Infinity) result += 1;
        if (1 / last === Infinity) result += 2;
        return result;
    }

    numericHoleDoesNotMatchZero(): number {
        const values = new Array<number>(1);
        return values.indexOf(0);
    }

    numericHoleDoesNotIncludeUndefined(): boolean {
        const values = new Array<number>(1);
        return values.includes(undefined);
    }

    numericHoleWithSymbolicSearch(value: number): boolean {
        const values = new Array<number>(1);
        return values.includes(value);
    }

    explicitUndefinedArrayIncludesUndefined(): boolean {
        const values = [undefined];
        return values.includes(undefined);
    }

    explicitUndefinedArrayIndexOfUndefined(): number {
        const values = [undefined];
        return values.indexOf(undefined);
    }

    stringCharAtHandlesBounds(): string {
        return "abc".charAt(1) + "abc".charAt(-1);
    }

    stringIndexOfHandlesOffsetsAndEmptySearch(): number {
        return "ababa".indexOf("ba", 2) * 100
            + "abc".indexOf("", Infinity) * 10
            + "abc".indexOf("a", -Infinity);
    }

    stringIncludesHandlesNaNPosition(): boolean {
        return "abc".includes("a", NaN) && !"abc".includes("a", Infinity);
    }

    stringExplicitUndefinedPositions(): number {
        return "abc".indexOf("a", undefined) + ("abc".charAt(undefined) === "a" ? 1 : 0);
    }

    stringSymbolicPosition(position: number): number {
        if (position === 0) return "ababa".indexOf("ba", position);
        if (position === 2) return "ababa".indexOf("ba", position);
        if (position === 4) return "ababa".indexOf("ba", position);
        return -100;
    }

    symbolicCharAt(position: number): string {
        return "abc".charAt(position);
    }

    symbolicCharAtMatches(position: number): boolean {
        if (position !== 0 && position !== 1) return true;
        return "ab".charAt(position) === (position === 0 ? "a" : "b");
    }

    stringSubstringBounds(): boolean {
        return "abc".substring(2, 1) === "b"
            && "abc".substring(NaN, Infinity) === "abc"
            && "abc".substring(-Infinity, -1) === ""
            && "abc".substring(1.9, undefined) === "bc"
            && "\ud83d\ude00".substring(1, 0).charCodeAt(0) === 0xd83d;
    }

    stringTrimWhitespace(): boolean {
        return "\u0009\u000a\u000b\u000c\u000d\u0020\u00a0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000\ufeff".trim() === ""
            && " \ud83d\ude00 x \u00a0".trim() === "\ud83d\ude00 x"
            && " \u0085\u180e\u200b ".trim() === "\u0085\u180e\u200b"
            && " \tvalue \n".trimStart() === "value \n"
            && " \tvalue \n".trimEnd() === " \tvalue";
    }

    stringCharCodeAtHandlesBounds(): number {
        const outside = "AZ".charCodeAt(2);
        return "AZ".charCodeAt(1) + (outside !== outside ? 1 : 0);
    }

    stringStartsAndEndsWithHandlePositions(): boolean {
        return "abc".startsWith("b", 1)
            && "abc".startsWith("", Infinity)
            && "abc".endsWith("b", 2)
            && "abc".endsWith("c", undefined);
    }

    stringLastIndexOfHandlesPositions(): number {
        return "ababa".lastIndexOf("ba") * 100
            + "ababa".lastIndexOf("ba", 2) * 10
            + "ababa".lastIndexOf("", Infinity);
    }

    stringSlicePreservesUtf16(): string {
        return "A😀Z".slice(1, 3);
    }

    stringModelsUseValueEquality(): boolean {
        const whole = "abc".slice(0);
        const independent = "abc".slice(0);
        const empty = "abc".slice(0, 0);

        return whole === "abc"
            && whole == "abc"
            && whole === independent
            && empty === ""
            && empty === "x".slice(1)
            && empty !== "a"
            && whole + "d" === "abcd";
    }

    stringEqualityPreservesReferenceIdentity(): boolean {
        const alias: number[] = [1];
        return alias === alias && alias !== [1];
    }

    stringValueEqualityThroughAny(): boolean {
        const whole: any = "abc".slice(0);
        const empty: any = "abc".slice(0, 0);
        return whole === "abc" && whole == "abc" && empty === "";
    }

    stringTruthinessUsesLength(): boolean {
        const empty = "abc".slice(0, 0);
        const nonEmpty = "abc".slice(0, 1);
        return !empty && !"" && !!nonEmpty;
    }

    nonStringTruthinessDoesNotReadStringStorage(): boolean {
        const object = { value: 1 };
        const array: number[] = [];
        return !undefined && !null && !!object && !!array;
    }

    mixedObjectEqualityDoesNotReadStringStorage(index: number): boolean {
        if (index !== 0 && index !== 1) return true;
        const dummy = "x";
        const values: any[] = [{}, {}];
        return values[index] !== "";
    }

    stringCapitalizeAscii(): string {
        const value = "hELLO";
        return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
    }

    stringCapitalizeEmpty(): string {
        const value = "";
        return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
    }

    stringCapitalizeNonAscii(): string {
        const value = "éCOLE";
        return value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
    }

    legacyArrayPush(): number {
        return [1].push(2);
    }

    legacyArrayFill(): number[] {
        return [1].fill(2);
    }

    legacyArrayUnshift(): number {
        return [1].unshift(2);
    }

    legacyArrayJoin(): string {
        return [1, 2].join("-");
    }

    legacyArraySlice(): number[] {
        return [1, 2].slice(1);
    }

    legacyArrayConcat(): number[] {
        return [1].concat([2]);
    }

    legacyArrayReverse(): number[] {
        return [1, 2].reverse();
    }

    legacyArrayToString(): string {
        return [1, 2].toString();
    }
}
