// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Call {
    callSimple(): number {
        return this.fortyTwo()
    }

    fortyTwo(): number {
        return 42
    }

    fib(n: number): number {
        if (n != n) return 0
        if (n < 0) return -1
        if (n > 10) return -100
        if (n == 0) return 1
        if (n == 1) return 1
        return this.fib(n - 1) + this.fib(n - 2)
    }

    callConcrete(): number {
        let x = new A()
        return x.foo()
    }

    callHidden(): number {
        let x: any = new B();
        return x.foo()
    }

    f(x: any, ...args: any[]): number {
        if (x == undefined) return 0
        return args.length + 1
    }

    callNoVararg(): number {
        return this.f(5) // 1
    }

    callVararg1(): number {
        return this.f(5, 10) // 2
    }

    callVararg2(): number {
        return this.f(5, 10, 20) // 3
    }

    callVarargArray(): number {
        return this.f(5, [10, 20]) // 2
    }

    g(x: any, y: any): number {
        if (x == undefined) return 0
        if (y == undefined) return 1
        return 2
    }

    callNormal(): number {
        return this.g(5, 10) // 2
    }

    callSingle(): number {
        return this.g(5) // 1
    }

    callNone(): number {
        return this.g() // 0
    }

    callUndefined(): number {
        return this.g(undefined, 20) // 0
    }

    callExtra(): number {
        return this.g(5, 10, 20) // 2
    }

    overloaded(a: number): number;
    overloaded(a: string): number;
    overloaded(a: any): number {
        if (typeof a === 'number') return 1
        if (typeof a === 'string') return 2
        return -1
    }

    callOverloadedNumber(): number {
        return this.overloaded(5);
    }

    callOverloadedString(): number {
        return this.overloaded("test");
    }

    callNamespace(): number {
        return new N1.C().foo();
    }

    static fifty(): number {
        return 50;
    }

    callStatic(): number {
        return Call.fifty();
    }

    callVirtual(obj: Parent): number {
        return obj.virtualMethod(); // 100 or 200
    }

    callVirtualParent(): number {
        let obj: Parent = new Parent();
        return obj.virtualMethod(); // 100
    }

    callVirtualChild(): number {
        let obj: Child = new Child();
        return obj.virtualMethod(); // 200
    }

    virtualDispatch(obj: Parent): number {
        if (obj instanceof Child) {
            return obj.virtualMethod(); // 200
        } else if (obj instanceof Parent) {
            return obj.virtualMethod(); // 100
        }
        return -1; // unreachable
    }

    methodWithDefault(x: number = 5): number {
        return x;
    }

    callDefault(): number {
        return this.methodWithDefault(); // 5 (default)
    }

    callDefaultPass(): number {
        return this.methodWithDefault(8); // 8 (passed)
    }

    callDefaultUndefined(): number {
        return this.methodWithDefault(undefined); // 5 (default)
    }

    callConstructorWithParam(): number {
        let x = new ValueHolder(5);
        return x.getValue();
    }

    callConstructorWithPublicParam(): number {
        let x = new ValueHolderWithPublicParameter(5);
        return x.value;
    }
}

class A {
    foo(): number {
        return 10
    }
}

class B {
    foo(): number {
        return 20
    }
}

namespace N1 {
    class C {
        foo(): number {
            return 30
        }
    }
}

namespace N2 {
    class C {
        foo(): number {
            return 40
        }
    }
}

class Parent {
    virtualMethod(): number {
        return 100;
    }
}

class Child extends Parent {
    override virtualMethod(): number {
        return 200;
    }
}

class ValueHolder {
    value: number;

    constructor(value: number) {
        this.value = value;
    }

    getValue(): number {
        return this.value;
    }
}

class ValueHolderWithPublicParameter {
    constructor(public value: number) {
    }
}
