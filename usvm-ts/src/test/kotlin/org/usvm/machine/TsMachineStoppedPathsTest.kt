package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.state.TsState
import org.usvm.util.TsMethodTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Prefix collection contracts, including a snapshot before the unknown-call policy prunes it. */
class TsMachineStoppedPathsTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/arrays/ArrayMethods.ts")

    private fun options() = UMachineOptions(
        solverType = SolverType.YICES,
        stopOnCoverage = 0,
        timeout = Duration.INFINITE,
    )

    @Test
    fun `unknown push retains its feasible input prefix without claiming a returned result`() {
        val method = getMethod(methodName = "arrayPush", className = "ArrayMethods")
        val tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()))

        val suspendedStates = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { suspendedStates += it }

        val outcome = TsMachine(
            scene = scene,
            options = options(),
            tsOptions = tsOptions,
            observer = stoppedObserver,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine ->
            val result = machine.analyzeWithMetadata(methods = listOf(method))
            stoppedObserver.collectRemainingStates()
            result
        }

        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = outcome.stopReason)
        assertTrue(outcome.unsupportedCall)
        assertFalse(outcome.engineFailed)
        assertTrue(outcome.states.isEmpty())
        assertTrue(suspendedStates.isNotEmpty())
        assertTrue(suspendedStates.all { it.models.isNotEmpty() })
        assertTrue(suspendedStates.all { it.callStack.isNotEmpty() })
        assertTrue(suspendedStates.all { it.unsupportedReason?.contains("push") == true })
    }

    @Test
    fun `unknown call without a stopped state observer retains ordinary witness behavior`() {
        val method = getMethod(methodName = "arrayPush", className = "ArrayMethods")
        val tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()))

        val outcome = TsMachine(
            scene = scene,
            options = options(),
            tsOptions = tsOptions,
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertTrue(outcome.unsupportedCall)
        assertTrue(outcome.states.isEmpty())
    }

    @Test
    fun `a runtime conversion limit preserves the reached input instead of a terminal witness`() {
        val stringScene = loadScene("/samples/lang/Strings.ts")
        val owner = stringScene.projectAndSdkClasses.single { it.name == "Strings" }
        val method = owner.methods.single { it.name == "stringWithNumber" }

        val suspendedStates = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { suspendedStates += it }

        val outcome = TsMachine(
            scene = stringScene,
            options = options(),
            tsOptions = TsOptions(),
            observer = stoppedObserver,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine ->
            val result = machine.analyzeWithMetadata(methods = listOf(method))
            stoppedObserver.collectRemainingStates()
            result
        }

        assertTrue(outcome.runtimeLimited)
        assertTrue(outcome.unsupportedPaths.any { it.contains("STRING_CONCAT_OPERAND_CONVERSION") })
        assertTrue(outcome.states.isEmpty())
        assertTrue(suspendedStates.isNotEmpty())
        assertTrue(suspendedStates.all { it.models.isNotEmpty() })
        assertTrue(suspendedStates.all { it.unsupportedReason != null })
    }

    @Test
    fun `prefix emission errors remain analysis errors instead of silently lost candidates`() {
        val method = getMethod(methodName = "arrayPush", className = "ArrayMethods")
        val stoppedObserver = TsStoppedStatesObserver { error("candidate sink failed") }
        val tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet()))

        val failure = assertFailsWith<IllegalStateException> {
            TsMachine(
                scene = scene,
                options = options(),
                tsOptions = tsOptions,
                observer = stoppedObserver,
                machineObserverFactory = stoppedObserver::createMachineObserver,
            ).use { machine ->
                machine.analyzeWithMetadata(methods = listOf(method))
                stoppedObserver.collectRemainingStates()
            }
        }

        assertEquals(expected = "candidate sink failed", actual = failure.message)
    }
}
