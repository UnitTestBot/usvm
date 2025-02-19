// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Test: Basic static field access
class StaticBasic {
    static value: number = 42;

    getValue(): number {
        return StaticBasic.value;
    }
}

// Test: Static field modification
class StaticModification {
    static count: number = 0;

    incrementAndGet(): number {
        StaticModification.count++;
        return StaticModification.count;
    }
}

// Test: Inheritance with static fields
class ParentStatic {
    static family: string = "Parent";

    static getFamily(): string {
        return ParentStatic.family;
    }
}

class ChildStatic extends ParentStatic {
    static family: string = "Child";

    static getChildFamily(): string {
        return ChildStatic.family;
    }
}

// Test: Static field shadowing
class ShadowParent {
    static id: number = 100;
}

class ShadowChild extends ShadowParent {
    static id: number = 200;

    getParentId(): number {
        return ShadowParent.id;
    }
}

// Test: Complex static initializer
class StaticInitializer {
    static computed: number = (() => {
        let sum = 0;
        for (let i = 1; i <= 5; i++) sum += i;
        return sum;
    })();

    getComputed(): number {
        return StaticInitializer.computed;
    }
}

// Test: Multiple static fields
class MultipleStatics {
    static count: number = 0;
    static readonly MAX: number = 100;
    static log: string[] = [];

    static addEntry(entry: string): void {
        MultipleStatics.log.push(entry);
    }

    getLogLength(): number {
        return MultipleStatics.log.length;
    }
}

// Test: Static object field
class StaticObject {
    static config = {
        enabled: true,
        timeout: 3000
    };

    checkEnabled(): boolean {
        return StaticObject.config.enabled;
    }
}

// Test: Static field type variations
class StaticTypes {
    static flag: boolean = true;
    static names: string[] = ["Alice", "Bob"];
    static magicNumber: number = 42;

    getTypeResults(): [boolean, string, number] {
        return [
            StaticTypes.flag,
            StaticTypes.names[0],
            StaticTypes.magicNumber
        ];
    }
}
