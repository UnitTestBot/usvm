// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class A {
    x: number;

    constructor(x: number) {
        this.x = x;
    }

    foo(): number {
        return 21;
    }
}

class B {
    x: number;

    constructor(x: number) {
        this.x = x;
    }

    foo(): number {
        return this.x;
    }
}

class C {
    x: A;

    constructor(x: A) {
        this.x = x;
    }
}

class D {
    x: B;

    constructor(x: B) {
        this.x = x;
    }
}

class Example {
    testFunction(): C {
        const obj: A = new B(11);
        const number = obj.foo();

        const aaa: C = new D(new A(number));
        return aaa
    }
}
