// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

/**
 * Field access and object-based reachability scenarios.
 * Tests reachability through object field comparisons and modifications.
 */
class FieldAccessReachability {
    x: number = 0;
    y: number = 5;

    // Simple field access reachability
    simpleFieldReachable(): number {
        if (this.x > 0) {
            if (this.y < 10) {
                return 1; // Reachable when x > 0 and y < 10
            }
        }
        return 0;
    }

    // Field modification affecting reachability
    fieldModificationReachable(value: number): number {
        this.x = value;
        if (this.x > 15) {
            if (this.x < 25) {
                return 1; // Reachable when 15 < value < 25
            }
        }
        return 0;
    }

    // Unreachable due to field constraints
    fieldConstraintUnreachable(): number {
        this.x = 10;
        if (this.x > 20) {
            return -1; // Unreachable: x is set to 10, cannot be > 20
        }
        return 0;
    }

    // Object creation and field access
    objectCreationReachable(): number {
        const obj = new SimpleDataClass();
        obj.value = 42;
        if (obj.value === 42) {
            if (obj.flag) {
                return 1; // Reachable if flag is true
            }
        }
        return 0;
    }

    // Multiple object field access
    multipleObjectFields(): number {
        const obj1 = new SimpleDataClass();
        const obj2 = new SimpleDataClass();
        obj1.value = 10;
        obj2.value = 20;

        if (obj1.value < obj2.value) {
            if (obj1.value + obj2.value === 30) {
                return 1; // Reachable: 10 < 20 and 10 + 20 = 30
            }
        }
        return 0;
    }

    // Field access chain reachability
    fieldChainReachable(): number {
        const container = new ContainerClass();
        container.data = new SimpleDataClass();
        container.data.value = 100;

        if (container.data.value > 50) {
            if (container.data.value < 150) {
                return 1; // Reachable when 50 < value < 150
            }
        }
        return 0;
    }

    // Ambiguous field access (multiple classes with same field name)
    ambiguousFieldAccess(useFirst: boolean): number {
        let obj: any;
        if (useFirst) {
            obj = new ClassA();
        } else {
            obj = new ClassB();
        }

        obj.commonField = 25;
        if (obj.commonField > 20) {
            return 1; // Reachable in both branches
        }
        return 0;
    }
}

// Helper classes for field access tests
class SimpleDataClass {
    value: number = 0;
    flag: boolean = true;
}

class ContainerClass {
    data: SimpleDataClass;
}

class ClassA {
    commonField: number = 0;
    uniqueA: number = 1;
}

class ClassB {
    commonField: number = 0;
    uniqueB: number = 2;
}
