// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class ObjectUsage {
    objectAsParameter(x: {}): number {
        if (x === undefined) return -1
        return 42
    }
}
