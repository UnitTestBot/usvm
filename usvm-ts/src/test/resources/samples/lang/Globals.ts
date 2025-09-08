let myValue = 42;

class Globals {
    getValue(): number {
        return myValue;
    }

    setValue(value: number): number {
        myValue = value;
        if (value != value) return myValue;
        if (value == 0) return myValue;
        return myValue;
    }

    useValue(): number {
        const x = this.getValue(); // 42
        this.setValue(100);
        const y = this.getValue(); // 100
        return x + y; // 42 + 100 = 142
    }
}
