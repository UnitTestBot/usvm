package org.usvm.ps

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ConstantTimeFairPathSelectorQuantumTest {
    @Test
    fun `bounded turns revisit every key and retain its pending states`() {
        val stopwatch = TestStopwatch()
        var remainingSeconds = 60L
        val selector = selector(
            stopwatch = stopwatch,
            remainingTime = { remainingSeconds.seconds },
            maxTimeQuantum = 2.seconds,
        )
        selector.add(states = listOf("a1", "a2", "b1", "c1"))

        val visited = List(size = 12) { index ->
            val state = selector.peek()
            if (index == 1) {
                selector.remove(state = state)
            }
            stopwatch.elapsedRaw++
            remainingSeconds--
            state
        }

        assertEquals(
            expected = listOf("a1", "a1", "b1", "b1", "c1", "c1", "a2", "a2", "b1", "b1", "c1", "c1"),
            actual = visited,
        )
    }

    @Test
    fun `an overrun preserves bounded turns for the remaining keys`() {
        val stopwatch = TestStopwatch()
        var remainingSeconds = 100L
        val selector = selector(
            stopwatch = stopwatch,
            remainingTime = { remainingSeconds.seconds },
            maxTimeQuantum = 2.seconds,
        )
        selector.add(states = listOf("a1", "b1", "c1"))
        assertEquals(expected = "a1", actual = selector.peek())

        stopwatch.elapsedRaw = 20L
        remainingSeconds = 80L
        val afterOverrun = selector.peek()
        stopwatch.elapsedRaw = 1L
        remainingSeconds = 79L
        val withinNextTurn = selector.peek()
        stopwatch.elapsedRaw = 2L
        remainingSeconds = 78L
        val afterNextTurn = selector.peek()

        assertEquals(expected = "b1", actual = afterOverrun)
        assertEquals(expected = "b1", actual = withinNextTurn)
        assertEquals(expected = "c1", actual = afterNextTurn)
    }

    @Test
    fun `the remaining budget can shorten the configured turn`() {
        val stopwatch = TestStopwatch()
        var remainingSeconds = 3L
        val selector = selector(
            stopwatch = stopwatch,
            remainingTime = { remainingSeconds.seconds },
            maxTimeQuantum = 2.seconds,
        )
        selector.add(states = listOf("a1", "b1", "c1"))

        val visited = List(size = 3) {
            val state = selector.peek()
            stopwatch.elapsedRaw++
            remainingSeconds--
            state
        }

        assertEquals(expected = listOf("a1", "b1", "c1"), actual = visited)
    }

    @Test
    fun `a finite turn also works with an unlimited total budget`() {
        val stopwatch = TestStopwatch()
        val selector = selector(
            stopwatch = stopwatch,
            remainingTime = { Duration.INFINITE },
            maxTimeQuantum = 2.seconds,
        )
        selector.add(states = listOf("a1", "b1", "c1"))
        assertEquals(expected = "a1", actual = selector.peek())

        stopwatch.elapsedRaw = 2L
        val nextState = selector.peek()

        assertEquals(expected = "b1", actual = nextState)
    }

    @Test
    fun `measured turns preserve overrun and final partial turn exactly once`() {
        val stopwatch = TestStopwatch()
        val turns = mutableListOf<Triple<Char, Duration, Duration>>()
        val selector = selector(
            stopwatch = stopwatch,
            remainingTime = { 30.seconds },
            maxTimeQuantum = 2.seconds,
            onTimeQuantum = { key, requested, elapsed -> turns += Triple(key, requested, elapsed) },
        )
        selector.add(states = listOf("a1", "b1", "c1"))
        assertEquals(expected = "a1", actual = selector.peek())

        stopwatch.elapsedRaw = 4L
        assertEquals(expected = "b1", actual = selector.peek())
        stopwatch.elapsedRaw = 1L
        selector.finishTimeQuantum()
        selector.finishTimeQuantum()

        assertEquals(
            expected = listOf(Triple('a', 2.seconds, 4.seconds), Triple('b', 2.seconds, 1.seconds)),
            actual = turns,
        )
    }

    @Test
    fun `successive turns of the only remaining key remain distinct`() {
        val stopwatch = TestStopwatch()
        val turns = mutableListOf<Triple<Char, Duration, Duration>>()
        val selector = selector(
            stopwatch = stopwatch,
            remainingTime = { 30.seconds },
            maxTimeQuantum = 2.seconds,
            onTimeQuantum = { key, requested, elapsed -> turns += Triple(key, requested, elapsed) },
        )
        selector.add(states = listOf("a1"))
        assertEquals(expected = "a1", actual = selector.peek())

        stopwatch.elapsedRaw = 2L
        assertEquals(expected = "a1", actual = selector.peek())
        stopwatch.elapsedRaw = 1L
        selector.finishTimeQuantum()

        assertEquals(
            expected = listOf(Triple('a', 2.seconds, 2.seconds), Triple('a', 2.seconds, 1.seconds)),
            actual = turns,
        )
    }

    @Test
    fun `nonpositive turn bounds are rejected`() {
        for (quantum in listOf(Duration.ZERO, -1.seconds)) {
            val stopwatch = TestStopwatch()

            assertFailsWith<IllegalArgumentException> {
                selector(
                    stopwatch = stopwatch,
                    remainingTime = { 30.seconds },
                    maxTimeQuantum = quantum,
                )
            }
        }
    }

    private fun selector(
        stopwatch: TestStopwatch,
        remainingTime: () -> Duration,
        maxTimeQuantum: Duration,
        onTimeQuantum: (Char, Duration, Duration) -> Unit = { _, _, _ -> },
    ): ConstantTimeFairPathSelector<String, Char, Char> = ConstantTimeFairPathSelector(
        initialKeys = setOf('a', 'b', 'c'),
        stopwatch = stopwatch,
        getRemainingTime = remainingTime,
        getKey = { state -> state.first() },
        getKeyPriority = { key -> key },
        basePathSelectorFactory = { BfsPathSelector() },
        maxTimeQuantum = maxTimeQuantum,
        onTimeQuantum = onTimeQuantum,
    )
}
