class NewA {
    value: number;

    constructor(value: number) {
        this.value = value;
    }
}

class NewB {
    value: number;

    constructor(value: number) {
        this.value = value;
    }
}

class NewText {
    value: string;

    constructor(_value: number) {
        this.value = "seven";
    }
}

class ConstructorCounter {
    count: number;

    constructor() {
        this.count = 0;
    }
}

class CountA {
    constructor(counter: ConstructorCounter) {
        counter.count = counter.count + 1;
    }
}

class CountB {
    constructor(counter: ConstructorCounter) {
        counter.count = counter.count + 1;
    }
}

class RuntimeNew {
    choose(useA: boolean): typeof NewA | typeof NewB {
        return useA ? NewA : NewB;
    }

    dynamicCall(useA: boolean): number {
        const value = new (this.choose(useA))(7);
        return value instanceof NewA && value.value === 7 ? 1 : 0;
    }

    inlineConditional(useA: boolean): number {
        const value = new (useA ? NewA : NewB)(7);
        return value instanceof NewA && value.value === 7 ? 1 : 0;
    }

    localAlias(useA: boolean): number {
        const selected = this.choose(useA);
        const alias = selected;
        const value = new alias(7);
        return value instanceof NewA && value.value === 7 ? 1 : 0;
    }

    valueFromEither(useA: boolean): number {
        const value = new (this.choose(useA))(7);
        return value.value;
    }

    fieldSortByRuntimeClass(useNumber: boolean): boolean {
        const value = new (useNumber ? NewA : NewText)(7);
        return useNumber ? value.value === 7 : value.value === "seven";
    }

    constructorCalledOnce(useA: boolean): boolean {
        const counter = new ConstructorCounter();
        const value = new (useA ? CountA : CountB)(counter);
        return counter.count === 1 && (value instanceof CountA) === useA;
    }

    sideEffectingArgument(): number {
        let selected: typeof NewA | typeof NewB = NewA;
        const value = new selected((selected = NewB, 7));
        return value instanceof NewA && (value as NewA).value === 7 && selected === NewB ? 1 : 0;
    }

    direct(): number {
        const value = new NewA(7);
        return value instanceof NewA && value.value === 7 ? 1 : 0;
    }

    nonConstructable(): number {
        const selected: any = 42;
        const value = new selected();
        return value instanceof NewA ? 1 : 0;
    }

    nonConstructableObject(): number {
        const selected: any = new NewA(7);
        const value = new selected();
        return value instanceof NewA ? 1 : 0;
    }

    symbolicConstructor(selected: typeof NewA): number {
        return new selected(7).value;
    }

    unknownConstructor(selected: any): number {
        return new selected(7).value;
    }
}
