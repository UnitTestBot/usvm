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
import org.usvm.util.eq
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class InOperatorFieldCollisionTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/operators/InOperatorFieldCollision.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `string field collisions preserve the actual object literal field type`() {
        val methods = listOf("numericFieldWithStringCollision", "writtenStringField", "declaredStringField")

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "Probe")

            discoverProperties<TsTestValue.TsNumber>(
                method = method,
                { result -> result eq 7 },
                invariants = arrayOf({ result -> result eq 7 }),
            )
        }

        val assertions = buildString {
            methods.forEach { methodName ->
                appendLine("if (new Probe().$methodName() !== 7) throw Error('$methodName');")
            }
        }

        replayInOperatorScript(directory, tsPath, scriptName = "string-field-collision", assertions = assertions)
    }

    @Test
    fun `new own field ignores unrelated declared field sort`() {
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            throwExceptionOnStepFailure = true,
        )

        val methods = listOf("run", "declaredProperty", "reassignedProperty")

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "Probe")
            val outcome = TsMachine(scene, options = options, tsOptions = TsOptions()).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), "$methodName: expected a successful state")
            outcome.states.forEach { state ->
                assertIs<TsMethodResult.Success>(state.methodResult, methodName)
                val test = TsTestResolver().resolve(method, state)
                assertEquals(7.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number, methodName)
            }
        }

        val assertions = buildString {
            methods.forEach { methodName ->
                appendLine("if (new Probe().$methodName() !== 7) throw Error('$methodName');")
            }
        }

        replayInOperatorScript(directory, tsPath, scriptName = "field-collision", assertions = assertions)
    }
}
