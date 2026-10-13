// @ts-nocheck

class SuperConstructorCall {
    static implicitSuper(): number {
        const subject = new ImplicitConstructorChild();
        return subject.value;
    }

    static explicitSuper(): number {
        const subject = new ExplicitConstructorChild(3);
        return subject.value;
    }

    static shadowedConstructorProperty(): number {
        const subject = new ImplicitConstructorChild();
        subject.constructor = () => subject.value + 10;
        return subject.constructor();
    }
}

class ConstructorParent {
    value: number;

    constructor(value: number = 7) {
        this.value = value;
    }
}

class ImplicitConstructorChild extends ConstructorParent {}

class ExplicitConstructorChild extends ConstructorParent {
    constructor(value: number) {
        super(value + 1);
        this.value = this.value * 2;
    }
}
