// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Numeric {
    numberToNumber(x: number): number {
        if (x != x) return Number(x) // NaN
        if (x == 0) return Number(x) // 0
        if (x > 0) return Number(x) // x (>0)
        if (x < 0) return Number(x) // x (<0)
        // unreachable
    }

    boolToNumber(x: boolean): number {
        if (x) return Number(x) // 1
        if (!x) return Number(x) // 0
        // unreachable
    }

    undefinedToNumber(): number {
        let x = undefined
        return Number(x) // NaN
    }

    nullToNumber(): number {
        let x = null
        return Number(x) // 0
    }

    emptyStringToNumber(): number {
        let x = ""
        return Number(x) // 0
    }

    numberStringToNumber(): number {
        let x = "42"
        return Number(x) // 42
    }

    stringToNumber(): number {
        let x = "hello"
        return Number(x) // NaN
    }

    emptyArrayToNumber(): number {
        let x = []
        return Number(x) // 0
    }

    singleNumberArrayToNumber(): number {
        let x = [42]
        return Number(x) // 42
    }

    singleUndefinedArrayToNumber(): number {
        let x = [undefined]
        return Number(x) // 0
    }

    singleObjectArrayToNumber(): number {
        let x = [{}]
        return Number(x) // NaN
    }

    singleCustomFortyTwoObjectArrayToNumber(): number {
        let x = [new FortyTwo()]
        return Number(x) // 42
    }

    multipleElementArrayToNumber(): number {
        let x = [5, 100]
        return Number(x) // NaN
    }

    emptyObjectToNumber(): number {
        let x = {}
        return Number(x) // NaN
    }

    objectToNumber(): number {
        let x = {a: 42}
        return Number(x) // NaN
    }

    customFortyTwoObjectToNumber(): number {
        let x = new FortyTwo()
        return Number(x) // 42
    }
}

class FortyTwo {
    toString() {
        return "42"
    }
}
