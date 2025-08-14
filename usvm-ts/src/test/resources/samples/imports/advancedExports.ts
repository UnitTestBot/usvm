// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Enum exports
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

// Const assertions and readonly types
export const CONSTANTS = {
    PI: 3.14159,
    MAX_SIZE: 100,
    CONFIG: {
        timeout: 5000,
        retries: 3
    }
} as const;

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

// Class with static methods and properties
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

// Abstract patterns (simulated with inheritance)
export class BaseProcessor {
    protected name: string;

    constructor(name: string) {
        this.name = name;
    }

    process(data: any): any {
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

// Module-level variables
let moduleState = 0;

export function getModuleState(): number {
    return moduleState;
}

export function setModuleState(value: number): void {
    moduleState = value;
}
