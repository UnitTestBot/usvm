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
        return [NaN].includes(NaN) && ![NaN].includes(0);
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
}
