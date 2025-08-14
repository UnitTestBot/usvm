// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

const internalValue = 100;

function internalFunction(x: number): number {
    return x + internalValue;
}

class InternalClass {
    value: number = 50;
}

// Mixed export styles
export { internalValue as renamedValue, internalFunction as calculate };
export { InternalClass };

// Re-export from another module
export { exportedNumber as reExportedNumber } from './namedExports';

// Export all from another module
export * as AllFromDefault from './defaultExport';
