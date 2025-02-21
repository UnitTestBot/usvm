// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Test: Basic number static
class StaticNumber {
    static value = 10;

    getValue(): number {
        return StaticNumber.value;
    }
}

// Test: Sequential static modifications with value persistence
class StaticModification {
    static count = 0;

    static incrementTwice(): number {
        this.count++;
        this.count++;
        return this.count;
    }
}

// Test: Inheritance shadowing
class StaticParent {
    static id = 100;
}

class StaticChild extends StaticParent {
    static id = 200;

    getParentId(): number {
        return StaticParent.id;
    }

    getChildId(): number {
        return StaticChild.id;
    }
}

// Test: Boolean static toggle
class StaticBoolean {
    static flag: boolean = false;

    toggleAndGet(): boolean {
        StaticBoolean.flag = !StaticBoolean.flag;
        return StaticBoolean.flag;
    }
}

// Test: Array static manipulation
class StaticArray {
    static numbers = [1, 2, 3];

    pushTwice(): number {
        StaticArray.numbers.push(4);
        StaticArray.numbers.push(5);
        return StaticArray.numbers.length;
    }
}

// Test: Null initialization and update
class StaticNull {
    static value: number | null = null;

    initialize(): number {
        StaticNull.value = 5;
        return StaticNull.value!;
    }
}

// Test: Object static operations
class StaticObject {
    static config: Config = {enabled: false, count: 0};

    toggleAndGet(): Config {
        StaticObject.config.flip()
        StaticObject.config.increment();
        return StaticObject.config;
    }
}

class Config {
    enabled: boolean;
    count: number;

    flip(): void {
        this.enabled = !this.enabled;
    }

    increment(): void {
        this.count++;
    }
}

// Test: Field swapping
class StaticAccess {
    static a = 1;
    static b = 2;

    calculateSum(): number {
        return StaticAccess.a + StaticAccess.b;
    }

    swapAndGetValues(): number[] {
        [StaticAccess.a, StaticAccess.b] = [StaticAccess.b, StaticAccess.a];
        return [StaticAccess.a, StaticAccess.b];
    }
}
