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

    nullAndUndefined(): number {
        return null === undefined ? 1 : null == undefined ? 2 : 3;
    }
}
