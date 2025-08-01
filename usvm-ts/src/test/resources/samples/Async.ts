// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Async {
    awaitResolvingPromise(): number {
        const promise = new Promise((resolve) => {
            resolve(42);
        });
        return await promise; // 42
    }

    awaitRejectingPromise() {
        const promise = new Promise((resolve, reject) => {
            reject(new Error("An error occurred"));
        });
        await promise; // exception
    }

    awaitResolvedPromise(): number {
        const promise = Promise.resolve(42);
        return await promise; // 42
    }

    awaitRejectedPromise() {
        const promise = Promise.reject(new Error("An error occurred"));
        await promise; // exception
    }
}
