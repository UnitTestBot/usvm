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
import kotlin.test.assertFailsWith
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
        }
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            stepLimit = 1uL,
        )
        val suspendedStates = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { state ->
            suspendedStates += state
            lifecycle += "suspended"
        }

        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            machineObserver = observer,
            observer = stoppedObserver,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine ->
            val result = machine.analyzeWithMetadata(methods = listOf(method))
            assertTrue(suspendedStates.isEmpty())
            stoppedObserver.collectRemainingStates()
            result
        }

        assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = outcome.stopReason)
        assertFalse(outcome.timedOut)
        assertEquals(expected = 1, actual = steps)
        assertEquals(expected = 0, actual = terminalStates)
        assertTrue(outcome.states.isEmpty())
        assertEquals(expected = listOf("stopped", "suspended"), actual = lifecycle)

        val suspended = suspendedStates.single()
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
        )

        val suspendedStates = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { suspendedStates += it }

        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            machineObserver = observer,
            observer = stoppedObserver,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine ->
            val result = machine.analyzeWithOutcome(methods = listOf(method))
            stoppedObserver.collectRemainingStates()
            result
        }

        assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = outcome.stopReason)
        assertEquals(expected = 0, actual = steps)
        assertTrue(outcome.states.isEmpty())
        assertSame(method, suspendedStates.single().entrypoint)
        assertTrue(suspendedStates.single().models.isNotEmpty())
    }

    @Test
    fun `analysis without a stopped state observer retains ordinary witness behavior`() {
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

        assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
    }

    @Test
    fun `path exhaustion keeps terminal witnesses and has no suspended states`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val suspendedStates = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { suspendedStates += it }

        val outcome = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            observer = stoppedObserver,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine ->
            val result = machine.analyzeWithMetadata(methods = listOf(method))
            stoppedObserver.collectRemainingStates()
            result
        }

        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = outcome.stopReason)
        assertTrue(outcome.states.isNotEmpty())
        assertTrue(suspendedStates.isEmpty())
    }

    @Test
    fun `exceptional machine stop does not emit queued inputs`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val suspendedStates = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { suspendedStates += it }
        val observer = object : UMachineObserver<TsState> {
            override fun onStatePeeked(state: TsState) {
                error("machine step observer failed")
            }
        }
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val failure = assertFailsWith<IllegalStateException> {
            TsMachine(
                scene = scene,
                options = options,
                tsOptions = TsOptions(),
                machineObserver = observer,
                observer = stoppedObserver,
                machineObserverFactory = stoppedObserver::createMachineObserver,
            ).use { machine ->
                machine.analyzeWithMetadata(methods = listOf(method))
                stoppedObserver.collectRemainingStates()
            }
        }

        assertEquals(expected = "machine step observer failed", actual = failure.message)
        assertTrue(suspendedStates.isEmpty())
    }

    @Test
    fun `queued input sink failure remains visible after a timeout`() {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val stoppedObserver = TsStoppedStatesObserver { error("queued input sink failed") }
        val options = UMachineOptions(
            solverType = SolverType.YICES,
            stopOnCoverage = 0,
            timeout = Duration.ZERO,
        )

        val failure = assertFailsWith<IllegalStateException> {
            TsMachine(
                scene = scene,
                options = options,
                tsOptions = TsOptions(),
                observer = stoppedObserver,
                machineObserverFactory = stoppedObserver::createMachineObserver,
            ).use { machine ->
                val result = machine.analyzeWithMetadata(methods = listOf(method))
                assertTrue(result.timedOut)
                assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = result.stopReason)
                stoppedObserver.collectRemainingStates()
            }
        }

        assertEquals(expected = "queued input sink failed", actual = failure.message)
    }

    @Test
    fun `empty analysis has no stopped states to collect`() {
        val suspendedStates = mutableListOf<TsState>()
        val stoppedObserver = TsStoppedStatesObserver { suspendedStates += it }

        val outcome = TsMachine(
            scene = scene,
            tsOptions = TsOptions(),
            observer = stoppedObserver,
            machineObserverFactory = stoppedObserver::createMachineObserver,
        ).use { machine ->
            val result = machine.analyzeWithMetadata(methods = emptyList())
            stoppedObserver.collectRemainingStates()
            result
        }

        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(suspendedStates.isEmpty())
    }
}
