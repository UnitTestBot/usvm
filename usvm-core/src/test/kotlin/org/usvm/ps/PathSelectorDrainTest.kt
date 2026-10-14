package org.usvm.ps

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.usvm.PathNode
import org.usvm.TestInstruction
import org.usvm.TestState
import org.usvm.UCallStack
import org.usvm.UPathSelector
import org.usvm.merging.CloseStatesSearcher
import org.usvm.merging.MergingPathSelector
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PathSelectorDrainTest {
    @Test
    fun `default drain removes every leaf state`() {
        val selector = BfsPathSelector<String>()
        selector.add(states = listOf("first", "second", "third"))

        val drained = selector.drain().toList()

        assertEquals(expected = listOf("first", "second", "third"), actual = drained)
        assertTrue(selector.isEmpty())
        assertTrue(selector.drain().none())
    }

    @Test
    fun `constant time drain includes unvisited keys without new scheduling events`() {
        val stopwatch = TestStopwatch()
        val children = mutableMapOf<Char, TrackingSelector<String>>()
        val turns = mutableListOf<Triple<Char, Duration, Duration>>()
        var stopped = false
        val selector = ConstantTimeFairPathSelector(
            initialKeys = setOf('a', 'b', 'c'),
            stopwatch = stopwatch,
            getRemainingTime = {
                check(!stopped) { "Drain must not request a new time quantum" }
                30.seconds
            },
            getKey = { state: String -> state.first() },
            getKeyPriority = { key ->
                check(!stopped) { "Drain must not reprioritize entry points" }
                key
            },
            basePathSelectorFactory = { key -> TrackingSelector<String>().also { children[key] = it } },
            maxTimeQuantum = 2.seconds,
            onTimeQuantum = { key, requested, elapsed -> turns += Triple(key, requested, elapsed) },
        )
        selector.add(states = listOf("a1", "a2", "b1", "c1"))
        assertEquals(expected = "a1", actual = selector.peek())
        stopwatch.elapsedRaw = 3L
        selector.finishTimeQuantum()
        stopped = true

        val drained = selector.drain().toList()

        assertEquals(expected = setOf("a1", "a2", "b1", "c1"), actual = drained.toSet())
        assertEquals(expected = 4, actual = drained.size)
        assertEquals(expected = 1, actual = children.values.sumOf { it.peekCount })
        assertEquals(expected = listOf(Triple('a', 2.seconds, 3.seconds)), actual = turns)
        assertEquals(expected = 3.seconds, actual = stopwatch.elapsed)
        assertFalse(stopwatch.isRunning)
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `completely fair drain preserves every never peeked key`() {
        val stopwatch = TestStopwatch()
        val children = mutableListOf<TrackingSelector<String>>()
        val selector = CompletelyFairPathSelector(
            initialKeys = setOf('a', 'b', 'c'),
            stopwatch = stopwatch,
            getKey = { state: String -> state.first() },
            basePathSelectorFactory = {
                TrackingSelector<String>().also { child -> children.add(child) }
            },
        )
        selector.add(states = listOf("a1", "b1", "c1"))

        val drained = selector.drain().toList()

        assertEquals(expected = setOf("a1", "b1", "c1"), actual = drained.toSet())
        assertEquals(expected = 0, actual = children.sumOf { it.peekCount })
        assertFalse(stopwatch.isRunning)
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `interleaved drain emits shared states once and preserves distinct identities`() {
        val first = Token(value = "same")
        val second = Token(value = "same")
        val children = listOf(TrackingSelector<Token>(), TrackingSelector<Token>())
        val selector = InterleavedPathSelector(selectors = children)
        selector.add(states = listOf(first, second))

        val drained = selector.drain().toList()

        assertEquals(expected = 2, actual = drained.size)
        assertSame(expected = first, actual = drained[0])
        assertSame(expected = second, actual = drained[1])
        assertTrue(children.all { it.isEmpty() })
        assertEquals(expected = 0, actual = children.sumOf { it.peekCount })
    }

    @Test
    fun `parallel drain visits every independent queue without scheduling`() {
        val first = Token(value = "same")
        val second = Token(value = "same")
        val children = listOf(TrackingSelector<Token>(), TrackingSelector<Token>())
        val selector = ParallelPathSelector(selectors = children)
        selector.add(states = listOf(first))
        selector.add(states = listOf(second))

        val drained = selector.drain().toList()

        assertEquals(expected = 2, actual = drained.size)
        assertSame(expected = first, actual = drained[0])
        assertSame(expected = second, actual = drained[1])
        assertTrue(selector.isEmpty())
        assertEquals(expected = 0, actual = children.sumOf { it.peekCount })
    }

    @Test
    @Suppress("IgnoredReturnValue") // Mock verification checks that this value-producing call never happens.
    fun `merging drain preserves queued identities and removes searcher entries`() {
        val first = mockk<TestState>()
        val second = mockk<TestState>()
        val child = TrackingSelector<TestState>()
        val searcher = mockk<CloseStatesSearcher<TestState>>(relaxed = true)
        val selector = MergingPathSelector(
            underlyingPathSelector = child,
            closeStatesSearcher = searcher,
        )
        selector.add(states = listOf(first, second))

        val drained = selector.drain().toList()

        assertEquals(expected = 2, actual = drained.size)
        assertSame(expected = first, actual = drained[0])
        assertSame(expected = second, actual = drained[1])
        assertEquals(expected = 0, actual = child.peekCount)
        verify(exactly = 0) { searcher.findCloseStates(any()) }
        verify(exactly = 1) { searcher.remove(first) }
        verify(exactly = 1) { searcher.remove(second) }
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `iterative deepening drain includes deferred states alongside active states`() {
        val header = TestInstruction(method = "method", offset = 1)
        val body = TestInstruction(method = "method", offset = 2)
        val outside = TestInstruction(method = "method", offset = 3)
        val firstHeader = PathNode.root<TestInstruction>() + header
        var path = firstHeader
        var forks = PathNode.root<PathNode<TestInstruction>>()
        val deferred = loopState(path = { path }, forks = { forks })
        val active = loopState(
            path = { firstHeader + outside },
            forks = { PathNode.root() },
        )
        val child = TrackingSelector<TestState>()
        val selector = IterativeDeepeningPs(
            underlyingPs = child,
            loopTracker = loopTracker(header = header),
            iterationLimit = null,
        )
        selector.add(states = listOf(deferred))
        assertSame(expected = deferred, actual = selector.peek())
        path = firstHeader + body + header
        forks += firstHeader
        selector.update(state = deferred)
        assertTrue(child.isEmpty())
        selector.add(states = listOf(active))

        val drained = selector.drain().toList()

        assertEquals(expected = 2, actual = drained.size)
        assertSame(expected = active, actual = drained[0])
        assertSame(expected = deferred, actual = drained[1])
        assertEquals(expected = 1, actual = child.peekCount)
        assertEquals(expected = 1, actual = child.drainCount)
        assertTrue(selector.isEmpty())
        assertTrue(selector.drain().none())
    }

    @Test
    fun `loop limiter drains its child without a synthetic peek`() {
        val header = TestInstruction(method = "method", offset = 1)
        val state = loopState(
            path = { PathNode.root<TestInstruction>() + header },
            forks = { PathNode.root() },
        )
        val child = TrackingSelector<TestState>()
        val selector = LoopLimiterPs(
            underlyingPs = child,
            loopTracker = loopTracker(header = header),
            iterationLimit = 2,
        )
        selector.add(states = listOf(state))

        val drained = selector.drain().toList()

        assertSame(expected = state, actual = drained.single())
        assertEquals(expected = 0, actual = child.peekCount)
        assertTrue(selector.isEmpty())
    }

    @Test
    fun `exception drain includes exceptional only states and emits overlapping states once`() {
        val regular = mockk<TestState>()
        val exceptionalOnly = mockk<TestState>()
        var regularIsExceptional = false
        every { regular.isExceptional } answers { regularIsExceptional }
        every { exceptionalOnly.isExceptional } returns true
        val child = TrackingSelector<TestState>()
        val selector = ExceptionPropagationPathSelector(selector = child)
        selector.add(states = listOf(regular, exceptionalOnly))
        regularIsExceptional = true
        selector.update(state = regular)

        val drained = selector.drain().toList()

        assertEquals(expected = 2, actual = drained.size)
        assertSame(expected = regular, actual = drained[0])
        assertSame(expected = exceptionalOnly, actual = drained[1])
        assertEquals(expected = 0, actual = child.peekCount)
        assertTrue(selector.isEmpty())
    }

    private fun loopState(
        path: () -> PathNode<TestInstruction>,
        forks: () -> PathNode<PathNode<TestInstruction>>,
    ): TestState {
        val state = mockk<TestState>()
        every { state.callStack } returns UCallStack<String, TestInstruction>(method = "method")
        every { state.pathNode } answers { path() }
        every { state.forkPoints } answers { forks() }
        return state
    }

    private fun loopTracker(header: TestInstruction): StateLoopTracker<String, TestInstruction, TestState> =
        object : StateLoopTracker<String, TestInstruction, TestState> {
            override fun findLoopEntrance(statement: TestInstruction): String? =
                if (statement == header) "loop" else null

            override fun isLoopIterationFork(loop: String, forkPoint: TestInstruction): Boolean = true
        }

    private data class Token(val value: String)

    private class TrackingSelector<State> : UPathSelector<State> {
        private val states = mutableListOf<State>()
        var peekCount: Int = 0
            private set
        var drainCount: Int = 0
            private set

        override fun isEmpty(): Boolean = states.isEmpty()

        override fun peek(): State {
            peekCount++
            return states.first()
        }

        override fun add(states: Collection<State>) {
            this.states.addAll(states)
        }

        override fun update(state: State) = Unit

        override fun remove(state: State) {
            val index = states.indexOfFirst { it === state }
            check(index >= 0) { "Missing state" }
            states.removeAt(index)
        }

        override fun drain(): Sequence<State> = sequence {
            drainCount++
            while (states.isNotEmpty()) {
                yield(states.removeAt(index = 0))
            }
        }
    }
}
