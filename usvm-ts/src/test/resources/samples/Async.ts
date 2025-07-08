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

    createAndAwaitRejectingPromise(): number {
        const promise = new Promise((resolve, reject) => {
            reject(new Error("An error occurred"));
        });
        await promise;
    }
}
