package org.usvm

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.usvm.ps.BfsPathSelector
import org.usvm.statistics.UMachineObserver
import org.usvm.stopstrategies.StopStrategy
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MachineForkLimitTest {
    @Test
    fun `binary fork retains both pending states at the threshold`() {
        val root = state()
        val sibling = state()
        val machine = ForkMachine(forkLimit = 1UL)
        val selector = queue(root)

        val result = machine.execute(selector = selector, forks = mapOf(root to listOf(sibling)))

        assertEquals(UMachineStopReason.FORK_LIMIT, result.stopReason)
        assertEquals(expected = 1UL, actual = result.generatedForks)
        assertEquals(expected = 1UL, actual = result.forkLimit)
        assertEquals(expected = setOf(root, sibling), actual = selector.drain().toSet())
        assertTrue(machine.terminated.isEmpty())
    }

    @Test
    fun `multiway fork finishes atomically and reports overshoot without dropping siblings`() {
        val root = state()
        val siblings = List(size = 3) { state() }
        val machine = ForkMachine(forkLimit = 1UL)
        val selector = queue(root)

        val result = machine.execute(selector = selector, forks = mapOf(root to siblings))

        assertEquals(UMachineStopReason.FORK_LIMIT, result.stopReason)
        assertEquals(expected = 3UL, actual = result.generatedForks)
        assertEquals(expected = 1UL, actual = result.forkLimit)
        assertEquals(expected = (siblings + root).toSet(), actual = selector.drain().toSet())
        assertTrue(machine.terminated.isEmpty())
    }

    @Test
    fun `zero threshold stops before execution while an empty queue is exhausted`() {
        val machine = ForkMachine(forkLimit = 0UL)
        val root = state()
        val selector = queue(root)

        val stopped = machine.execute(selector = selector)
        val empty = machine.execute(selector = queue())

        assertEquals(UMachineStopReason.FORK_LIMIT, stopped.stopReason)
        assertEquals(expected = 0UL, actual = stopped.generatedForks)
        assertEquals(expected = setOf(root), actual = selector.drain().toSet())
        assertEquals(UMachineStopReason.EXHAUSTED, empty.stopReason)
        assertEquals(expected = 0, actual = machine.steps)
    }

    @Test
    fun `disabled limit preserves execution and nonforking steps do not spend the budget`() {
        for (limit in listOf(null, 1UL)) {
            val machine = ForkMachine(forkLimit = limit)
            val root = state()
            val selector = queue(root)

            val result = machine.execute(selector = selector)

            assertEquals(UMachineStopReason.EXHAUSTED, result.stopReason)
            assertEquals(expected = 0UL, actual = result.generatedForks)
            assertEquals(expected = listOf(root), actual = machine.terminated)
            assertTrue(selector.isEmpty())
        }
    }

    @Test
    fun `disabled limit allows branching through exhaustion`() {
        val root = state()
        val sibling = state()
        val machine = ForkMachine(forkLimit = null)
        val selector = queue(root)

        val result = machine.execute(selector = selector, forks = mapOf(root to listOf(sibling)))

        assertEquals(UMachineStopReason.EXHAUSTED, result.stopReason)
        assertEquals(expected = 1UL, actual = result.generatedForks)
        assertEquals(expected = setOf(root, sibling), actual = machine.terminated.toSet())
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `an existing stopping strategy retains its separate stop reason`() {
        val machine = ForkMachine(forkLimit = 1UL)
        val selector = queue(state())
        val strategy = StopStrategy { machine.steps >= 1 }

        val result = machine.execute(selector = selector, stopStrategy = strategy)

        assertEquals(UMachineStopReason.STOPPED, result.stopReason)
        assertEquals(expected = 0UL, actual = result.generatedForks)
        assertFalse(selector.isEmpty())
        assertTrue(machine.terminated.isEmpty())
    }

    @Test
    fun `terminal successors count but a drained queue is exhausted`() {
        val root = state()
        val sibling = state()
        val machine = ForkMachine(forkLimit = 1UL)

        val result = machine.execute(
            selector = queue(root),
            forks = mapOf(root to listOf(sibling)),
            terminateImmediately = true,
        )

        assertEquals(UMachineStopReason.EXHAUSTED, result.stopReason)
        assertEquals(expected = 1UL, actual = result.generatedForks)
        assertEquals(expected = setOf(root, sibling), actual = machine.terminated.toSet())
    }

    @Test
    fun `counter resets between runs of the same machine`() {
        val machine = ForkMachine(forkLimit = 1UL)

        repeat(times = 2) {
            val root = state()
            val selector = queue(root)
            val result = machine.execute(selector = selector, forks = mapOf(root to listOf(state())))

            assertEquals(UMachineStopReason.FORK_LIMIT, result.stopReason)
            assertEquals(expected = 1UL, actual = result.generatedForks)
            assertFalse(selector.isEmpty())
        }

        assertEquals(expected = 2, actual = machine.steps)
    }

    private fun state(): TestState {
        val state = mockk<TestState>()
        every { state.models } returns listOf(mockk())
        return state
    }

    private fun queue(vararg states: TestState): BfsPathSelector<TestState> = BfsPathSelector<TestState>().apply {
        add(states = states.toList())
    }

    private class ForkMachine(forkLimit: ULong?) : UMachine<TestState>() {
        override val options = UMachineOptions(forkLimit = forkLimit)
        val terminated = mutableListOf<TestState>()
        var steps = 0

        fun execute(
            selector: UPathSelector<TestState>,
            forks: Map<TestState, List<TestState>> = emptyMap(),
            terminateImmediately: Boolean = false,
            stopStrategy: StopStrategy = StopStrategy { false },
        ): UMachineRunResult {
            val visited = mutableSetOf<TestState>()
            val completed = mutableSetOf<TestState>()
            val interpreter = object : UInterpreter<TestState>() {
                override fun step(state: TestState): StepResult<TestState> {
                    steps++
                    val firstVisit = visited.add(state)
                    if (!firstVisit) completed += state
                    val siblings = if (firstVisit) forks[state].orEmpty() else emptyList()
                    return StepResult(forkedStates = siblings.asSequence(), originalStateAlive = true)
                }
            }
            val observer = object : UMachineObserver<TestState> {
                override fun onStateTerminated(state: TestState, stateReachable: Boolean) {
                    assertTrue(stateReachable)
                    terminated += state
                }
            }

            return run(
                interpreter = interpreter,
                pathSelector = selector,
                observer = observer,
                isStateTerminated = { state -> terminateImmediately || state in completed },
                stopStrategy = stopStrategy,
            )
        }

        override fun close() = Unit
    }
}
