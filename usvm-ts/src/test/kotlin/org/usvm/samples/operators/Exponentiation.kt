package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
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
            val tests = analyze(methodName)

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
    fun `concrete Number edge cases replay in Node`() {
        val cases = mapOf(
            "nanToZero" to 1.0,
            "negativeZeroToMinusOne" to Double.NEGATIVE_INFINITY,
            "negativeInfinitySquared" to Double.POSITIVE_INFINITY,
            "negativeOneInfinite" to Double.NaN,
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

            assertTrue(failure.message.orEmpty().contains("Symbolic exponentiation"))
        }
    }

    @Test
    fun `symbolic reciprocal is explicitly unsupported`() {
        val failure = assertFailsWith<UnsupportedOperationException> { analyze("reciprocal") }

        assertTrue(failure.message.orEmpty().contains("exponent -1.0"))
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

    private fun replay(methodName: String, tests: List<TsTest>) {
        val source = javaClass.getResourceAsStream(resource)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing $resource")
        val script = directory.resolve("$methodName.ts")
        val output = directory.resolve("$methodName.out")
        script.writeText(buildString {
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
        })

        val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Node replay timed out")
            assertEquals(0, process.exitValue(), output.readText())
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
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
