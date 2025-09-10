// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Function call reachability scenarios.
 * Tests reachability through method calls, return values, and call chains.
 */
class FunctionCallReachability {

    // Simple method call reachability
    simpleCallReachable(x: number): number {
        const result = this.doubleValue(x);
        if (result > 20) {
            if (result < 40) {
                return 1; // Reachable when 10 < x < 20 (since result = 2*x)
            }
        }
        return 0;
    }

    // Method call with unreachable path
    callUnreachable(): number {
        const result = this.constantValue();
        if (result > 100) {
            return -1; // Unreachable: constantValue() returns 42, not > 100
        }
        return 0;
    }

    // Chained method calls
    chainedCallsReachable(x: number): number {
        const result = this.addTen(this.doubleValue(x));
        if (result === 30) {
            return 1; // Reachable when x = 10: double(10) = 20, addTen(20) = 30
        }
        return 0;
    }

    // Conditional method calls
    conditionalCallReachable(useFirst: boolean, x: number): number {
        let result: number;
        if (useFirst) {
            result = this.doubleValue(x);
        } else {
            result = this.tripleValue(x);
        }

        if (result === 30) {
            return 1; // Reachable: x=15 with useFirst=true, or x=10 with useFirst=false
        }
        return 0;
    }

    // Recursive call reachability
    recursiveCallReachable(n: number): number {
        const result = this.factorial(n);
        if (result === 24) {
            return 1; // Reachable when n = 4 (4! = 24)
        }
        return 0;
    }

    // Method call affecting object state
    stateModificationReachable(): number {
        const counter = new Counter();
        this.incrementCounter(counter, 5);

        if (counter.value === 5) {
            if (counter.value > 3) {
                return 1; // Reachable: counter starts at 0, incremented by 5
            }
        }
        return 0;
    }

    // Method with side effects on multiple objects
    multiObjectCallReachable(): number {
        const obj1 = new SimpleCounter();
        const obj2 = new SimpleCounter();

        this.crossIncrement(obj1, obj2, 10);

        if (obj1.count === 10) {
            if (obj2.count === 10) {
                return 1; // Reachable: both objects get incremented by 10
            }
        }
        return 0;
    }

    // Static method call reachability
    staticCallReachable(x: number, y: number): number {
        const result = MathUtils.add(x, y);
        if (result > 50) {
            if (result < 100) {
                return 1; // Reachable with appropriate x, y values
            }
        }
        return 0;
    }

    // Method call with array parameter
    arrayParameterCallReachable(): number {
        const arr = [1, 2, 3, 4, 5];
        const sum = this.calculateSum(arr);

        if (sum === 15) {
            if (arr.length === 5) {
                return 1; // Reachable: sum of [1,2,3,4,5] is 15
            }
        }
        return 0;
    }

    // Helper methods for testing
    doubleValue(x: number): number {
        return x * 2;
    }

    tripleValue(x: number): number {
        return x * 3;
    }

    constantValue(): number {
        return 42;
    }

    addTen(x: number): number {
        return x + 10;
    }

    factorial(n: number): number {
        if (n <= 1) return 1;
        if (n > 10) return -1; // Prevent infinite recursion in tests
        return n * this.factorial(n - 1);
    }

    incrementCounter(counter: Counter, amount: number): void {
        counter.value = counter.value + amount;
    }

    crossIncrement(obj1: SimpleCounter, obj2: SimpleCounter, amount: number): void {
        obj1.count = obj1.count + amount;
        obj2.count = obj2.count + amount;
    }

    calculateSum(arr: number[]): number {
        let sum = 0;
        for (let i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum;
    }
}

// Helper classes for function call tests
class Counter {
    value: number = 0;
}

class SimpleCounter {
    count: number = 0;
}

class MathUtils {
    static add(a: number, b: number): number {
        return a + b;
    }

    static multiply(a: number, b: number): number {
        return a * b;
    }
}
