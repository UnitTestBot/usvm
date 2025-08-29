package org.usvm.samples.imports

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.getResourcePath

class Imports : TsMethodTestRunner() {
    private val tsPath = "/samples/imports"

    override val scene: EtsScene = run {
        val path = getResourcePath(tsPath)
        loadEtsProjectAutoConvert(path, useArkAnalyzerTypeInference = null)
    }

    @Test
    fun `test get exported number`() {
        val method = getMethod("getExportedNumber")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 123 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get exported string`() {
        val method = getMethod("getExportedString")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "hello" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get exported boolean`() {
        val method = getMethod("getExportedBoolean")
        discoverProperties<TsTestValue.TsBoolean>(
            method = method,
            { r -> r.value },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get exported null`() {
        val method = getMethod("getExportedNull")
        discoverProperties<TsTestValue.TsNull>(
            method = method,
            { r -> r == TsTestValue.TsNull },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get exported undefined`() {
        val method = getMethod("getExportedUndefined")
        discoverProperties<TsTestValue.TsUndefined>(
            method = method,
            { r -> r == TsTestValue.TsUndefined },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get exported float`() {
        val method = getMethod("getExportedFloat")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 3.14159 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get exported negative number`() {
        val method = getMethod("getExportedNegativeNumber")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq -456 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get exported empty string`() {
        val method = getMethod("getExportedEmptyString")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get default value`() {
        val method = getMethod("getDefaultValue")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "default-string" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get renamed value`() {
        val method = getMethod("getRenamedValue")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 100 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get renamed string`() {
        val method = getMethod("getRenamedString")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "mixed" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get renamed boolean`() {
        val method = getMethod("getRenamedBoolean")
        discoverProperties<TsTestValue.TsBoolean>(
            method = method,
            { r -> r.value }, // true
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Star imports are not yet supported")
    @Test
    fun `test use namespace variables`() {
        val method = getMethod("useNamespaceVariables")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 126.14159 }, // 123 + 3.14159
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Re-exporting is not yet supported")
    @Test
    fun `test use re-exported values`() {
        val method = getMethod("useReExportedValues")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 165 }, // reExportedNumber (123) + AllFromDefault.namedValue (42)
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test get computed number`() {
        val method = getMethod("getComputedNumber")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 314.159 }, // PI * MAX_SIZE = 3.14159 * 100
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("String operations are not yet supported")
    @Test
    fun `test get config string`() {
        val method = getMethod("getConfigString")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "timeout:5000ms" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test use const imports`() {
        val method = getMethod("useConstImports")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5103.14159 }, // PI(3.14159) + MAX_SIZE(100) + timeout(5000)
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Star imports are not yet supported")
    @Test
    fun `test use destructuring`() {
        val method = getMethod("useDestructuring")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 246 }, // bool ? num * 2 : num -> true ? 123 * 2 : 123 = 246
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("String operations are not yet supported")
    @Test
    fun `test combine variables`() {
        val method = getMethod("combineVariables")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "hello-named-export-mixed-timeout:5000ms" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test math operations on variables`() {
        val method = getMethod("mathOperationsOnVariables")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 537.159 }, // 123 + 100 + 314.159
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Imported functions are not supported yet")
    @Test
    fun `test use imported function`() {
        val method = getMethod("useImportedFunction")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { input, r -> (input eq 5) && (r eq 10) },
            { input, r -> (input eq 0) && (r eq 0) },
            { input, r -> (input eq -3) && (r eq -6) },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Disabled("Imported classes are not supported yet")
    @Test
    fun `test use imported class`() {
        val method = getMethod("useImportedClass")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { value, r -> (value eq 10) && (r eq 30) },
            { value, r -> (value eq 5) && (r eq 15) },
            { value, r -> (value eq 0) && (r eq 0) },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Disabled("Imported functions and classes are not supported yet")
    @Test
    fun `test use renamed complex imports`() {
        val method = getMethod("useRenamedComplexImports")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 160 }, // computeValue(10) = 110, + instance.value = 50
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Namespace imports with functions/classes are not supported yet")
    @Test
    fun `test use namespace complex import`() {
        val method = getMethod("useNamespaceComplexImport")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { value, r -> (value eq 10) && (r eq 20) },
            { value, r -> (value eq 5) && (r eq 10) },
            { value, r -> (value eq 0) && (r eq 0) },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Disabled("Async functions are not supported yet")
    @Test
    fun `test use async import`() {
        val method = getMethod("useAsyncImport")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { delay, r -> (delay eq 10) && (r eq 105) },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Disabled("Enums are not supported yet")
    @Test
    fun `test use enum imports`() {
        val method = getMethod("useEnumImports")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "red-2" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Function overloads are not supported yet")
    @Test
    fun `test use function overloads number`() {
        val method = getMethod("useFunctionOverloadsNumber")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { input, r -> (input eq 5) && (r eq 10) },
            { input, r -> (input eq 0) && (r eq 0) },
            { input, r -> (input eq 10) && (r eq 20) },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Disabled("Function overloads are not supported yet")
    @Test
    fun `test use function overloads string`() {
        val method = getMethod("useFunctionOverloadsString")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { input, r -> (input eq "hello") && (r eq "HELLO") },
            { input, r -> (input eq "test") && (r eq "TEST") },
            { input, r -> (input eq "") && (r eq "") },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Disabled("Generic functions are not supported yet")
    @Test
    fun `test use generic function`() {
        val method = getMethod("useGenericFunction")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 126 }, // createArray(42, 3) -> length(3) * value(42) = 126
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Static class methods are not supported yet")
    @Test
    fun `test use static methods`() {
        val method = getMethod("useStaticMethods")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 3 }, // reset(), increment() = 1, increment() = 2, return 1 + 2 = 3
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Class inheritance is not supported yet")
    @Test
    fun `test use inheritance`() {
        val method = getMethod("useInheritance")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 50 }, // NumberProcessor.process(5) = 5 * 10 = 50
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test use module state`() {
        val method = getMethod("useModuleState")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 100 }, // setModuleState(100), getModuleState() = 100
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Enum operations are not supported yet")
    @Test
    fun `test chained enum operations`() {
        val method = getMethod("chainedEnumOperations")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 9 }, // colors.length(3) + numbers.reduce(1+2+3=6) = 3 + 6 = 9
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Disabled("Interface patterns are not supported yet")
    @Test
    fun `test use interface pattern`() {
        val method = getMethod("useInterfacePattern")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { id, name, r -> (id eq 1) && (name eq "test") && (r eq "1-test") },
            { id, name, r -> (id eq 42) && (name eq "hello") && (r eq "42-hello") },
            { id, name, r -> (id eq 0) && (name eq "") && (r eq "0-") },
            invariants = arrayOf(
                { _, _, _ -> true }
            )
        )
    }

    @Disabled("Type aliases are not supported yet")
    @Test
    fun `test use type alias`() {
        val method = getMethod("useTypeAlias")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { count, active, r -> (count eq 10) && (active eq true) && (r eq 20) },
            { count, active, r -> (count eq 10) && (active eq false) && (r eq 10) },
            { count, active, r -> (count eq 5) && (active eq true) && (r eq 10) },
            invariants = arrayOf(
                { _, _, _ -> true }
            )
        )
    }
}
