// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class InOperator {
    testInOperatorObject(): number {
        let obj = { x: 42, y: undefined };

        // if ("x" in obj && "y" in obj && !("z" in obj)) return 1;

        if (!("x" in obj)) return -1; // "x" property exists
        if (!("y" in obj)) return -2; // "y" property exists, even if it's undefined
        if ("z" in obj) return -3; // "z" property does not exist
        if (!("toString" in obj)) return -4; // "toString" method exists on the object

        return 1;
    }

    testInOperatorObjectAfterDelete(): number {
        let obj = { x: 42, y: undefined };
        delete obj.x;

        if ("x" in obj) return -1; // "x" property does not exist after deletion
        if (!("y" in obj)) return -2; // "y" property still exists, even if it's undefined
        if ("z" in obj) return -3; // "z" property does not exist
        if (!("toString" in obj)) return -4; // "toString" method exists on the object

        return 1;
    }

    testInOperatorArray(): number {
        let arr = [1, 2, 3];

        if (!(0 in arr)) return -1; // index 0 exists
        if (!(1 in arr)) return -2; // index 1 exists
        if (!(2 in arr)) return -3; // index 2 exists
        if (3 in arr) return -4; // index 3 doesn't exist
        if (!("length" in arr)) return -5; // length property exists

        return 1;
    }

    testInOperatorArrayAfterDelete(): number {
        let arr = [1, 2, 3];
        delete arr[1]; // delete index 1

        if (!(0 in arr)) return -1; // index 0 exists
        if (1 in arr) return -2; // index 1 does not exist after deletion
        if (!(2 in arr)) return -3; // index 2 exists
        if (3 in arr) return -4; // index 3 doesn't exist
        if (!("length" in arr)) return -5; // length property exists

        return 1;
    }

    testInOperatorString(): number {
        let str = "hello";

        if (!(0 in str)) return -1; // index 0 exists
        if (!(1 in str)) return -2; // index 1 exists
        if (!(2 in str)) return -3; // index 2 exists
        if (!(3 in str)) return -4; // index 3 exists
        if (!(4 in str)) return -5; // index 4 exists
        if (5 in str) return -6; // index 5 doesn't exist
        if (!("length" in str)) return -7; // length property exists

        return 1;
    }
}
