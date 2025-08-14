// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

export const exportedNumber: number = 123;
export const exportedString: string = "hello";
export const exportedBoolean: boolean = true;

export function exportedFunction(x: number): number {
    return x * 2;
}

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

export interface ExportedInterface {
    id: number;
    name: string;
}

export type ExportedType = {
    count: number;
    active: boolean;
};

export async function exportedAsyncFunction(delay: number): Promise<number> {
    return new Promise((resolve) => {
        setTimeout(() => resolve(delay * 10), 1);
    });
}
