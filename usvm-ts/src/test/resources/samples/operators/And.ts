// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class And {
    andOfBooleanAndBoolean(a: boolean, b: boolean): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    // (a)   (b)  (a && b)
    //  x     y      x
    //  x    NaN     x
    //  x    0.0     x
    // NaN    y     NaN
    // NaN   NaN    NaN
    // NaN   0.0    NaN
    // 0.0    y     0.0
    // 0.0   NaN    0.0
    // 0.0   0.0    0.0
    andOfNumberAndNumber(a: number, b: number): number {
        if (a && b) return 1                // (a!=0 && !a.isNaN) && (b!=0 && !b.isNaN)
        if (a && (b != b)) return 2         // (a!=0 && !a.isNaN) && (b.isNaN)
        if (a) return 3                     // (a!=0 && !a.isNaN) && (b==0)
        if ((a != a) && b) return 4         // (a.isNaN) && (b!=0 && !b.isNaN)
        if ((a != a) && (b != b)) return 5  // (a.isNaN) && (b.isNaN)
        if ((a != a)) return 6              // (a.isNaN) && (b==0)
        if (b) return 7                     // (a==0) && (b!=0 && !b.isNaN)
        if (b != b) return 8                // (a==0) && (b.isNaN)
        return 9                            // (a==0) && (b==0)
    }

    //   (a)   (b)  (a && b)
    //  true    x      x
    //  true   NaN    NaN
    //  true   0.0    0.0
    // false    x    false
    // false   NaN   false
    // false   0.0   false
    andOfBooleanAndNumber(a: boolean, b: number): number {
        if (a && b) return 1         //  a && (b!=0 && !b.isNaN)
        if (a && (b != b)) return 2  //  a && (b.isNaN)
        if (a) return 3              //  a && (b==0)
        if (b) return 4              // !a && (b!=0 && !b.isNaN)
        if (b != b) return 5         // !a && (b.isNaN)
        return 6                     // !a && (b==0)
    }

    // (a)    (b)   (a && b)
    //  x     true    true
    //  x    false   false
    // NaN    true    NaN
    // NaN   false    NaN
    // 0.0    true    0.0
    // 0.0   false    0.0
    andOfNumberAndBoolean(a: number, b: boolean): number {
        if (a && b) return 1         // (a!=0 && !a.isNaN) &&  b
        if (a) return 2              // (a!=0 && !a.isNaN) && !b
        if ((a != a) && b) return 3  //          (a.isNaN) &&  b
        if (a != a) return 4         //          (a.isNaN) && !b
        if (b) return 5              //             (a==0) &&  b
        return 6                     //             (a==0) && !b
    }

    andOfObjectAndObject(a: object, b: object): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    andOfUnknown(a, b): number {
        if (a && b) return 1
        if (a) return 2
        if (b) return 3
        return 4
    }

    truthyUnknown(a, b): number {
        if (a) return 1
        if (b) return 2
        return 99
    }
}
