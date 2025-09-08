// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Internal variables for export
const internalValue = 100;
const internalString = "mixed";
const internalBoolean = true;
const internalArray = [10, 20, 30];
const internalObject = { count: 5, active: true };

// Internal complex symbols
function internalFunction(x: number): number {
    return x + internalValue;
}

class InternalClass {
    value: number = 50;
}

// Renamed exports of variables
export {
    internalValue as renamedValue,
    internalString as renamedString,
    internalBoolean as renamedBoolean,
    internalArray as renamedArray,
    internalObject as renamedObject,
};

// Renamed exports of complex symbols
export { internalFunction as calculate };
export { InternalClass as ExportedClass };

// Re-exports from other modules
export {
    exportedNumber as reExportedNumber,
    exportedString as reExportedString,
} from './namedExports';

// Namespace re-export
export * as AllFromDefault from './defaultExport';
