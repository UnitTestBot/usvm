package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.util.TsMethodTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Low-level queue and lifecycle contracts; real-source native coverage is checked by the Calls adapter. */
class TsMachineSuspendedStatesTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/StaticOverloads.ts")

    @Test
    fun `step limit emits unfinished states after stopping without running another step`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val lifecycle = mutableListOf<String>()
        var steps = 0
        var terminalStates = 0
        val observer = object : UMachineObserver<TsState> {
            override fun onState(parent: TsState, forks: Sequence<TsState>) {
                steps++
            }

            override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
                terminalStates++
            }

            override fun onMachineStopped() {
                lifecycle += "stopped"
            }

            override fun onStateSuspended(state: TsState) {
                lifecycle += "suspended"
            }
        }
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            stepLimit = 1uL,
            emitRemainingStatesOnStop = true,
        )

        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            machineObserver = observer,
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertEquals(TsAnalysisStopReason.STOPPED, outcome.stopReason)
        assertFalse(outcome.timedOut)
        assertEquals(expected = 1, actual = steps)
        assertEquals(expected = 0, actual = terminalStates)
        assertTrue(outcome.states.isEmpty())
        assertEquals(expected = listOf("stopped", "suspended"), actual = lifecycle)

        val suspended = outcome.suspendedStates.single()
        assertSame(method, suspended.entrypoint)
        assertTrue(suspended.callStack.isNotEmpty())
        assertTrue(suspended.models.isNotEmpty())
        val returnedFromEntryPoint = (suspended.methodResult as? TsMethodResult.Success.RegularCall)?.method == method
        assertFalse(returnedFromEntryPoint)
    }

    @Test
    fun `zero timeout exposes the unexecuted initial state as a suspended input`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        var steps = 0
        val observer = object : UMachineObserver<TsState> {
            override fun onState(parent: TsState, forks: Sequence<TsState>) {
                steps++
            }
        }
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.ZERO,
            emitRemainingStatesOnStop = true,
        )

        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            machineObserver = observer,
        ).use { machine -> machine.analyzeWithOutcome(methods = listOf(method)) }

        assertEquals(TsAnalysisStopReason.STOPPED, outcome.stopReason)
        assertEquals(expected = 0, actual = steps)
        assertTrue(outcome.states.isEmpty())
        assertSame(method, outcome.suspendedStates.single().entrypoint)
        assertTrue(outcome.suspendedStates.single().models.isNotEmpty())
    }

    @Test
    fun `suspended state emission is opt in`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            stepLimit = 1uL,
        )

        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertEquals(TsAnalysisStopReason.STOPPED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.suspendedStates.isEmpty())
    }

    @Test
    fun `path exhaustion keeps terminal witnesses and has no suspended states`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            emitRemainingStatesOnStop = true,
        )

        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
        ).use { machine -> machine.analyzeWithMetadata(methods = listOf(method)) }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isNotEmpty())
        assertTrue(outcome.suspendedStates.isEmpty())
    }
}
