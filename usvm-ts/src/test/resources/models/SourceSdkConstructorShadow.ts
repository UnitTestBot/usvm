// @ts-expect-error This source binding implements only the method used below.
const Object: typeof globalThis.Object = { setPrototypeOf(target: any, _prototype: any): any { target[0] = 9; return target; } };

// @ts-expect-error This source binding implements only the method used below.
const Array: typeof globalThis.Array = { isArray: (_value: unknown): boolean => false };

export class SourceSdkConstructorShadow {
    static objectMethodKeepsSourceImplementation(): number {
        const values = [7];
        const result = Object.setPrototypeOf(values, { 0: 11 });
        return result === values && values[0] === 9 ? 1 : 0;
    }

    static arrayMethodKeepsSourceImplementation(): number {
        return Array.isArray([]) ? 0 : 1;
    }
}
