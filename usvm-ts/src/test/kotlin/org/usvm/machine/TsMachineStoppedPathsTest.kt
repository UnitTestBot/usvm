package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.util.TsMethodTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Prefix collection contracts, including a snapshot before the unknown-call policy prunes it. */
class TsMachineStoppedPathsTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/arrays/ArrayMethods.ts")

    private fun options(emit: Boolean) = UMachineOptions(
        solverType = SolverType.YICES,
        stopOnCoverage = 0,
        timeout = Duration.INFINITE,
        emitRemainingStatesOnStop = emit,
    )

    @Test
    fun `unknown push retains its feasible input prefix without claiming a returned result`() {
        val method = getMethod(methodName = "arrayPush", className = "ArrayMethods")
        val tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()))

        val outcome = TsMachine(
            scene = scene,
            options = options(emit = true),
            tsOptions = tsOptions,
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedCall)
        assertFalse(outcome.engineFailed)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.suspendedStates.isNotEmpty())
        assertTrue(outcome.suspendedStates.all { it.models.isNotEmpty() })
        assertTrue(outcome.suspendedStates.all { it.callStack.isNotEmpty() })
        assertTrue(outcome.suspendedStates.all { it.unsupportedReason?.contains("push") == true })
    }

    @Test
    fun `stopped path collection stays disabled by default`() {
        val method = getMethod(methodName = "arrayPush", className = "ArrayMethods")
        val tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()))

        val outcome = TsMachine(
            scene = scene,
            options = options(emit = false),
            tsOptions = tsOptions,
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertTrue(outcome.unsupportedCall)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.suspendedStates.isEmpty())
    }

    @Test
    fun `a runtime conversion limit preserves the reached input instead of a terminal witness`() {
        val stringScene = loadScene("/samples/lang/Strings.ts")
        val owner = stringScene.projectAndSdkClasses.single { it.name == "Strings" }
        val method = owner.methods.single { it.name == "stringWithNumber" }

        val outcome = TsMachine(
            scene = stringScene,
            options = options(emit = true),
            tsOptions = TsOptions(),
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertTrue(outcome.runtimeLimited)
        assertTrue(outcome.unsupportedPaths.any { it.contains("STRING_CONCAT_OPERAND_CONVERSION") })
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.suspendedStates.isNotEmpty())
        assertTrue(outcome.suspendedStates.all { it.models.isNotEmpty() })
        assertTrue(outcome.suspendedStates.all { it.unsupportedReason != null })
    }

    @Test
    fun `prefix emission errors remain analysis errors instead of silently lost candidates`() {
        val method = getMethod(methodName = "arrayPush", className = "ArrayMethods")
        val observer = object : UMachineObserver<TsState> {
            override fun onStateSuspended(state: TsState) {
                error("candidate sink failed")
            }
        }
        val tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()))

        val failure = assertFailsWith<IllegalStateException> {
            TsMachine(
                scene = scene,
                options = options(emit = true),
                tsOptions = tsOptions,
                machineObserver = observer,
            ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }
        }

        assertEquals(expected = "candidate sink failed", actual = failure.message)
    }
}
