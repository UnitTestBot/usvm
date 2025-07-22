// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Async {
    awaitResolvingPromise(): number {
        const promise = new Promise((resolve) => {
            resolve(42);
        });
        const res = await promise; // 42

        if (res === 42) return 1;
        return 0;
    }

    awaitRejectingPromise() {
        const promise = new Promise((resolve, reject) => {
            reject(new Error("An error occurred"));
        });
        await promise; // exception
    }

    awaitResolvedPromise(): number {
        const promise = Promise.resolve(42);
        const res = await promise; // 42

        if (res === 42) return 1;
        return 0;
    }

    awaitRejectedPromise() {
        const promise = Promise.reject(new Error("An error occurred"));
        await promise; // exception
    }
}
