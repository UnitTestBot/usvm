package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundedStringToNumberTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    override val scene = loadScene("/models/BoundedStringToNumber.ts")
    override val tsOptions = TsOptions(maxArraySize = 16)
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            machine.analyze(methods = listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `literal string numeric syntax follows ECMAScript rather than Java parsing extensions`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method(name = "literalForms"),
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 1.0 }),
        )
    }

    @Test
    fun `bounded symbolic decimal integers preserve trimming negative zero NaN and exact fifteen digits`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method(name = "selectedIntegers"),
            { value, result -> value.value == "12" && result.number == 12.0 },
            { value, result -> value.value == " -0 " && result.number == Double.NEGATIVE_INFINITY },
            { value, result -> value.value == "+" && result.number.isNaN() },
            { value, result -> value.value == "999999999999999" && result.number == 999_999_999_999_999.0 },
            invariants = arrayOf({ value, result ->
                when (value.value) {
                    "12" -> result.number == 12.0
                    " -0 " -> result.number == Double.NEGATIVE_INFINITY
                    "+" -> result.number.isNaN()
                    "999999999999999" -> result.number == 999_999_999_999_999.0
                    else -> result.number == 0.0
                }
            }),
        )
    }

    @Test
    fun `unary plus uses the same bounded integer conversion`() {
        discoverProperties<TsTestValue.TsString, TsTestValue.TsNumber>(
            method = method(name = "unaryInteger"),
            { value, result -> value.value == "-12" && result.number == -12.0 },
            invariants = arrayOf({ value, result -> result.number == if (value.value == "-12") -12.0 else 0.0 }),
        )
    }

    @Test
    fun `unmodeled symbolic formats retain unsupported prefixes and ordinary siblings`() {
        for (name in listOf("unsupportedFraction", "unsupportedPrecision")) {
            val method = method(name = name)
            val machineOptions = options.copy(throwExceptionOnStepFailure = false)
            val outcome = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
                machine.analyzeWithMetadata(methods = listOf(method))
            }
            val tests = outcome.states.map { TsTestResolver().resolve(method, it) }

            assertTrue(outcome.unsupportedPaths.any { "Numeric conversion" in it }, name)
            assertFalse(outcome.engineFailed, name)
            assertTrue(tests.isNotEmpty(), name)
            assertTrue(tests.all { (it.returnValue as TsTestValue.TsNumber).number == 0.0 }, name)
        }
    }

    @Test
    fun `native JavaScript confirms literal and selected symbolic values`() {
        val source = getResourcePath("/models/BoundedStringToNumber.ts").readText()
        val checks = """
            if (BoundedStringToNumber.literalForms() !== 1) throw Error("literal syntax");
            for (const value of ["12", " -0 ", "+", "999999999999999", "", "else"]) {
                const expected = ["12", " -0 ", "+", "999999999999999"].includes(value)
                    ? (value === " -0 " ? 1 / Number(value) : Number(value)) : 0;
                if (!Object.is(BoundedStringToNumber.selectedIntegers(value), expected)) throw Error(value);
            }
            if (BoundedStringToNumber.unaryInteger("-12") !== -12) throw Error("unary plus");
            if (BoundedStringToNumber.unsupportedFraction("1.5") !== 1.5) throw Error("fraction boundary");
            if (BoundedStringToNumber.unsupportedPrecision("9007199254740993") !== 9007199254740992) {
                throw Error("precision boundary");
            }
        """.trimIndent()

        assertNodeReplay(
            source = source + checks,
            directory = directory,
            name = "bounded-string-to-number",
            timeoutMessage = "Bounded string conversion replay timed out",
        )
    }

    @Test
    fun `literal parser rounds decimal and radix values at binary64 precision`() {
        assertEquals(9_007_199_254_740_992.0, ecmaScriptStringToNumber("9007199254740993"))
        assertEquals(9_007_199_254_740_992.0, ecmaScriptStringToNumber("0x20000000000001"))
        assertEquals((-0.0).toRawBits(), ecmaScriptStringToNumber("-0").toRawBits())
        assertTrue(ecmaScriptStringToNumber("\u180e1").isNaN())
    }

    private fun method(name: String) = getMethod(methodName = name, className = "BoundedStringToNumber")
}
