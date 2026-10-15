// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Async {
    awaitPlainObject(): number {
        const object = { answer: 42, __resolved__: true };
        const result = await object;

        return result === object && result.answer === 42 && result.__resolved__ ? 1 : 0;
    }

    awaitNestedPromises(): number {
        const outer = new Promise((resolve) => {
            const inner = new Promise((resolveInner) => resolveInner(2));
            const innerValue = await inner;
            resolve(innerValue + 1);
        });
        const result = await outer;

        return result === 3 ? 1 : 0;
    }

    async asyncValue(): Promise<number> {
        return 7;
    }

    asyncValueIsPromise(): number {
        const promise = this.asyncValue();
        if (promise === 7) return 0;

        const result = await promise;
        return result === 7 ? 1 : 0;
    }

    async asyncThrow(): Promise<number> {
        throw new Error("async failure");
    }

    asyncThrowRejectsAfterCall(): number {
        let returnedFromCall = false;
        try {
            const promise = this.asyncThrow();
            returnedFromCall = true;
            await promise;
            return 0;
        } catch (error) {
            return returnedFromCall ? 1 : 0;
        }
    }

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

    resolvePreservesSettledPromise(): number {
        const original = Promise.resolve(7);
        const resolved = Promise.resolve(original);
        const value = await resolved;

        return resolved === original && value === 7 ? 1 : 0;
    }

    resolvePreservesPendingPromise(): number {
        const original = new Promise((resolve) => resolve(7));
        const resolved = Promise.resolve(original);
        const value = await resolved;

        return resolved === original && value === 7 ? 1 : 0;
    }

    resolvePreservesRejectedPromise(): number {
        const original = Promise.reject(new Error("rejected"));
        const resolved = Promise.resolve(original);
        if (resolved !== original) return 0;

        try {
            await resolved;
            return 0;
        } catch (error) {
            return 1;
        }
    }

    awaitRejectedPromise() {
        const promise = Promise.reject(new Error("An error occurred"));
        await promise; // exception
    }
}
