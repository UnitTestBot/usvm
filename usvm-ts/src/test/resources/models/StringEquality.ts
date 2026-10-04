class StringEquality {
    equalsA(value: string): number {
        return value === "a" ? 1 : 2;
    }

    notEqualsA(value: string): number {
        return value !== "a" ? 1 : 2;
    }

    equalsEmpty(value: string): number {
        return value === "" ? 1 : 2;
    }

    equalsUnicode(value: string): number {
        return value === "\uD83D\uDE00" ? 1 : 2;
    }

    equalsOther(left: string, right: string): number {
        return left === right ? 1 : 2;
    }

    equalsAtLengthOne(left: string, right: string): number {
        if (left.length !== 1 || right.length !== 1) return 3;

        return left === right ? 1 : 2;
    }

    looselyEqualsOther(left: string, right: string): number {
        return left == right ? 1 : 2;
    }

    looselyNotEqualsOther(left: string, right: string): number {
        return left != right ? 1 : 2;
    }

    equalsAnyStrings(left: any, right: string): number {
        if (typeof left !== "string") return 3;
        return left === right ? 1 : 2;
    }

    equalsUnknownStrings(left: unknown, right: string): number {
        if (typeof left !== "string") return 3;
        return left === right ? 1 : 2;
    }

    equalsAnyDirect(left: any, right: string): number {
        return left === right ? 1 : 2;
    }

    looselyEqualsDynamicStrings(left: any, right: any): number {
        if (typeof left !== "string" || typeof right !== "string") return 3;
        return left == right ? 1 : 2;
    }

    refinedStringLength(left: any): number {
        if (typeof left !== "string") return 3;

        return left.length === 1 ? 1 : 2;
    }

    refinedStringAlias(left: unknown): number {
        const alias = left;
        if (typeof left !== "string") return 3;

        return (alias as string).length === 1 ? 1 : 2;
    }

    refinedStringUnused(left: any): number {
        if (typeof left !== "string") return 3;

        return 1;
    }

    refinedStringValueFieldUnused(box: { value: any }): number {
        const selected = box.value;
        if (typeof selected !== "string") return 3;

        return 1;
    }

    refinedStringValueFieldLength(box: { value: any }): number {
        const selected = box.value;
        if (typeof selected !== "string") return 3;

        return selected.length === 1 ? 1 : 2;
    }

    equalsRefinedStringValueField(box: { value: any }, other: string): number {
        const selected = box.value;
        if (typeof selected !== "string") return 3;

        return selected === other ? 1 : 2;
    }

    refinedStringNullish(left: any): number {
        if (left === null) return 4;
        if (left === undefined) return 5;
        if (typeof left !== "string") return 3;

        return left.length === 1 ? 1 : 2;
    }

    nullAndUndefined(): number {
        return null === undefined ? 1 : null == undefined ? 2 : 3;
    }
}
