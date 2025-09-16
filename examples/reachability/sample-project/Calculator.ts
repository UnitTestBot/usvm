// Sample TypeScript project for reachability analysis
export class Calculator {
    private value: number = 0;

    constructor(initialValue: number = 0) {
        this.value = initialValue;
    }

    public add(num: number): Calculator {
        if (num < 0) {
            throw new Error("Negative numbers not allowed");
        }
        this.value += num;
        return this;
    }

    public subtract(num: number): Calculator {
        this.value -= num;
        return this;
    }

    public multiply(num: number): Calculator {
        if (num === 0) {
            this.value = 0;
            return this;
        }
        this.value *= num;
        return this;
    }

    public divide(num: number): Calculator {
        if (num === 0) {
            throw new Error("Division by zero");
        }
        this.value /= num;
        return this;
    }

    public getValue(): number {
        return this.value;
    }

    public reset(): Calculator {
        this.value = 0;
        return this;
    }

    // Method with complex control flow for testing
    public complexOperation(a: number, b: number, c: number): number {
        if (a > 0) {
            if (b > 0) {
                if (c > 0) {
                    return a + b + c;  // Target: reachable path
                } else {
                    return a + b - c;  // Target: reachable path
                }
            } else {
                return a - b;  // Target: reachable path
            }
        } else {
            if (b === 0 && c === 0) {
                return 0;  // Target: hard to reach path
            } else {
                return -1;  // Target: unreachable in some contexts
            }
        }
    }
}
