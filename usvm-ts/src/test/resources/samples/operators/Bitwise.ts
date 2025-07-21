// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Bitwise {
    bitwiseNot(a: number): number {
        let res = ~a;

        if (a == 5 && res == -6) return 1; // ~5 = -6
        if (a == -1 && res == 0) return 2; // ~(-1) = 0
        if (a == 0 && res == -1) return 3; // ~0 = -1

        return 0;
    }

    bitwiseAnd(a: number, b: number): number {
        let res = a & b;

        if (a == 5 && b == 3 && res == 1) return 1; // 5 & 3 = 1 (101 & 011 = 001)
        if (a == 15 && b == 7 && res == 7) return 2; // 15 & 7 = 7 (1111 & 0111 = 0111)
        if (a == 0 && res == 0) return 3; // anything & 0 = 0

        return 0;
    }

    bitwiseOr(a: number, b: number): number {
        let res = a | b;

        if (a == 5 && b == 3 && res == 7) return 1; // 5 | 3 = 7 (101 | 011 = 111)
        if (a == 0 && b == 0 && res == 0) return 2; // 0 | 0 = 0
        if (a == 15 && b == 16 && res == 31) return 3; // 15 | 16 = 31

        return 0;
    }

    bitwiseXor(a: number, b: number): number {
        let res = a ^ b;

        if (a == 5 && b == 3 && res == 6) return 1; // 5 ^ 3 = 6 (101 ^ 011 = 110)
        if (a == 7 && b == 7 && res == 0) return 2; // same numbers XOR = 0
        if (a == 0 && res == b) return 3; // 0 ^ x = x

        return 0;
    }

    leftShift(a: number, b: number): number {
        let res = a << b;

        if (a == 5 && b == 1 && res == 10) return 1; // 5 << 1 = 10
        if (a == 1 && b == 3 && res == 8) return 2; // 1 << 3 = 8
        if (b == 0 && res == a) return 3; // x << 0 = x

        return 0;
    }

    rightShift(a: number, b: number): number {
        let res = a >> b;

        if (a == 10 && b == 1 && res == 5) return 1; // 10 >> 1 = 5
        if (a == -8 && b == 2 && res == -2) return 2; // -8 >> 2 = -2 (sign extends)
        if (b == 0 && res == a) return 3; // x >> 0 = x

        return 0;
    }

    unsignedRightShift(a: number, b: number): number {
        let res = a >>> b;

        if (a == 10 && b == 1 && res == 5) return 1; // 10 >>> 1 = 5
        if (a == -1 && b == 1 && res == 2147483647) return 2; // -1 >>> 1 = max positive int
        if (b == 0 && res == a) return 3; // x >>> 0 = x

        return 0;
    }
}
