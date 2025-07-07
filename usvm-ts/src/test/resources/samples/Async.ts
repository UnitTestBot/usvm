// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Async {
    createAndAwaitPromise(): number {
        const promise = new Promise((resolve) => {
            resolve(42);
        });
        const resolved = await promise;
        if (resolved == 42) {
            return 1;
        } else {
            return -1; // unreachable
        }
    }
}
