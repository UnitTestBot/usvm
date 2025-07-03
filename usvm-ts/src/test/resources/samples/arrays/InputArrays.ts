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
}
