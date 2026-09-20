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
}
