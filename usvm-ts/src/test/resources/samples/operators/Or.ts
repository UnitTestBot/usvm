// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Or {
    // (a)   (b)   (a || b) Case
    // false false   false   4
    // false true     true   3
    // true  false    true   2
    // true  true     true   1
    orOfBooleanAndBoolean(a: boolean, b: boolean): number {
        if (a || b) {
            if (a && b) return 1 // (a) && (b)
            if (a) return 2      // (a) && (!b)
            if (b) return 3      // (!a) && (b)
            return 0
        }
        if (a) return 0
        if (b) return 0
        return 4                 // (!a) && (!b)
    }

    // (a)   (b)  (a || b) Case
    //  x     y       x     1
    //  x    NaN      x     2
    //  x    0.0      x     3
    // NaN    y       y     4
    // NaN   NaN    NaN     5
    // NaN   0.0    0.0     6
    // 0.0    y       y     7
    // 0.0   NaN    NaN     8
    // 0.0   0.0    0.0     9
    orOfNumberAndNumber(a: number, b: number): number {
        if (a || b) {
            if (a) {
                if (b) return 1       // (a) && (b)
                if (b != b) return 2  // (a) && (b.isNaN)
                if (b == 0) return 3  // (a) && (b==0)
                return 0
            }
            if (a != a) {
                if (b) return 4       // (a.isNaN) && (b)
                return 0
            }
            if (a == 0) {
                if (b) return 7       // (a==0) && (b)
                return 0
            }
            return 0
        }
        if (a != a) {
            if (b != b) return 5      // (a.isNaN) && (b.isNaN)
            if (b == 0) return 6      // (a.isNaN) && (b==0)
            return 0
        }
        if (a == 0) {
            if (b != b) return 8      // (a==0) && (b.isNaN)
            if (b == 0) return 9      // (a==0) && (b==0)
            return 0
        }
        return 0 // unreachable
    }
}
