package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisResult
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class Exponentiation : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val resource = "/samples/operators/Exponentiation.ts"

    override val scene: EtsScene = loadScene(resource)

    @Test
    fun `number and boolean fake bases retain their active payload`() {
        for (methodName in listOf("numberOrBooleanBase", "mixedBase")) {
            val method = getMethod(methodName)

            discoverProperties<TsTestValue, TsTestValue.TsNumber>(
                method = method,
                { input, result -> input is TsTestValue.TsNumber && input.number == 3.0 && result.number == 9.0 },
                { input, result -> input is TsTestValue.TsBoolean && input.value && result.number == 1.0 },
                { input, result -> input is TsTestValue.TsBoolean && !input.value && result.number == 0.0 },
                invariants = arrayOf({ input, result ->
                    val number = primitiveNumber(input)
                    sameNumber(result.number, number * number)
                }),
            )

            replay(methodName, analyze(methodName))
        }
    }

    @Test
    fun `number and boolean fake exponents use the supported power cases`() {
        for (methodName in listOf("numberOrBooleanExponent", "mixedExponent")) {
            val method = getMethod(methodName)

            discoverProperties<TsTestValue, TsTestValue.TsNumber>(
                method = method,
                { input, result -> input is TsTestValue.TsNumber && input.number == 2.0 && result.number == 9.0 },
                { input, result -> input is TsTestValue.TsBoolean && input.value && result.number == 3.0 },
                { input, result -> input is TsTestValue.TsBoolean && !input.value && result.number == 1.0 },
            )

            replay(methodName, analyze(methodName))
        }
    }

    @Test
    fun `symbolic exponent selects a supported power under path constraints`() {
        val method = getMethod("symbolicExponent")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, exponent, result -> exponent.number == 0.0 && result.number == 1.0 },
            { input, exponent, result -> exponent.number == 1.0 && sameNumber(input.number, result.number) },
            { input, exponent, result -> exponent.number == 2.0 && sameNumber(input.number * input.number, result.number) },
            { input, exponent, result -> input.number == 9.0 && exponent.number == 0.5 && result.number == 3.0 },
        )

        replay("symbolicExponent", analyze("symbolicExponent"))
    }

    @Test
    fun `fake null and undefined payloads use Number conversion`() {
        val baseMethod = getMethod("mixedBase")

        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = baseMethod,
            { input, result -> input == TsTestValue.TsNull && sameNumber(result.number, expected = 0.0) },
            { input, result -> input == TsTestValue.TsUndefined && result.number.isNaN() },
        )

        val exponentMethod = getMethod("mixedExponent")

        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = exponentMethod,
            { input, result -> input == TsTestValue.TsNull && result.number == 1.0 },
        )

        replay("mixedBase", analyze("mixedBase"))
        replay("mixedExponent", analyze("mixedExponent"))
    }

    @Test
    fun `supported symbolic powers replay in Node`() {
        for (methodName in listOf("square", "squareRoot")) {
            val method = getMethod(methodName)

            if (methodName == "square") {
                discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
                    method = method,
                    { input, result -> input.number == 3.0 && result.number == 9.0 },
                )
            } else {
                discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
                    method = method,
                    { input, result ->
                        input.number.toRawBits() == (-0.0).toRawBits() &&
                            result.number.toRawBits() == 0.0.toRawBits()
                    },
                    { input, result ->
                        input.number == Double.NEGATIVE_INFINITY && result.number == Double.POSITIVE_INFINITY
                    },
                )
            }

            val outcome = analyzeWithDefaultFailureHandling(methodName)
            val tests = outcome.states.map { state -> TsTestResolver().resolve(method, state) }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(tests.isNotEmpty(), "No results for $methodName")
            if (methodName == "square") {
                assertTrue(tests.any { test ->
                    val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
                    val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                    input == 3.0 && result == 9.0
                }, "The generated square(3) witness is missing")
            }
            if (methodName == "squareRoot") {
                assertTrue(tests.any { test ->
                    val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
                    val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                    input.toRawBits() == (-0.0).toRawBits() && result.toRawBits() == 0.0.toRawBits()
                }, "The generated signed-zero witness is missing for $methodName")
                assertTrue(tests.any { test ->
                    val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
                    val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                    input == Double.NEGATIVE_INFINITY && result == Double.POSITIVE_INFINITY
                }, "The generated negative-infinity witness is missing for $methodName")
            }

            replay(methodName, tests)
        }
    }

    @Test
    fun `constant fractional power is evaluated`() {
        val method = getMethod("constantFractional")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result.number == 3.0 },
            invariants = arrayOf({ result -> result.number == 3.0 }),
        )

        val tests = analyze("constantFractional")

        assertTrue(tests.isNotEmpty())
        tests.forEach { test ->
            val result = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            assertEquals(3.0, result)
        }
        replay("constantFractional", tests)
    }

    @Test
    fun `unmodeled concrete fractional power is unsupported and runs in Node`() {
        val outcome = analyzeWithDefaultFailureHandling("unmodeledConcreteFractional")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { "exponent 0.7" in it })

        val source = javaClass.getResourceAsStream(resource)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing $resource")
        val script = buildString {
            appendLine(source)
            appendLine("const actual = new Exponentiation().unmodeledConcreteFractional();")
            appendLine("if (!Object.is(actual, 0.1 ** 0.7)) throw Error('Node replay mismatch');")
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "unmodeledConcreteFractional",
            timeoutMessage = "Node replay timed out",
        )
    }

    @Test
    fun `concrete Number edge cases replay in Node`() {
        val cases = mapOf(
            "nanToZero" to 1.0,
            "negativeZeroToMinusOne" to Double.NEGATIVE_INFINITY,
            "positiveZeroToMinusOne" to Double.POSITIVE_INFINITY,
            "negativeInfinitySquared" to Double.POSITIVE_INFINITY,
            "negativeOneInfinite" to Double.NaN,
            "negativeOneNegativeInfinite" to Double.NaN,
            "negativeFractional" to Double.NaN,
        )

        for ((methodName, expected) in cases) {
            val method = getMethod(methodName)
            val matchesExpected: (TsTestValue.TsNumber) -> Boolean = { result ->
                result.number.toRawBits() == expected.toRawBits() || (result.number.isNaN() && expected.isNaN())
            }
            discoverProperties<TsTestValue.TsNumber>(
                method = method,
                matchesExpected,
                invariants = arrayOf(matchesExpected),
            )

            val tests = analyze(methodName)

            assertTrue(tests.isNotEmpty(), "No result for $methodName")
            tests.forEach { test ->
                val actual = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                assertTrue(
                    actual.toRawBits() == expected.toRawBits() || (actual.isNaN() && expected.isNaN()),
                    "$methodName returned $actual rather than $expected",
                )
            }
            replay(methodName, tests)
        }
    }

    @Test
    fun `unmodeled fixed power is explicit`() {
        val failure = assertFailsWith<UnsupportedOperationException> { analyze("unsupportedFractional") }

        assertTrue(failure.message.orEmpty().contains("exponentiation"))
    }

    @Test
    fun `zero reciprocals are modeled while ordinary reciprocals stay unsupported`() {
        val method = getMethod("reciprocal")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { input, result -> sameNumber(input.number, expected = 0.0) && result.number == Double.POSITIVE_INFINITY },
            { input, result -> sameNumber(input.number, expected = -0.0) && result.number == Double.NEGATIVE_INFINITY },
            invariants = arrayOf({ input, _ -> input.number == 0.0 }),
        )

        val outcome = analyzeWithDefaultFailureHandling("reciprocal")

        assertTrue(outcome.states.isNotEmpty())
        assertTrue(outcome.unsupportedPaths.any { "exponent -1.0" in it })
        replay("reciprocal", analyze("reciprocal"))
    }

    @Test
    fun `unsupported powers remain visible in ordinary analysis outcome`() {
        val cases = mapOf(
            "unsupportedFractional" to "Number exponentiation with exponent 1.5",
            "stringBase" to "outside the supported Number conversion model",
        )

        for ((methodName, expectedReason) in cases) {
            val outcome = analyzeWithDefaultFailureHandling(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isEmpty(), "$methodName yielded a completed state")
            assertTrue(
                outcome.unsupportedPaths.any { reason -> expectedReason in reason },
                "$methodName: ${outcome.unsupportedPaths}",
            )
        }
    }

    @Test
    fun `symbolic powers and fake conversions retain unsupported alternatives`() {
        val cases = mapOf(
            "symbolicExponent" to "Number exponentiation with exponent",
            "mixedBase" to "outside the supported Number conversion model",
            "mixedExponent" to "outside the supported Number conversion model",
        )

        for ((methodName, expectedReason) in cases) {
            val outcome = analyzeWithDefaultFailureHandling(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isNotEmpty(), "$methodName lost supported states")
            assertTrue(outcome.unsupportedPaths.any { expectedReason in it }, "$methodName: ${outcome.unsupportedPaths}")
        }
    }

    @Test
    fun `unsupported string conversion is explicit`() {
        val failure = assertFailsWith<UnsupportedOperationException> { analyze("stringBase") }

        assertTrue(failure.message.orEmpty().contains("outside the supported Number conversion model"))
    }

    private fun analyze(methodName: String): List<TsTest> {
        val method = getMethod(methodName)

        return TsMachine(scene = scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyze(methods = listOf(method)).map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    private fun analyzeWithDefaultFailureHandling(methodName: String): TsAnalysisResult {
        val method = getMethod(methodName)
        val options = machineOptions.copy(throwExceptionOnStepFailure = false)

        return TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }
    }

    private fun replay(methodName: String, tests: List<TsTest>) {
        val source = javaClass.getResourceAsStream(resource)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing $resource")
        val script = buildString {
            appendLine(source)
            tests.forEachIndexed { index, test ->
                val args = test.before.parameters.map { value ->
                    when (value) {
                        is TsTestValue.TsNumber -> jsNumber(value.number)
                        is TsTestValue.TsBoolean -> value.value.toString()
                        TsTestValue.TsNull -> "null"
                        TsTestValue.TsUndefined -> "undefined"
                        else -> error("Unmodeled replay input: $value")
                    }
                }.joinToString()
                val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number
                appendLine(
                    "if (!Object.is(new Exponentiation().$methodName($args), ${jsNumber(expected)})) " +
                        "throw Error('Replay mismatch at result $index');"
                )
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = methodName,
            timeoutMessage = "Node replay timed out",
        )
    }

    private fun jsNumber(value: Double): String = when {
        value.isNaN() -> "NaN"
        value == Double.POSITIVE_INFINITY -> "Infinity"
        value == Double.NEGATIVE_INFINITY -> "-Infinity"
        value == 0.0 && value.toRawBits() == (-0.0).toRawBits() -> "-0"
        else -> value.toString()
    }

    private fun primitiveNumber(value: TsTestValue): Double = when (value) {
        is TsTestValue.TsNumber -> value.number
        is TsTestValue.TsBoolean -> if (value.value) 1.0 else 0.0
        TsTestValue.TsNull -> 0.0
        TsTestValue.TsUndefined -> Double.NaN
        else -> error("Unmodeled Number conversion: $value")
    }

    private fun sameNumber(actual: Double, expected: Double): Boolean =
        actual.toRawBits() == expected.toRawBits() || (actual.isNaN() && expected.isNaN())

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 3_500L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
