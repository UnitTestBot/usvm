// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

class Boolean {
    private readonly value: boolean;

    constructor(value: any) {
        this.value = Boolean(value);
    }

    valueOf(): boolean {
        return this.value;
    }
}

class Number {
    private readonly value: number;

    constructor(value: any) {
        this.value = Number(value);
    }

    valueOf(): number {
        return this.value;
    }
}
