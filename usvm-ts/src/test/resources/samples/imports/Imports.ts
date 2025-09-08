// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols

import {
    exportedNumber,
    exportedString,
    exportedBoolean,
    exportedNull,
    exportedUndefined,
    exportedArray,
    exportedObject,
    exportedFloat,
    exportedNegativeNumber,
    exportedEmptyString,
    exportedFunction,
    ExportedClass,
    exportedAsyncFunction,
} from './namedExports';

import defaultValue from './defaultExport';

import {
    renamedValue as aliasedValue,
    renamedString as aliasedString,
    renamedBoolean as aliasedBoolean,
    renamedArray as aliasedArray,
    renamedObject as aliasedObject,
    calculate as computeValue,
    ExportedClass as RenamedClass,
} from './mixedExports';

import * as AllExports from './namedExports';

import {
    reExportedNumber,
} from './mixedExports';

import {
    CONSTANTS,
    computedNumber,
    configString,
    Color,
    NumberEnum,
    processValue,
    createArray,
    Utility,
    NumberProcessor,
    getModuleState,
    setModuleState,
} from './advancedExports';

class Imports {
    getExportedNumber(): number {
        return exportedNumber;
    }

    getExportedString(): string {
        return exportedString;
    }

    getExportedBoolean(): boolean {
        return exportedBoolean;
    }

    getExportedNull(): null {
        return exportedNull;
    }

    getExportedUndefined(): undefined {
        return exportedUndefined;
    }

    getExportedArray(): number[] {
        return exportedArray;
    }

    getExportedObject(): object {
        return exportedObject;
    }

    getExportedFloat(): number {
        return exportedFloat;
    }

    getExportedNegativeNumber(): number {
        return exportedNegativeNumber;
    }

    getExportedEmptyString(): string {
        return exportedEmptyString;
    }

    getDefaultValue(): string {
        return defaultValue;
    }

    getRenamedValue(): number {
        return aliasedValue;
    }

    getRenamedString(): string {
        return aliasedString;
    }

    getRenamedBoolean(): boolean {
        return aliasedBoolean;
    }

    getRenamedArray(): number[] {
        return aliasedArray;
    }

    getRenamedObject(): object {
        return aliasedObject;
    }

    useNamespaceVariables(): number {
        return AllExports.exportedNumber + AllExports.exportedFloat;
    }

    useReExportedValues(): number {
        return reExportedNumber;
    }

    getComputedNumber(): number {
        return computedNumber;
    }

    getConfigString(): string {
        return configString;
    }

    useConstImports(): number {
        return CONSTANTS.PI + CONSTANTS.MAX_SIZE + CONSTANTS.CONFIG.timeout;
    }

    useDestructuring(): number {
        const { exportedNumber: num, exportedBoolean: bool } = AllExports;
        return bool ? num * 2 : num;
    }

    combineVariables(): string {
        return `${exportedString}-${aliasedString}-${configString}`;
    }

    mathOperationsOnVariables(): number {
        return exportedNumber + aliasedValue + computedNumber;
    }

    useImportedFunction(input: number): number {
        return exportedFunction(input);
    }

    useImportedClass(value: number): number {
        const instance = new ExportedClass(value);
        return instance.multiply(3);
    }

    useRenamedComplexImports(): number {
        const result = computeValue(10);
        const instance = new RenamedClass();
        return result + instance.value;
    }

    useNamespaceComplexImport(value: number): number {
        const instance = new AllExports.ExportedClass(value);
        return AllExports.exportedFunction(instance.getValue());
    }

    async useAsyncImport(delay: number): Promise<number> {
        const result = await exportedAsyncFunction(delay);
        return result + 5;
    }

    useEnumImports(): string {
        const color = Color.Red;
        const num = NumberEnum.Second;
        return `${color}-${num}`;
    }

    useFunctionOverloadsNumber(input: number): number {
        return processValue(input);
    }

    useFunctionOverloadsString(input: string): string {
        return processValue(input);
    }

    useGenericFunction(): number {
        const numbers = createArray(42, 3);
        return numbers.length * numbers[0];
    }

    useStaticMethods(): number {
        Utility.reset();
        const first = Utility.increment();
        const second = Utility.increment();
        return first + second;
    }

    useInheritance(): number {
        const processor = new NumberProcessor();
        return processor.process(5);
    }

    useModuleState(): number {
        setModuleState(100);
        return getModuleState();
    }

    chainedEnumOperations(): number {
        const colors = [Color.Red, Color.Green, Color.Blue];
        const numbers = [NumberEnum.First, NumberEnum.Second, NumberEnum.Third];
        return colors.length + numbers.reduce((sum, num) => sum + num, 0);
    }

    useInterfacePattern(id: number, name: string): string {
        const obj = { id: id, name: name };
        return `${obj.id}-${obj.name}`;
    }

    useTypeAlias(count: number, active: boolean): number {
        const obj = { count: count, active: active };
        return active ? obj.count * 2 : obj.count;
    }
}
