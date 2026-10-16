class PlainCounter {
    value = 3;
    increment(value: number): number { return this.value + value; }
}

class LiteralCounter {
    ['value'] = 3;
    ['increment'](value: number): number { return this.value + value; }
    static ['answer'](): number { return 11; }
}

class IteratorCounter {
    value = 3;
    [Symbol.iterator](): number { return 17; }
    ['Symbol.iterator'](): number { return 29; }
    increment(value: number): number { return this.value + value; }
    static [Symbol.iterator](): number { return 31; }
    static ['Symbol.iterator'](): number { return 43; }
}

class SymbolFieldCounter {
    [Symbol.iterator] = 17;
    ['Symbol.iterator'] = 29;
    increment(value: number): number { return value + 1; }
}

class SymbolOnlyCounter {
    [Symbol.iterator](): number { return 17; }
}

export class ComputedClassMembers {
    plainCounter(): number { return new PlainCounter().increment(4); }
    literalCounter(): number { return new LiteralCounter().increment(4); }
    iteratorCounter(): number { return new IteratorCounter().increment(4); }
    literalStatic(): number { return LiteralCounter.answer(); }
    stringMethod(): number { return new IteratorCounter()['Symbol.iterator'](); }
    stringStaticMethod(): number { return IteratorCounter['Symbol.iterator'](); }
    symbolMethod(): number { return new IteratorCounter()[Symbol.iterator](); }
    symbolFieldInitialization(): number { return new SymbolFieldCounter().increment(4); }

    shadowedSymbol(): number {
        const Symbol = { iterator: 'increment' };
        const Counter = class { [Symbol.iterator](): number { return 19; } };
        return new Counter().increment();
    }

    dynamicKey(): number {
        const key = () => 'increment';
        const Counter = class { [key()](): number { return 23; } };
        return new Counter().increment();
    }
}
