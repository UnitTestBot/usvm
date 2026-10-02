package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsCatchRoutingTest {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/samples/lang/Exceptions.ts")
    private val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    private val options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        throwExceptionOnStepFailure = true,
    )

    @Test
    fun `catch paths retain the thrown value and replay in Node`() {
        val tests = listOf("conditionalCatch", "caughtValue", "nestedCatch", "catchesCall", "rethrowToOuter")
            .associateWith(::analyze)

        assertEquals(setOf(1, 2), tests.getValue("conditionalCatch").map(::resultNumber).toSet())
        tests.forEach { (name, generated) ->
            generated.forEach { test -> assertExpectedResult(name, test) }
        }

        replay(tests)
    }

    private fun assertExpectedResult(name: String, test: TsTest) {
        val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
        val expected = when (name) {
            "conditionalCatch" -> if (input == 0.0) 2.0 else 1.0
            "caughtValue", "nestedCatch", "catchesCall", "rethrowToOuter" -> input + 1.0
            else -> error("Unexpected method $name")
        }

        assertEquals(expected, assertIs<TsTestValue.TsNumber>(test.returnValue).number, "$name: $test")
    }

    private fun analyze(name: String): List<TsTest> {
        val method = scene.projectClasses.single { it.name == "Exceptions" }
            .methods
            .single { it.name == name }
        val result = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, result.stopReason, name)
        assertTrue(result.unsupportedPaths.isEmpty(), "$name: ${result.unsupportedPaths}")
        assertTrue(result.states.isNotEmpty(), name)
        assertTrue(result.states.all { it.methodResult is TsMethodResult.Success }, name)

        return result.states.map { state -> TsTestResolver().resolve(method, state) }
    }

    private fun resultNumber(test: TsTest): Int =
        assertIs<TsTestValue.TsNumber>(test.returnValue).number.toInt()

    private fun replay(tests: Map<String, List<TsTest>>) {
        val script = buildString {
            appendLine(source.readText())
            tests.forEach { (name, generated) ->
                generated.forEachIndexed { index, test ->
                    val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
                    val expected = assertIs<TsTestValue.TsNumber>(test.returnValue).number

                    appendLine("if (new Exceptions().$name($input) !== $expected) {")
                    appendLine("  throw Error('$name witness $index');")
                    appendLine("}")
                }
            }
        }
        val replay = directory.resolve("catch-routing.ts")
        val output = directory.resolve("catch-routing.out")
        replay.writeText(script)

        val process = ProcessBuilder("node", "--experimental-strip-types", replay.toString())
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
}
