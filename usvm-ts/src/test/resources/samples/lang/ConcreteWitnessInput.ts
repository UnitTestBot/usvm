declare class ConcreteWitnessExternal {
    static operation(): number;
}

class ConcreteWitnessBox {
    value: number = 0;
}

class ConcreteWitnessInput {
    value: number = 0;

    mutateAndStop(box: ConcreteWitnessBox, items: number[]): number {
        if (this.value !== 11 || box.value !== 12 || items.length !== 1 || items[0] !== 13) return 0;

        this.value = 91;
        box.value = 92;
        items[0] = 93;
        return this.nested(items, box);
    }

    nested(items: number[], box: ConcreteWitnessBox): number {
        return ConcreteWitnessExternal.operation();
    }

    alias(left: number[], right: number[]): number {
        if (left === right && left.length === 1) return left[0];

        return 0;
    }
}
