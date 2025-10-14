// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Higher-order functions and closures reachability scenarios.
 * Tests reachability through function composition and array processing patterns.
 */
class HigherOrderFunctionsReachability {

    // Function factory with conditional return
    createMultiplier(factor: number): (x: number) => number {
        if (factor > 2) {
            return function(x: number): number {
                return x * factor;
            };
        } else {
            return function(x: number): number {
                return x;
            };
        }
    }

    functionFactoryReachable(value: number): number {
        const factor = 3;
        if (factor > 2) {
            const result = value * factor;
            if (result === 15) {
                return 1; // Reachable when value = 5 and factor = 3
            }
        }
        return 0;
    }

    // Array processing with callback-like behavior
    processWithCallback(arr: number[], multiplier: number): number {
        for (const item of arr) {
            const processed = item * multiplier;
            if (processed > 100) {
                return 1; // Reachable based on array values and multiplier
            }
        }
        return 0;
    }

    callbackReachable(): number {
        const numbers = [10, 20, 30];
        const multiplier = 5;
        return this.processWithCallback(numbers, multiplier);
        // Returns 1 because 30 * 5 = 150 > 100
    }

    // Counter with incremental updates
    closureReachable(): number {
        let counter = 0;

        counter += 5;
        counter += 10;

        if (counter === 15) {
            return 1; // Reachable when accumulated counter equals 15
        }
        return 0;
    }

    // Function composition through sequential operations
    compositionReachable(input: number): number {
        const doubled = input * 2;
        const addedTen = doubled + 10;

        if (addedTen === 30) {
            return 1; // Reachable when input = 10: double(10) = 20, addTen(20) = 30
        }
        return 0;
    }

    // Conditional expression with ternary operator
    arrowFunctionReachable(): number {
        const input = 7;
        const result = input > 5 ? input * 2 : input;

        if (result === 14) {
            return 1; // Reachable: 7 > 5, so 7 * 2 = 14
        }
        return 0;
    }

    // Map-like transformation with early termination
    mapOperationReachable(values: number[]): number {
        let hasLargeValue = false;

        for (const value of values) {
            const transformed = value * 3;
            if (transformed > 50) {
                hasLargeValue = true;
                break;
            }
        }

        if (hasLargeValue) {
            return 1; // Reachable when any value * 3 > 50
        }
        return 0;
    }

    // Filter-like counting operation
    filterOperationReachable(values: number[]): number {
        let filteredCount = 0;

        for (const value of values) {
            if (value > 10) {
                filteredCount++;
            }
        }

        if (filteredCount === 2) {
            return 1; // Reachable when exactly 2 values are > 10
        }
        return 0;
    }
}
