// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Truthy {
    arrayTruthy() {
        if (![]) return -1 // unreachable
        if (![0]) return -1 // unreachable
        if (![1]) return -1 // unreachable
        return 0
    }
}
