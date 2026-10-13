package org.usvm

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.usvm.ps.BfsPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.UMachineObserver
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Exercises machine failure routing with a deliberately throwing interpreter, without a solver. */
class MachineFailureTest {
    @Test
    fun `step failure reports the original error once and preserves sibling states`() {
        val failedState = mockState(id = 1u, startMethod = "failed")
        val sibling = mockState(id = 2u, startMethod = "sibling")
        sibling.models = listOf(mockk())
        val failure = AssertionError("synthetic interpreter failure")
        val observer = RecordingObserver()
        val siblingObserver = RecordingObserver()
        val interpreter = object : UInterpreter<TestState>() {
            override fun step(state: TestState): StepResult<TestState> {
                if (state === failedState) throw failure

                return StepResult(forkedStates = emptySequence(), originalStateAlive = true)
            }
        }

        TestMachine(throwExceptionOnStepFailure = false).execute(
            interpreter = interpreter,
            states = listOf(failedState, sibling),
            observer = CompositeUMachineObserver(observer, siblingObserver),
        )

        for (recorded in listOf(observer, siblingObserver)) {
            val (reportedState, reportedFailure) = recorded.failures.single()
            assertSame(expected = failedState, actual = reportedState)
            assertSame(expected = failure, actual = reportedFailure)
            assertEquals(expected = listOf(failedState to false, sibling to true), actual = recorded.terminated)
            assertEquals(expected = 1, actual = recorded.stops)
        }
    }

    @Test
    fun `strict mode propagates the original step error and stops the machine`() {
        val state = mockState(id = 1u, startMethod = "failed")
        val failure = AssertionError("strict interpreter failure")
        val observer = RecordingObserver()

        val caught = assertFailsWith<AssertionError> {
            TestMachine(throwExceptionOnStepFailure = true).execute(
                interpreter = throwingInterpreter(failure),
                states = listOf(state),
                observer = observer,
            )
        }

        assertSame(expected = failure, actual = caught)
        assertTrue(observer.failures.isEmpty())
        assertTrue(observer.terminated.isEmpty())
        assertEquals(expected = 1, actual = observer.stops)
    }

    @Test
    fun `cancellation and interruption are never reported as recoverable failures`() {
        val signals = listOf(CancellationException("cancelled"), InterruptedException("interrupted"))
        for (signal in signals) {
            val state = mockState(id = 1u, startMethod = "cancelled")
            val observer = RecordingObserver()

            val caught = assertFailsWith<Exception> {
                TestMachine(throwExceptionOnStepFailure = false).execute(
                    interpreter = throwingInterpreter(signal),
                    states = listOf(state),
                    observer = observer,
                )
            }

            assertSame(expected = signal, actual = caught)
            assertTrue(observer.failures.isEmpty())
            assertTrue(observer.terminated.isEmpty())
            assertEquals(expected = 1, actual = observer.stops)
        }
    }

    private fun throwingInterpreter(error: Throwable): UInterpreter<TestState> =
        object : UInterpreter<TestState>() {
            override fun step(state: TestState): StepResult<TestState> = throw error
        }

    private class TestMachine(throwExceptionOnStepFailure: Boolean) : UMachine<TestState>() {
        override val options = UMachineOptions(throwExceptionOnStepFailure = throwExceptionOnStepFailure)

        fun execute(
            interpreter: UInterpreter<TestState>,
            states: List<TestState>,
            observer: UMachineObserver<TestState>,
        ) {
            val pathSelector = BfsPathSelector<TestState>()
            pathSelector.add(states)

            run(interpreter, pathSelector, observer, isStateTerminated = { true })
        }

        override fun close() = Unit
    }

    private class RecordingObserver : UMachineObserver<TestState> {
        val failures = mutableListOf<Pair<TestState, Throwable>>()
        val terminated = mutableListOf<Pair<TestState, Boolean>>()
        var stops = 0

        override fun onStepFailure(state: TestState, error: Throwable) {
            failures += state to error
        }

        override fun onStateTerminated(state: TestState, stateReachable: Boolean) {
            terminated += state to stateReachable
        }

        override fun onMachineStopped() {
            stops++
        }
    }
}
