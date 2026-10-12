// @ts-expect-error A source binding may expose only the method used by this program.
const Array: typeof globalThis.Array = { isArray: (_value: unknown): boolean => false };

export function globalShadowKeepsItsImplementation(): boolean {
    return Array.isArray([]);
}
