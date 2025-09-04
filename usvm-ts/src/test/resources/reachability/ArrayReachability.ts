// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Array access and manipulation reachability scenarios.
 * Tests reachability through array element comparisons and operations.
 */
class ArrayReachability {

    // Simple array element access reachability
    simpleArrayReachable(): number {
        const arr = [10, 20, 30];
        if (arr[0] === 10) {
            if (arr[1] > 15) {
                return 1; // Reachable: arr[0] is 10 and arr[1] is 20 > 15
            }
        }
        return 0;
    }

    // Array modification reachability
    arrayModificationReachable(index: number, value: number): number {
        const arr = [1, 2, 3];
        if (index >= 0 && index < 3) {
            arr[index] = value;
            if (arr[index] > 10) {
                return 1; // Reachable when value > 10
            }
        }
        return 0;
    }

    // Unreachable due to array bounds
    arrayBoundsUnreachable(): number {
        const arr = [5, 10, 15];
        if (arr[0] > 20) {
            return -1; // Unreachable: arr[0] is 5, cannot be > 20
        }
        return 0;
    }

    // Dynamic array creation and access
    dynamicArrayReachable(size: number): number {
        if (size > 0 && size < 10) {
            const arr = new Array(size);
            arr[0] = 42;
            if (arr[0] === 42) {
                if (arr.length === size) {
                    return 1; // Reachable when 0 < size < 10
                }
            }
        }
        return 0;
    }

    // Array element comparison reachability
    arrayComparisonReachable(): number {
        const arr1 = [10, 20];
        const arr2 = [15, 25];

        if (arr1[0] < arr2[0]) {
            if (arr1[1] < arr2[1]) {
                return 1; // Reachable: 10 < 15 and 20 < 25
            }
        }
        return 0;
    }

    // Array sum condition reachability
    arraySumReachable(): number {
        const arr = [5, 10, 15];
        const sum = arr[0] + arr[1] + arr[2];

        if (sum === 30) {
            if (arr[0] < arr[1]) {
                return 1; // Reachable: sum is 30 and 5 < 10
            }
        }
        return 0;
    }

    // Nested array access
    nestedArrayReachable(): number {
        const matrix = [[1, 2], [3, 4]];
        if (matrix[0][0] === 1) {
            if (matrix[1][1] === 4) {
                return 1; // Reachable: matrix[0][0] is 1 and matrix[1][1] is 4
            }
        }
        return 0;
    }

    // Array with object elements
    arrayObjectReachable(): number {
        const objects = [
            new ArrayElement(10),
            new ArrayElement(20),
        ];

        if (objects[0].value < objects[1].value) {
            if (objects[0].value + objects[1].value === 30) {
                return 1; // Reachable: 10 < 20 and 10 + 20 = 30
            }
        }
        return 0;
    }

    // Array length-based conditions
    arrayLengthReachable(arr: number[]): number {
        if (arr.length > 2) {
            if (arr[0] > 0) {
                if (arr[arr.length - 1] > arr[0]) {
                    return 1; // Reachable with appropriate array
                }
            }
        }
        return 0;
    }
}

// Helper class for array tests
class ArrayElement {
    value: number;

    constructor(val: number) {
        this.value = val;
    }
}
