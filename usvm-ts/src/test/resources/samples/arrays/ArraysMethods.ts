class ArraysMethods {
    shiftMethod(x: boolean) {
        const arr = [1, 2, 3];
        const firstElement = arr.shift();

        if (x) {
            return firstElement;
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

    pushMethod(x: boolean) {
        const arr = [1, 2, 3];
        arr.push(4);

        if (x) {
            return arr[arr.length - 1]; // Return the last element after push
        }

        return arr;
    }
}
