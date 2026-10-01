// @ts-nocheck

class UnknownStringConcat {
    concatOpaqueString(): number {
        const upper = "a".toUpperCase();
        const result = upper + "!";

        return result.length;
    }

    concatOpaqueNumber(value: number): number {
        const result = `${value}`;

        return result.length;
    }
}
