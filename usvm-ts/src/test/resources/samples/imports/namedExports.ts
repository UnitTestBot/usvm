// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Variable exports
export const exportedNumber: number = 123;
export const exportedString: string = "hello";
export const exportedBoolean: boolean = true;
export const exportedFloat: number = 3.14159;
export const exportedNull = null;
export const exportedUndefined = undefined;
export const exportedArray = [1, 2, 3];
export const exportedObject = { id: 100, name: "test" };
export const exportedNegativeNumber: number = -456;
export const exportedEmptyString: string = "";

// Function export
export function exportedFunction(x: number): number {
    return x * 2;
}

// Class export
export class ExportedClass {
    private readonly value: number;

    constructor(value: number) {
        this.value = value;
    }

    getValue(): number {
        return this.value;
    }

    multiply(factor: number): number {
        return this.value * factor;
    }
}

// Type definitions
export interface ExportedInterface {
    id: number;
    name: string;
}

export type ExportedType = {
    count: number;
    active: boolean;
};

// Async function export
export async function exportedAsyncFunction(delay: number): Promise<number> {
    return new Promise((resolve) => {
        setTimeout(() => resolve(delay * 10), 1);
    });
}
