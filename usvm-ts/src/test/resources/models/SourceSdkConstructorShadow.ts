// @ts-expect-error This source binding implements only the method used below.
const Object: typeof globalThis.Object = { setPrototypeOf(target: any, _prototype: any): any { target[0] = 9; return target; } };

// @ts-expect-error This source binding implements only the method used below.
const Array: typeof globalThis.Array = { isArray: (_value: unknown): boolean => false };

// @ts-expect-error This source binding deliberately uses a numeric property key.
const Symbol: typeof globalThis.Symbol = { iterator: 0 };

export class SourceSdkConstructorShadow {
    static objectMethodKeepsSourceImplementation(): number {
        const values = [7];
        const result = Object.setPrototypeOf(values, { 0: 11 });
        return result === values && values[0] === 9 ? 1 : 0;
    }

    static arrayMethodKeepsSourceImplementation(): number {
        return Array.isArray([]) ? 0 : 1;
    }

    static symbolKeyKeepsSourceImplementation(): number {
        const values = [7];
        values[Symbol.iterator] = 11;
        return values[0] === 11 ? 1 : 0;
    }
}
