// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

let blockScopedResult = 0;
{
    let obj = {};
    obj.x = 7;
    blockScopedResult = "x" in obj ? 7 : 0;
}

class InOperator {
    readsBlockScopedResult(): number {
        return blockScopedResult;
    }

    hasPresentNumberProperty(value: number): number {
        const obj = { x: value };
        if ("x" in obj) return 1;
        return -1;
    }

    hasUndefinedProperty(value: number): number {
        const obj = { x: undefined, y: value };
        if ("x" in obj) return 1;
        return -1;
    }

    lacksProperty(value: number): number {
        const obj = { x: value };
        if ("missing" in obj) return -1;
        return 1;
    }

    lacksOptionalProperty(value: number): number {
        const obj: { x?: number } = {};
        return "x" in obj ? -1 : 1;
    }

    hasOwnConstructorMethod(value: number): number {
        const obj = { constructor() {} };
        return "constructor" in obj ? 1 : -1;
    }

    hasAddedProperty(value: number): number {
        const obj: { x?: number } = {};
        obj.x = value;
        return "x" in obj ? 1 : -1;
    }

    readsAddedProperty(value: number): number {
        const obj = {};
        obj.x = value;
        if (!("x" in obj)) return -1;
        return obj.x;
    }

    readsMissingOptionalAfterIn(): number {
        const obj: { x?: number } = {};
        if ("x" in obj) return 0;
        return obj.x === undefined ? 1 : -1;
    }

    lacksDeletedProperty(value: number): number {
        const obj = { x: value };
        delete obj.x;
        return "x" in obj ? -1 : 1;
    }

    hasRestoredProperty(value: number): number {
        const obj = { x: value };
        delete obj.x;
        obj.x = value;
        return "x" in obj ? 1 : -1;
    }

    conditionalDelete(shouldDelete: boolean): number {
        const obj = { x: 1 };
        if (shouldDelete) delete obj.x;
        return "x" in obj ? 1 : 0;
    }

    specialPrototypeInitializer(): boolean {
        const obj = { __proto__: null };
        return "__proto__" in obj;
    }

    inheritedThroughPrototypeInitializer(): boolean {
        const obj = { __proto__: { inherited: 1 } };
        return "inherited" in obj;
    }

    inheritedThroughAssignedPrototype(): boolean {
        const obj = {};
        obj.__proto__ = { inherited: 1 };
        return "inherited" in obj;
    }

    deletedToStringExposesPrototype(): boolean {
        const obj = { toString: 1 };
        delete obj.toString;
        return "toString" in obj;
    }

    inheritedConstructor(): boolean {
        const obj = {};
        return "constructor" in obj;
    }

    hasSymbolicKey(key: string): boolean {
        const obj = { x: 1 };
        return key in obj;
    }

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
