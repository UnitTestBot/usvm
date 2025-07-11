class ArraysMethods {
    shiftMethod(x: boolean) {
        const arr = [1, 2, 3];
        const firstElement = arr.shift();

        if (x) {
            return firstElement;
        }

        return arr;
    }

    unshiftMethod(x: boolean) {
        const arr = [1, 2, 3];
        arr.unshift(0);
        if (x) {
            return arr[0];
        }
        return arr;
    }

    popMethod(x: boolean) {
        const arr = [1, 2, 3];
        const lastElement = arr.pop();

        if (x) {
            return lastElement;
        }

        return arr;
    }

    pushMethod() {
        const arr = [1, 2, 3];
        arr.push(4);
        return arr;
    }

    fillMethod(x: boolean) {
        const arr = [1, 2, 3];
        arr.fill(7);
        if (x) {
            return arr[0];
        }
        return arr;
    }
}
