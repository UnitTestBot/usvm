// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Asynchronous programming reachability scenarios.
 * Tests reachability through async patterns and error handling.
 */
class AsyncReachability {

    // Async function with conditional logic
    async asyncAwaitReachable(delay: number): Promise<number> {
        const result = delay * 2;
        if (result > 50) {
            if (result < 100) {
                return 1; // Reachable when delay * 2 is between 50-100
            }
        }
        return 0;
    }

    // Promise with conditional return
    promiseChainReachable(value: number): Promise<number> {
        const doubled = value * 2;
        if (doubled === 20) {
            return Promise.resolve(1); // Reachable when input value is 10
        }
        return Promise.resolve(0);
    }

    // Array processing with Promise-like result
    async promiseAllReachable(values: number[]): Promise<number> {
        const results = values.map(v => v * 2);

        if (results.length === 3) {
            if (results[1] === 40) {
                return 1; // Reachable when values[1] is 20
            }
        }
        return 0;
    }

    // Callback pattern with threshold check
    callbackReachable(value: number, callback: (result: number) => void): number {
        const processed = value + 10;
        if (processed > 25) {
            callback(processed);
            return 1; // Reachable when value > 15
        }
        return 0;
    }

    // Try-catch error handling
    errorHandlingReachable(shouldThrow: boolean): number {
        try {
            if (shouldThrow) {
                throw new Error("Test error");
            }
            return 1; // Reachable when shouldThrow is false
        } catch (error) {
            return -1; // Reachable when shouldThrow is true
        }
    }

    // Conditional branching with different calculations
    conditionalAsyncReachable(useSync: boolean, value: number): number {
        let result: number;

        if (useSync) {
            result = value * 2;
        } else {
            result = value + 10;
        }

        if (result === 20) {
            return 1; // Reachable: value=10 with useSync=true, or value=10 with useSync=false
        }
        return 0;
    }

    // Promise creation with conditional logic
    promiseCreationReachable(shouldResolve: boolean, value: number): number {
        if (shouldResolve) {
            if (value > 5) {
                return 1; // Reachable when shouldResolve=true and value>5
            }
        } else {
            if (value < 0) {
                return -1; // Reachable when shouldResolve=false and value<0
            }
        }
        return 0;
    }
}
