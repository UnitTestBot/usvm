package org.usvm.machine

import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.expr.TsSimpleValueResolver
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.util.TsMethodTestRunner
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration

/** Injects plugin callback failures to test error routing, not the semantics of a source construct. */
class TsInterpreterFailureTest : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/StaticOverloads.ts")

    @Test
    fun `interpreter and machine catch paths each report an unexpected callback failure once`() {
        val failures = listOf(IllegalStateException("callback exception"), AssertionError("callback error"))
        for (failure in failures) {
            val observer = FailingObserver(failure)

            val result = analyze(observer, throwExceptionOnStepFailure = false)

            assertSame(expected = failure, actual = observer.failures.single())
            assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
            assertTrue(result.states.isEmpty())
            assertTrue(result.unsupportedPaths.isEmpty())
            assertEquals(expected = 1, actual = observer.stops)
        }
    }

    @Test
    fun `strict interpreter mode propagates callback failures`() {
        val failure = IllegalStateException("callback exception")
        val observer = FailingObserver(failure)

        val caught = assertFailsWith<IllegalStateException> {
            analyze(observer, throwExceptionOnStepFailure = true)
        }

        assertSame(expected = failure, actual = caught)
        assertTrue(observer.failures.isEmpty())
        assertEquals(expected = 1, actual = observer.stops)
    }

    @Test
    fun `callback cancellation and interruption propagate through both catch layers`() {
        val signals = listOf(CancellationException("cancelled"), InterruptedException("interrupted"))
        for (signal in signals) {
            val observer = FailingObserver(signal)

            val caught = assertFailsWith<Exception> {
                analyze(observer, throwExceptionOnStepFailure = false)
            }

            assertSame(expected = signal, actual = caught)
            assertTrue(observer.failures.isEmpty())
            assertEquals(expected = 1, actual = observer.stops)
        }
    }

    private fun analyze(observer: FailingObserver, throwExceptionOnStepFailure: Boolean): TsAnalysisResult {
        val method = getMethod(methodName = "callOverloaded", className = "StaticOverloads")
        val options = UMachineOptions(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
            throwExceptionOnStepFailure = throwExceptionOnStepFailure,
        )

        return TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            machineObserver = observer,
            observer = observer,
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }
    }

    private class FailingObserver(private val failure: Throwable) : TsInterpreterObserver, UMachineObserver<TsState> {
        val failures = mutableListOf<Throwable>()
        var stops = 0

        override fun onReturnStatement(
            simpleValueResolver: TsSimpleValueResolver,
            stmt: EtsReturnStmt,
            scope: TsStepScope,
        ) {
            throw failure
        }

        override fun onStepFailure(state: TsState, error: Throwable) {
            failures += error
        }

        override fun onMachineStopped() {
            stops++
        }
    }
}
