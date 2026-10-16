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

    static nestedSuper(): number {
        const subject = new SymbolicConstructorChild();
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

class SymbolicConstructorBase {
    value: number = 1;

    constructor() {
        this.value = this.value + 2;
    }
}

class SymbolicConstructorMiddle extends SymbolicConstructorBase {
    middleValue: number = this.value * 3;

    constructor() {
        super();
        this.value = this.middleValue + 4;
    }
}

class SymbolicConstructorChild extends SymbolicConstructorMiddle {
    childValue: number = this.value * 5;

    constructor() {
        super();
        this.value = this.childValue + 6;
    }
}

class SymbolicImplicitConstructorChild extends SymbolicConstructorMiddle {}
