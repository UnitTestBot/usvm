package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class InOperatorPresenceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/operators/InOperator.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `in checks field presence independently of field value`() {
        val methods = listOf("hasPresentNumberProperty", "hasUndefinedProperty", "lacksProperty")

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "InOperator")
            val outcome = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isNotEmpty(), methodName)
            val tests = outcome.states.map { state ->
                assertIs<TsMethodResult.Success>(state.methodResult, methodName)
                TsTestResolver().resolve(method, state)
            }
            tests.forEach { test ->
                assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue, methodName).number)
            }

            replay(methodName, tests.map { test ->
                assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
            })
        }
    }

    @Test
    fun `unmodeled keys arrays and deletion stop explicitly`() {
        val methods = listOf(
            "hasSymbolicKey",
            "hasInputProperty",
            "testInOperatorObject",
            "testInOperatorArray",
            "testInOperatorObjectAfterDelete",
        )

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "InOperator")

            assertFailsWith<UnsupportedOperationException>(methodName) {
                TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
                    machine.analyzeWithOutcome(methods = listOf(method))
                }
            }
        }
    }

    private fun replay(methodName: String, values: List<Double>) {
        val source = getResourcePath(tsPath).readText()
        val script = directory.resolve("$methodName.ts")
        val output = directory.resolve("$methodName.out")
        script.writeText(buildString {
            appendLine(source)
            values.forEachIndexed { index, value ->
                appendLine("if (new InOperator().$methodName($value) !== 1) throw Error('state $index');")
            }
        })

        val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Node replay timed out: $methodName")
        assertEquals(0, process.exitValue(), output.readText())
    }

    private val machineOptions = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = Duration.INFINITE,
        throwExceptionOnStepFailure = true,
    )
}
