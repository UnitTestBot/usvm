// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,ES6RedundantAwait

class Async2 {
    // Test chaining promises
    chainPromises(): number {
        const promise1 = Promise.resolve(10);
        const promise2 = promise1.then((value) => value * 2);
        return await promise2; // Should return 20
    }

    // Test promise chaining with rejection
    chainWithRejection(): number {
        const promise1 = Promise.reject(new Error("First error"));
        const promise2 = promise1.catch((error) => 42);
        return await promise2; // Should return 42
    }

    // Test async function returning a promise
    async asyncFunction(value: number): Promise<number> {
        if (value > 0) {
            return value * 2;
        } else {
            throw new Error("Value must be positive");
        }
    }

    // Test awaiting an async function
    awaitAsyncFunction(): number {
        return await this.asyncFunction(5); // Should return 10
    }

    // Test multiple awaits in sequence
    multipleAwaits(): number {
        const promise1 = Promise.resolve(5);
        const promise2 = Promise.resolve(3);
        const result1 = await promise1;
        const result2 = await promise2;
        return result1 + result2; // Should return 8
    }

    // Test Promise.all
    promiseAll(): number {
        const promises = [
            Promise.resolve(1),
            Promise.resolve(2),
            Promise.resolve(3),
        ];
        const results = await Promise.all(promises);
        return results.reduce((sum, val) => sum + val, 0); // Should return 6
    }

    // Test Promise.race
    promiseRace(): number {
        const promises = [
            Promise.resolve(42),
            Promise.resolve(100),
        ];
        return await Promise.race(promises); // Should return 42 (first resolved)
    }

    // Test nested async/await
    nestedAsync(): number {
        const inner = async () => {
            const value = await Promise.resolve(15);
            return value + 5;
        };
        return await inner(); // Should return 20
    }

    // Test conditional awaiting
    conditionalAwait(usePromise: boolean): number {
        if (usePromise) {
            return await Promise.resolve(100);
        } else {
            return 200;
        }
    }

    // Test exception handling with try/catch
    asyncTryCatch(): number {
        try {
            await Promise.reject(new Error("Test error"));
            return -1; // Should not reach this
        } catch (error) {
            return 50; // Should return this
        }
    }

    // Test finally block with async
    asyncFinally(): number {
        let result = 0;
        try {
            result = await Promise.resolve(10);
        } finally {
            result += 5;
        }
        return result; // Should return 15
    }

    // Test setTimeout-like delayed promise
    delayedPromise(delay: number, value: number): Promise<number> {
        return new Promise((resolve) => {
            // Simulate async delay (in real implementation this would use setTimeout)
            resolve(value);
        });
    }

    // Test concurrent operations
    concurrentOperations(): number {
        const promise1 = this.delayedPromise(100, 10);
        const promise2 = this.delayedPromise(200, 20);
        const promise3 = this.delayedPromise(50, 5);

        const results = await Promise.all([promise1, promise2, promise3]);
        return results[0] + results[1] + results[2]; // Should return 35
    }

    // Test promise constructor with immediate resolution
    immediateResolve(): number {
        const promise = new Promise((resolve, reject) => {
            const condition = true;
            if (condition) {
                resolve(123);
            } else {
                reject(new Error("Should not happen"));
            }
        });
        return await promise; // Should return 123
    }

    // Test promise constructor with immediate rejection
    immediateReject(): number {
        const promise = new Promise((resolve, reject) => {
            const condition = false;
            if (condition) {
                resolve(456);
            } else {
                reject(new Error("Immediate rejection"));
            }
        });
        return await promise; // Should throw exception
    }

    // Test promise with both resolve and reject paths
    conditionalPromise(shouldResolve: boolean): number {
        const promise = new Promise((resolve, reject) => {
            if (shouldResolve) {
                resolve(777);
            } else {
                reject(new Error("Conditional rejection"));
            }
        });
        return await promise;
    }

    // Test awaiting primitive values (should pass through)
    awaitPrimitives(): number {
        const num = await 42;
        const str = await "hello";
        const bool = await true;
        return num; // Should return 42
    }

    // Test mixed promise and non-promise values
    mixedValues(): number {
        const directValue = 10;
        const promiseValue = await Promise.resolve(20);
        const anotherDirect = 30;
        return directValue + promiseValue + anotherDirect; // Should return 60
    }

    // Test recursive async calls
    recursiveAsync(count: number): number {
        if (count <= 0) {
            return 0;
        }
        const currentValue = await Promise.resolve(count);
        const restValue = await this.recursiveAsync(count - 1);
        return currentValue + restValue;
    }

    // Test promise that resolves to another promise
    nestedPromiseResolution(): number {
        const innerPromise = Promise.resolve(88);
        const outerPromise = Promise.resolve(innerPromise);
        return await outerPromise; // Should return 88
    }

    // Test error propagation through promise chain
    errorPropagation(): number {
        return await Promise.resolve(10)
            .then(value => {
                if (value > 5) {
                    throw new Error("Value too large");
                }
                return value * 2;
            })
            .catch(error => 99); // Should return 99
    }
}
