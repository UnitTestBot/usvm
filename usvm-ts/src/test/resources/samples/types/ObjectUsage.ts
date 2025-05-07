// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class ObjectUsage {
    objectAsParameter(object: Object): number {
        if (object == undefined) {
            return -1
        }

        return 42
    }
}
