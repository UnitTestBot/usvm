// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,UnnecessaryLocalVariableJS

class InputArrays {
    inputArrayOfNumbers(x: number[]) {
        if (x[0] === 1) return 1;
        if (x[0] === undefined) return -1;
        return 2;
    }

    writeIntoInputArray(x: number[]) {
        if (x[0] != 1) {
            x[0] = 1;
        }

        return x[0];
    }

    idForArrayOfNumbers(x: number[]) {
        return x;
    }

    arrayOfBooleans(x: boolean[]) {
        if (x[0] === true) return 1;
        return -1;
    }

    arrayOfUnknownValues(x: any[]) {
        if (x[0] === 1.1) return x;
        if (x[1] === true) return x;
        if (x[2] === undefined) return x;
        return x;
    }

    writeIntoArrayOfUnknownValues(x: any[]) {
        x[1] = true;
        x[2] = undefined;
        x[0] = null;
        return x;
    }

    rewriteFakeValueInArray(x: any[]) {
        if (x[0] === 1) {
            x[0] = null;
        }
        return x[0];
    }

    readFakeObjectAndWriteFakeObject(x: any[], y) {
        if (x[0] === 1) {
            x[0] = y;
            if (x[0] === 2) {
                return x;
            } else {
                return x;
            }
        }

        return x;
    }

    conditionalLength(input: number) {
        const arr = createNumberArray(input);
        const res = processArray(arr);
        if (res.length > 0) return 1;
        if (input > 0) return -1; // unreachable, since 'input > 0' implies 'res.length > 0'
        return 0;
    }
}

function createNumberArray(size: number): number[] {
    const arr = [];
    for (let i = 0; i < size && i < 5; i++) {
        arr.push(i + 1);
    }
    return arr;
}

function processArray(arr: number[]): number[] {
    const result = [];
    for (let i = 0; i < arr.length; i++) {
        result.push(arr[i] * 2);
    }
    return result;
}
