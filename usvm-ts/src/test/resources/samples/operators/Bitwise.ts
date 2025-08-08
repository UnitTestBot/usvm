// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Bitwise {
    bitwiseNot(a: number): number {
        let res = ~a;

        if (a == 5) return res; // ~5 = -6
        if (a == -1) return res; // ~(-1) = 0
        if (a == 0) return res; // ~0 = -1

        return res;
    }

    bitwiseAnd(a: number, b: number): number {
        let res = a & b;

        if (a == 5 && b == 3) return res; // 5 & 3 = 1 (101 & 011 = 001)
        if (a == 15 && b == 7) return res; // 15 & 7 = 7 (1111 & 0111 = 0111)
        if (a == 0) return res; // anything & 0 = 0

        return res;
    }

    bitwiseOr(a: number, b: number): number {
        let res = a | b;

        if (a == 5 && b == 3) return res; // 5 | 3 = 7 (101 | 011 = 111)
        if (a == 0 && b == 0) return res; // 0 | 0 = 0
        if (a == 15 && b == 16) return res; // 15 | 16 = 31

        return res;
    }

    bitwiseXor(a: number, b: number): number {
        let res = a ^ b;

        if (a == 5 && b == 3) return res; // 5 ^ 3 = 6 (101 ^ 011 = 110)
        if (a == 7 && b == 7) return res; // x ^ x = 0
        if (a == 0) return res; // 0 ^ x = x

        return res;
    }

    leftShift(a: number, b: number): number {
        let res = a << b;

        if (a == 5 && b == 1) return res; // 5 << 1 = 10
        if (a == 1 && b == 3) return res; // 1 << 3 = 8
        if (b == 0) return res; // x << 0 = x

        return res; // fallback case
    }

    rightShift(a: number, b: number): number {
        let res = a >> b;

        if (a == 10 && b == 1) return res; // 10 >> 1 = 5
        if (a == -8 && b == 2) return res; // -8 >> 2 = -2 (sign extends)
        if (b == 0) return res; // x >> 0 = x

        return res; // fallback case
    }

    unsignedRightShift(a: number, b: number): number {
        let res = a >>> b;

        if (a == 10 && b == 1) return res; // 10 >>> 1 = 5
        if (a == -1 && b == 1) return res; // -1 >>> 1 = 2147483647 (max positive int32)
        if (b == 0) return res; // x >>> 0 = x

        return res; // fallback case
    }
}
