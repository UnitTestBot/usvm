class NewA {
    value: number;
    onlyA: number = 1;

    constructor(value: number) {
        this.value = value;
    }
}

class NewB {
    value: number;
    onlyB: number = 2;

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

// Inherited declarations also appear in scene-wide lookup for the parent class.
class NewAChild extends NewA {}

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

    writeToEither(useA: boolean): number {
        const value = new (useA ? NewA : NewB)(7);
        value.value = 5;
        return value.value;
    }

    writeThroughAlias(useA: boolean): number {
        const value = new (useA ? NewA : NewB)(7);
        const alias = value;
        alias.value = 11;
        return value.value;
    }

    writeFieldWithDifferentRuntimeSort(useNumber: boolean): boolean {
        const value = new (useNumber ? NewA : NewText)(7);
        value.value = useNumber ? 9 : "nine";
        return useNumber ? value.value === 9 : value.value === "nine";
    }

    fieldReadWithInheritedDeclarations(useNumber: boolean): boolean {
        const value = new (useNumber ? NewA : NewText)(7);
        return useNumber ? value.value === 7 : value.value === "seven";
    }

    fieldWriteWithInheritedDeclarations(useNumber: boolean): boolean {
        const value = new (useNumber ? NewA : NewText)(7);
        value.value = useNumber ? 11 : "eleven";
        return useNumber ? value.value === 11 : value.value === "eleven";
    }

    writeIncompatibleRuntimeField(useNumber: boolean): boolean {
        const value = new (useNumber ? NewA : NewText)(7);
        value.value = "changed";
        return value.value === "changed";
    }

    writePossiblyIncompatibleRuntimeField(useNumber: boolean, writeString: boolean): boolean {
        const value = new (useNumber ? NewA : NewText)(7);
        value.value = writeString ? "changed" : 9;
        return writeString ? value.value === "changed" : value.value === 9;
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

    writeAddedFieldFromBoxedValue(useNumber: boolean): boolean {
        const source: any = { payload: useNumber ? 7 : false };
        const target: any = { marker: 1 };

        target.value = source.payload;

        return useNumber ? target.value === 7 : target.value === false;
    }

    returnAddedFieldFromBoxedValue(useNumber: boolean): any {
        const source: any = { payload: useNumber ? 7 : false };
        const target: any = { marker: 1 };

        target.value = source.payload;

        return target;
    }

    writeTypedPrimitiveFromBoxedValue(useNumber: boolean): boolean {
        const source: any = { payload: useNumber ? 7 : false };
        const target = new NewTypedFields();
        const value = source.payload;

        if (useNumber) {
            target.count = value;
            return target.count === 7;
        }

        target.enabled = value;
        return target.enabled === false;
    }

    writeTypedReferenceFromBoxedValue(hasArray: boolean): boolean {
        const source: any = { payload: hasArray ? [7] : undefined };
        const target = new NewTypedFields();

        target.values = source.payload;
        const alias = target.values;

        return hasArray ? alias.length === 1 && alias[0] === 7 : alias === undefined;
    }

    writeAddedReferenceFromBoxedValue(hasArray: boolean): boolean {
        const source: any = { payload: hasArray ? [7] : new NewA(7) };
        const target: any = { marker: 1 };

        target.value = source.payload;
        const alias = target.value;

        return hasArray ? alias.length === 1 && alias[0] === 7 : alias instanceof NewA && alias.value === 7;
    }

    writeAddedNullishFromBoxedValue(useNull: boolean): boolean {
        const source: any = { payload: useNull ? null : undefined };
        const target: any = { marker: 1 };

        target.value = source.payload;

        return useNull ? target.value === null : target.value === undefined;
    }

    writeAddedFunctionFromBoxedValue(useFunction: boolean): boolean {
        const source: any = { payload: useFunction ? (() => 7) : "text" };
        const target: any = { marker: 1 };

        target["value"] = source.payload;
        const alias = target.value;

        return useFunction ? typeof alias === "function" && alias instanceof Function : alias === "text";
    }

    writeIncompatibleBoxedValue(useNumber: boolean): boolean {
        const source: any = { payload: useNumber ? 7 : false };
        const target = new NewTypedFields();

        target.count = source.payload;

        return target.count === 7;
    }

    writeIncompatibleBoxedValueByKey(useNumber: boolean): boolean {
        const source: any = { payload: useNumber ? 7 : false };
        const target: any = new NewTypedFields();

        target["count"] = source.payload;

        return target.count === 7;
    }

    nonConstructableWithHasInstance(): boolean {
        const selected: any = new NewHasInstanceObject();
        new selected();
        return false;
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

    caughtNonConstructable(): number {
        try {
            const selected: any = 42;
            new selected();
            return 0;
        } catch {
            return 1;
        }
    }

    caughtNonConstructableString(value: string): number {
        if (value.length !== 1) return 0;

        try {
            const selected: any = value + "b";
            new selected();
            return 0;
        } catch {
            return 1;
        }
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

class NewTypedFields {
    count: number = 0;
    enabled: boolean = true;
    values: number[] = [];
}

class NewHasInstanceObject {
    [Symbol.hasInstance](_value: unknown): boolean { return true; }
}
