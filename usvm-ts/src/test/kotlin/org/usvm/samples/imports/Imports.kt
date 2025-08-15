package org.usvm.samples.imports

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
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

    @Test
    fun `test use default import`() {
        val method = getMethod("useDefaultImport")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { message, r -> (message eq "test") && (r eq "test") },
            { message, r -> (message eq "") && (r eq "") },
            { message, r -> (message eq "hello") && (r eq "hello") },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Test
    fun `test use mixed imports`() {
        val method = getMethod("useMixedImports")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 42 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test use renamed imports`() {
        val method = getMethod("useRenamedImports")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 260 }, // computeValue(10) = 110, aliasedValue = 100, instance.value = 50
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test use namespace import`() {
        val method = getMethod("useNamespaceImport")
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
    fun `test chained type operations`() {
        val method = getMethod("chainedTypeOperations")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, y, r -> (x eq 5) && (y eq 3) && (r eq 19) }, // 5*2 + 3*3 = 10 + 9 = 19
            { x, y, r -> (x eq 10) && (y eq 4) && (r eq 32) }, // 10*2 + 4*3 = 20 + 12 = 32
            { x, y, r -> (x eq 0) && (y eq 0) && (r eq 0) },
            invariants = arrayOf(
                { _, _, _ -> true }
            )
        )
    }

    @Test
    fun `test complex chaining`() {
        val method = getMethod("complexChaining")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { input, r -> (input eq 5) && (r eq 220) }, // exportedFunction(5)=10, computeValue(10)=110, +aliasedValue(100)=210, +value(110)=220
            { input, r -> (input eq 0) && (r eq 200) }, // exportedFunction(0)=0, computeValue(0)=100, +aliasedValue(100)=200
            { input, r -> (input eq 10) && (r eq 240) }, // exportedFunction(10)=20, computeValue(20)=120, +aliasedValue(100)=220, +value(120)=240
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

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

    @Test
    fun `test use destructuring`() {
        val method = getMethod("useDestructuring")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 246 },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test conditional import usage`() {
        val method = getMethod("conditionalImportUsage")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { condition, value, r -> (condition eq true) && (value eq 5) && (r eq 615) }, // ExportedClass(5).multiply(123) = 5 * 123 = 615
            { condition, value, r -> (condition eq false) && (value eq 5) && (r eq 105) }, // computeValue(5) = 5 + 100 = 105
            { condition, value, r -> (condition eq true) && (value eq 2) && (r eq 246) }, // ExportedClass(2).multiply(123) = 2 * 123 = 246
            invariants = arrayOf(
                { _, _, _ -> true }
            )
        )
    }

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

    @Test
    fun `test use const imports`() {
        val method = getMethod("useConstImports")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5103.14159 }, // PI(3.14159) + MAX_SIZE(100) + timeout(5000) = 5103.14159
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test use function overloads`() {
        val method = getMethod("useFunctionOverloads")
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

    @Test
    fun `test complex static interactions`() {
        val method = getMethod("complexStaticInteractions")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "Utility v1.0.0, counter: 5" },
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test nested constant access`() {
        val method = getMethod("nestedConstantAccess")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 5003 }, // timeout(5000) + retries(3) = 5003
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }

    @Test
    fun `test process color enum`() {
        val method = getMethod("processColorEnum")
        discoverProperties<TsTestValue.TsString, TsTestValue.TsString>(
            method = method,
            { color, r -> (color eq "red") && (r eq "red-processed") },
            { color, r -> (color eq "green") && (r eq "green-processed") },
            { color, r -> (color eq "blue") && (r eq "blue-processed") },
            invariants = arrayOf(
                { _, _ -> true }
            )
        )
    }

    @Test
    fun `test multiple inheritance levels`() {
        val method = getMethod("multipleInheritanceLevels")
        discoverProperties<TsTestValue.TsString>(
            method = method,
            { r -> r eq "base: test-50" }, // BaseProcessor("base").process("test") + "-" + NumberProcessor().process(10)
            invariants = arrayOf(
                { _ -> true }
            )
        )
    }
}
