// @ts-nocheck
// noinspection JSUnusedGlobalSymbols,JSUnusedLocalSymbols

// Named imports
import {
    exportedNumber,
    exportedString,
    exportedBoolean,
    exportedFunction,
    ExportedClass,
    exportedAsyncFunction,
} from './namedExports';

// Default import
import DefaultExportedClass from './defaultExport';

// Mixed imports (default + named)
import DefaultClass, { namedValue } from './defaultExport';

// Renamed imports
import {
    renamedValue as aliasedValue,
    calculate as computeValue,
    InternalClass as RenamedClass,
} from './mixedExports';

// Namespace import
import * as AllExports from './namedExports';

// Re-exported imports
import { reExportedNumber, AllFromDefault } from './mixedExports';

// Advanced imports
import {
    Color,
    NumberEnum,
    CONSTANTS,
    processValue,
    createArray,
    Utility,
    BaseProcessor,
    NumberProcessor,
    getModuleState,
    setModuleState,
} from './advancedExports';

class Imports {
    // Test named imports - primitives
    getExportedNumber(): number {
        return exportedNumber;
    }

    getExportedString(): string {
        return exportedString;
    }

    getExportedBoolean(): boolean {
        return exportedBoolean;
    }

    // Test imported function
    useImportedFunction(input: number): number {
        return exportedFunction(input);
    }

    // Test imported class
    useImportedClass(value: number): number {
        const instance = new ExportedClass(value);
        return instance.multiply(3);
    }

    // Test default import
    useDefaultImport(message: string): string {
        const instance = new DefaultExportedClass(message);
        return instance.getMessage();
    }

    // Test mixed import (default + named)
    useMixedImports(): number {
        const instance = new DefaultClass();
        instance.setMessage("test");
        return namedValue;
    }

    // Test renamed imports
    useRenamedImports(): number {
        const result = computeValue(10);
        const instance = new RenamedClass();
        return result + aliasedValue + instance.value;
    }

    // Test namespace import
    useNamespaceImport(value: number): number {
        const instance = new AllExports.ExportedClass(value);
        return AllExports.exportedFunction(instance.getValue());
    }

    // Test re-exported values
    useReExportedValues(): number {
        return reExportedNumber + AllFromDefault.namedValue;
    }

    // Test chained imports with type operations
    chainedTypeOperations(x: number, y: number): number {
        const class1 = new ExportedClass(x);
        const class2 = new AllExports.ExportedClass(y);
        return class1.multiply(2) + class2.multiply(3);
    }

    // Test async imported function
    async useAsyncImport(delay: number): Promise<number> {
        const result = await exportedAsyncFunction(delay);
        return result + 5;
    }

    // Test complex chaining with multiple imports
    complexChaining(input: number): number {
        const processed = exportedFunction(input);
        const computed = computeValue(processed);
        const instance = new ExportedClass(computed);
        return instance.getValue() + aliasedValue;
    }

    // Test interface usage (TypeScript interfaces are compile-time only,
    // but we can test objects conforming to the interface)
    useInterfacePattern(id: number, name: string): string {
        const obj: any = { id: id, name: name };
        return `${obj.id}-${obj.name}`;
    }

    // Test type alias usage
    useTypeAlias(count: number, active: boolean): number {
        const obj: any = { count: count, active: active };
        return active ? obj.count * 2 : obj.count;
    }

    // Test destructuring with imports
    useDestructuring(): number {
        const { exportedNumber: num, exportedBoolean: bool } = AllExports;
        return bool ? num * 2 : num;
    }

    // Test conditional import usage
    conditionalImportUsage(condition: boolean, value: number): number {
        if (condition) {
            const instance = new ExportedClass(value);
            return instance.multiply(exportedNumber);
        } else {
            return computeValue(value);
        }
    }

    // Test enum imports
    useEnumImports(): string {
        const color = Color.Red;
        const num = NumberEnum.Second;
        return `${color}-${num}`;
    }

    // Test const object imports
    useConstImports(): number {
        return CONSTANTS.PI + CONSTANTS.MAX_SIZE + CONSTANTS.CONFIG.timeout;
    }

    // Test function overloads
    useFunctionOverloads(input: number): number {
        return processValue(input) as number;
    }

    useFunctionOverloadsString(input: string): string {
        return processValue(input) as string;
    }

    // Test generic functions
    useGenericFunction(): number {
        const numbers = createArray(42, 3);
        return numbers.length * numbers[0];
    }

    // Test static class methods
    useStaticMethods(): number {
        Utility.reset();
        const first = Utility.increment();
        const second = Utility.increment();
        return first + second;
    }

    // Test inheritance patterns
    useInheritance(): number {
        const processor = new NumberProcessor();
        return processor.process(5);
    }

    // Test module state
    useModuleState(): number {
        setModuleState(100);
        return getModuleState();
    }

    // Test chained enum operations
    chainedEnumOperations(): number {
        const colors = [Color.Red, Color.Green, Color.Blue];
        const numbers = [NumberEnum.First, NumberEnum.Second, NumberEnum.Third];
        return colors.length + numbers.reduce((sum, num) => sum + num, 0);
    }

    // Test mixed type operations with imports
    mixedTypeOperations(count: number): any[] {
        const arr = createArray(Color.Red, count);
        const processor = new BaseProcessor("test");
        return arr.map(item => processor.process(item));
    }

    // Test complex static interactions
    complexStaticInteractions(): string {
        Utility.reset();
        for (let i = 0; i < 5; i++) {
            Utility.increment();
        }
        return Utility.getInfo();
    }

    // Test nested constant access
    nestedConstantAccess(): number {
        const config = CONSTANTS.CONFIG;
        return config.timeout + config.retries;
    }

    // Test enum as parameter
    processColorEnum(color: Color): string {
        switch (color) {
            case Color.Red:
                return "red-processed";
            case Color.Green:
                return "green-processed";
            case Color.Blue:
                return "blue-processed";
            default:
                return "unknown";
        }
    }

    // Test multiple inheritance levels
    multipleInheritanceLevels(): string {
        const base = new BaseProcessor("base");
        const number = new NumberProcessor();
        return base.process("test") + "-" + number.process(10);
    }
}
