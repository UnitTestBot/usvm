// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,UnnecessaryLocalVariableJS

class ArrayMethods {
    arrayPush(x: boolean) {
        const arr = [10, 20, 30];
        const newLength = arr.push(5);
        if (x) {
            return newLength;  // 4
        } else {
            return arr;  // [10, 20, 30, 5]
        }
    }

    arrayPushIntoNumber(x: number[]) {
        x.push(123);
        return x;
    }

    arrayPushIntoUnknown(x: any[]) {
        x.push(123);
        return x;
    }

    arrayPop(x: boolean) {
        const arr = [10, 20, 30];
        const lastElement = arr.pop();
        if (x) {
            return lastElement;  // 30
        } else {
            return arr;  // [10, 20]
        }
    }

    arrayFill(x: boolean) {
        let arr = [10, 20, 30];
        let filled = arr.fill(7);
        if (x) {
            return filled;  // [7, 7, 7]
        } else {
            return arr;  // [7, 7, 7] - modified in place
        }
    }

    arrayFillIgnoresExtraArgumentsAndClampsBounds() {
        const values = [1, 2, 3];
        values.fill(9, -1, 10, 100);
        return values[0] * 100 + values[1] * 10 + values[2];
    }

    arrayFillClearsBoxedOverride() {
        const values: number[] = [1, 2];
        const alias: any[] = values;
        alias[0] = undefined;
        alias.fill(7);
        return values[0] * 10 + values[1];
    }

    arrayFillWithoutValueOnEmptyArray() {
        const values: any[] = [];
        values.fill();
        return values.length;
    }

    arrayFillWithoutValueOnTypedArray() {
        const values: number[] = [1, 2];
        const alias: any[] = values;
        alias.fill();
        return alias[0] === undefined && alias[1] === undefined ? 1 : -1;
    }

    arrayFillPreservesBoxedOverridesOutsideRange() {
        const values: number[] = [1, 2, 3];
        const alias: any[] = values;
        alias[0] = undefined;
        alias[2] = undefined;
        alias.fill(7, 1, 2);
        return alias[0] === undefined && alias[1] === 7 && alias[2] === undefined ? 1 : -1;
    }

    arrayShift(x: boolean) {
        const arr = [10, 20, 30];
        const firstElement = arr.shift();
        if (x) {
            return firstElement;  // 10
        } else {
            return arr;  // [20, 30]
        }
    }

    arrayUnshift(x: boolean) {
        const arr = [10, 20, 30];
        let newLength = arr.unshift(5);
        if (x) {
            return newLength;  // 4
        } else {
            return arr;  // [5, 10, 20, 30]
        }
    }

    arrayJoin(x: boolean) {
        const arr = [1, 2, 3];
        if (x) {
            return arr.join();  // "1,2,3"
        } else {
            return arr.join("-");  // "1-2-3"
        }
    }

    arraySlice(x: boolean) {
        const arr = [10, 20, 30, 40, 50];
        if (x) {
            return arr.slice(1, 3);  // [20, 30]
        } else {
            return arr.slice(2);  // [30, 40, 50]
        }
    }

    arrayConcat(x: boolean) {
        const arr1 = [1, 2];
        const arr2 = [3, 4];
        const result = arr1.concat(arr2);
        if (x) {
            return result;  // [1, 2, 3, 4]
        } else {
            return arr1;  // [1, 2]
        }
    }

    arrayIndexOf(x: boolean) {
        const arr = [10, 20, 30, 20];
        if (x) {
            return arr.indexOf(20);  // 1 (first occurrence)
        } else {
            return arr.indexOf(99);  // -1 (not found)
        }
    }

    arrayIncludes(x: boolean) {
        const arr = [1, 2, 3];
        if (x) {
            return arr.includes(2);  // true
        } else {
            return arr.includes(5);  // false
        }
    }

    arrayReverse(x: boolean) {
        const arr = [1, 2, 3];
        const reversed = arr.reverse();
        if (x) {
            return reversed;  // [3, 2, 1]
        } else {
            return arr;  // [3, 2, 1] - modified in place
        }
    }

    arrayReverseIgnoresArguments() {
        const values = [1, 2, 3];
        values.reverse(99);
        return values[0] * 100 + values[1] * 10 + values[2];
    }
}
