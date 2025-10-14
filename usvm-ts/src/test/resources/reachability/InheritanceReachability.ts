// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Class inheritance and polymorphism reachability scenarios.
 * Tests reachability through class hierarchies, method overriding, and inheritance patterns.
 */

abstract class BaseClass {
    protected value: number = 0;

    abstract process(): number;

    protected commonMethod(): number {
        return this.value * 2;
    }
}

class ConcreteA extends BaseClass {
    constructor(initialValue: number) {
        super();
        this.value = initialValue;
    }

    process(): number {
        if (this.value > 10) {
            return this.commonMethod(); // Call to parent method
        }
        return 0;
    }

    specificMethodA(): number {
        if (this.value === 15) {
            return 1; // Reachable when value is exactly 15
        }
        return 0;
    }
}

class ConcreteB extends BaseClass {
    private multiplier: number = 3;

    constructor(initialValue: number, multiplier: number) {
        super();
        this.value = initialValue;
        this.multiplier = multiplier;
    }

    process(): number {
        const result = this.value * this.multiplier;
        if (result > 50) {
            return 1; // Reachable based on value and multiplier
        }
        return 0;
    }

    // Override parent method with different implementation
    commonMethod(): number {
        return this.value * this.multiplier;
    }
}

class InheritanceReachability {

    // Conditional instantiation with different types
    polymorphicCallReachable(useA: boolean, value: number): number {
        let result: number;

        if (useA) {
            const instanceA = new ConcreteA(value);
            result = instanceA.process();
        } else {
            const instanceB = new ConcreteB(value, 2);
            result = instanceB.process();
        }

        if (result > 0) {
            return 1; // Reachable through either path
        }
        return 0;
    }

    // Type-specific method call
    instanceofInheritanceReachable(value: number): number {
        const obj = new ConcreteA(value);

        const specificResult = obj.specificMethodA();
        if (specificResult === 1) {
            return 1; // Reachable when value is 15
        }
        return 0;
    }

    // Method overriding with different calculations
    methodOverrideReachable(): number {
        const objA = new ConcreteA(10);
        const objB = new ConcreteB(10, 3);

        const resultA = objA.commonMethod(); // Uses base implementation: 10 * 2 = 20
        const resultB = objB.commonMethod(); // Uses overridden implementation: 10 * 3 = 30

        if (resultA === 20 && resultB === 30) {
            return 1; // Reachable with different method implementations
        }
        return 0;
    }

    // Constructor with parameter passing
    constructorChainingReachable(): number {
        const obj = new ConcreteB(15, 4);
        const processResult = obj.process(); // 15 * 4 = 60 > 50, returns 1

        if (processResult === 1) {
            return 1; // Reachable when constructor parameters lead to result > 50
        }
        return 0;
    }

    // Inheritance with protected field access
    simpleInheritanceReachable(): number {
        const obj = new ConcreteA(12);

        if (obj.value > 10) { // Access protected field from parent
            const commonResult = obj.commonMethod(); // Call inherited method
            if (commonResult === 24) { // 12 * 2 = 24
                return 1; // Reachable through inheritance
            }
        }
        return 0;
    }

    // Field access through inheritance hierarchy
    fieldInheritanceReachable(): number {
        const obj = new ConcreteB(8, 5);

        // Access inherited protected field
        if (obj.value < 10) {
            const result = obj.process(); // 8 * 5 = 40, not > 50
            if (result === 0) {
                return 1; // Reachable when multiplication doesn't exceed threshold
            }
        }
        return 0;
    }
}
