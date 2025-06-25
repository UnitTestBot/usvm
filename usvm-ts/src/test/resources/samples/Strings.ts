// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Strings {
    typeOfString(): string {
        let x = "hello"
        return typeof x // "string"
    }

    typeOfNumber(): string {
        let x = 42
        return typeof x // "number"
    }

    typeOfBoolean(): string {
        let x = true
        return typeof x // "boolean"
    }

    typeOfUndefined(): string {
        let x = undefined
        return typeof x // "undefined"
    }

    typeOfNull(): string {
        let x = null
        return typeof x // "object"
    }

    typeOfObject(): string {
        let x = {}
        return typeof x // "object"
    }

    typeOfArray(): string {
        let x = [5, 42]
        return typeof x // "object"
    }

    typeOfFunction(): string {
        let x = function() {}
        return typeof x // "function"
    }

    typeOfInputString(x: string): string {
        return typeof x // "string"
    }

    typeOfInputNumber(x: number): string {
        return typeof x // "number"
    }

    typeOfInputBoolean(x: boolean): string {
        return typeof x // "boolean"
    }

    typeOfInputUndefined(x: undefined): string {
        return typeof x // "undefined"
    }

    typeOfInputNull(x: null): string {
        return typeof x // "object"
    }

    typeOfInputObject(x: {}): string {
        return typeof x // "object"
    }

    typeOfInputArray(x: any[]): string {
        return typeof x // "object"
    }

    typeOfInputFunction(x: Function): string {
        return typeof x // "function"
    }
}
