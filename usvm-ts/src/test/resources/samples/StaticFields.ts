// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Test: Basic number static
class StaticNumber {
    static value = 10;

    static getValue(): number {
        return this.value;
    }
}

// Test: Default value initialization
class StaticDefault {
    static value: number;

    static getValue(): number {
        return this.value;
    }
}

// Test: Sequential static modifications with value persistence
class StaticModification {
    static count = 0;

    static incrementTwice(): number {
        this.count += 1;
        this.count += 1;
        return this.count;
    }
}

// Test: Inheritance
class StaticBase {
    static id = 100;
}

class StaticDerived extends StaticBase {
    static f = 42;

    static getId(): number {
        return this.id + this.f;
    }
}

// Test: Inheritance shadowing
class StaticParent {
    static id = 100;
}

class StaticChild extends StaticParent {
    override static id = 200;

    getParentId(): number {
        return StaticParent.id;
    }

    getChildId(): number {
        return StaticChild.id;
    }

    static getId(): number {
        return this.id;
    }
}

// Test: Boolean static toggle
class StaticBoolean {
    static flag: boolean = false;

    static toggleAndGet(): boolean {
        this.flag = !this.flag;
        return this.flag;
    }
}

// Test: Array static manipulation
class StaticArray {
    static numbers = [1, 2, 3];

    static pushTwice(): number {
        this.numbers.push(4);
        this.numbers.push(5);
        return this.numbers.length;
    }
}

// Test: Null initialization and update
class StaticNull {
    static value: number | null = null;

    static initialize(): number {
        this.value = 5;
        return this.value;
    }
}

// Test: Object static operations
class StaticObject {
    static config: Config = {enabled: true, count: 10};

    static modifyAndGet(): Config {
        this.config.increment();
        this.config.increment();
        this.config.increment();
        this.config.flip();
        this.config.increment();
        this.config.increment();
        this.config.flip();
        return this.config;
    }
}

class Config {
    enabled: boolean;
    count: number;

    flip(): void {
        this.enabled = !this.enabled;
    }

    increment(): void {
        this.count += 1;
    }
}

// Test: Field swapping
class StaticAccess {
    static a = 5;
    static b = 10;

    static calculateSum(): number {
        return this.a + this.b;
    }

    static swapAndGetValues(): number[] {
        [this.a, this.b] = [this.b, this.a];
        return [this.a, this.b];
    }
}

// Test: Any-type static
class StaticAny {
    static value: any = 10;

    static getNumber(): number {
        return this.value;
    }
}
