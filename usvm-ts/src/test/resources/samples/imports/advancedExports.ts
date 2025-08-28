// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Const object with nested values
export const CONSTANTS = {
    PI: 3.14159,
    MAX_SIZE: 100,
    CONFIG: {
        timeout: 5000,
        retries: 3,
    },
} // as const;

// Computed variable exports
export const computedNumber = CONSTANTS.PI * CONSTANTS.MAX_SIZE;
export const configString = `timeout:${CONSTANTS.CONFIG.timeout}ms`;

// Enum definitions
export enum Color {
    Red = "red",
    Green = "green",
    Blue = "blue"
}

export enum NumberEnum {
    First = 1,
    Second = 2,
    Third = 3
}

// Function overloads
export function processValue(value: number): number;
export function processValue(value: string): string;
export function processValue(value: number | string): number | string {
    if (typeof value === "number") {
        return value * 2;
    }
    return value.toUpperCase();
}

// Generic function
export function createArray<T>(item: T, count: number): T[] {
    return new Array(count).fill(item);
}

// Class with static methods
export class Utility {
    static readonly VERSION = "1.0.0";
    static counter = 0;

    static increment(): number {
        return ++this.counter;
    }

    static reset(): void {
        this.counter = 0;
    }

    static getInfo(): string {
        return `Utility v${this.VERSION}, counter: ${this.counter}`;
    }
}

// Inheritance classes
export class BaseProcessor {
    protected name: string;

    constructor(name: string) {
        this.name = name;
    }

    process(data: any): string {
        return `${this.name}: ${data}`;
    }
}

export class NumberProcessor extends BaseProcessor {
    constructor() {
        super("NumberProcessor");
    }

    process(data: number): number {
        return data * 10;
    }
}

// Module state functions
let moduleState = 0;

export function getModuleState(): number {
    return moduleState;
}

export function setModuleState(value: number): void {
    moduleState = value;
}
