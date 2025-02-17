class Arrays {
    createConstantArrayOfNumbers() {
        let x = [1, 2, 3]
        if (x[0] == 1) return 1
        return -1
    }

    createAndReturnConstantArrayOfNumbers() {
        return [1, 2, 3]
    }

    createAndAccessArrayOfBooleans() {
        let x = [true, false, true]
        if (x[0] == true) return 1
        return -1
    }

    createArrayOfBooleans() {
        let x = [true, false, true]
        return x
    }

    createMixedArray() {
        let x = [1.1, true, undefined]
        return x
    }

    createArrayOfUnknownValues(a, b, c) {
        let x = [a, b, c]

        if (x[0] == 1.1) {
            return x
        }

        if (x[1] == true) {
            return x
        }

        if (x[2] == undefined) {
            return x
        }

        return x
    }
}
