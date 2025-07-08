// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class ArraysMethods {
    pushMethod(x: boolean) {
        const arr = [10, 20, 30];
        const newLength = arr.push(5);
        if (x) return newLength; // 4
        return arr; // [10, 20, 30, 5]
    }

    popMethod(x: boolean) {
        const arr = [10, 20, 30];
        const lastElement = arr.pop();
        if (x) return lastElement; // 30
        return arr; // [10, 20]
    }

    fillMethod() {
        let arr = [10, 20, 30];
        arr.fill(7);
        return arr; // [7, 7, 7]
    }
    shiftMethod(x: boolean) {
        const arr = [10, 20, 30];
        const firstElement = arr.shift();
        if (x) return firstElement;
        return arr;
    }

    unshiftMethod(x: boolean) {
        const arr = [2, 9, 7];
        let newLength = arr.unshift(3);
        if (x) return newLength; // 4
        return arr; // [3, 2, 9, 7]
    }
}
