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
    fun `supported symbolic powers replay in Node`() {
        for (methodName in listOf("square", "squareRoot")) {
            val outcome = analyzeWithDefaultFailureHandling(methodName)
            val method = getMethod(methodName)
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
    fun `unsupported symbolic powers are explicit`() {
        for (methodName in listOf("symbolicExponent", "unsupportedFractional")) {
            val failure = assertFailsWith<UnsupportedOperationException> { analyze(methodName) }

            assertTrue(failure.message.orEmpty().contains("exponentiation"))
        }
    }

    @Test
    fun `symbolic reciprocal is explicitly unsupported`() {
        val failure = assertFailsWith<UnsupportedOperationException> { analyze("reciprocal") }

        assertTrue(failure.message.orEmpty().contains("exponent -1.0"))
    }

    @Test
    fun `unsupported powers remain visible in ordinary analysis outcome`() {
        val cases = mapOf(
            "symbolicExponent" to "Symbolic exponentiation exponent",
            "unsupportedFractional" to "Number exponentiation with exponent 1.5",
            "reciprocal" to "Number exponentiation with exponent -1.0",
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
                    val number = assertIs<TsTestValue.TsNumber>(value).number
                    jsNumber(number)
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
