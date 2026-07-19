package org.usvm.stopstrategies

import org.junit.jupiter.api.Test
import org.usvm.targets.UTarget
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class ProgressBasedStopStrategyTest {
    @Test
    fun `progress extends no-progress window`() {
        val clock = FakeClock()
        var progress = 0L
        val strategy = strategy(clock, progressCounter = { progress })

        assertFalse(strategy.shouldStop())

        clock.advance(750.milliseconds)
        progress++
        assertFalse(strategy.shouldStop())

        clock.advance(999.milliseconds)
        assertFalse(strategy.shouldStop())
        assertNull(strategy.terminationReason)

        clock.advance(1.milliseconds)
        assertTrue(strategy.shouldStop())
        assertEquals(ProgressBasedStopReason.NO_PROGRESS_TIMEOUT, strategy.terminationReason)
        assertEquals("Stop reason: timeout_no_progress", strategy.stopReason())
    }

    @Test
    fun `no progress stops exactly at timeout boundary`() {
        val clock = FakeClock()
        val strategy = strategy(clock)

        assertFalse(strategy.shouldStop())

        clock.advance(999.milliseconds)
        assertFalse(strategy.shouldStop())

        clock.advance(1.milliseconds)
        assertTrue(strategy.shouldStop())
        assertEquals(ProgressBasedStopReason.NO_PROGRESS_TIMEOUT, strategy.terminationReason)
    }

    @Test
    fun `global safety timeout is independent from progress`() {
        val clock = FakeClock()
        var progress = 0L
        val strategy = strategy(
            clock = clock,
            progressCounter = { progress },
            progressTimeout = 1.seconds,
            globalSafetyTimeout = 3.seconds,
        )

        assertFalse(strategy.shouldStop())

        repeat(2) {
            clock.advance(900.milliseconds)
            progress++
            assertFalse(strategy.shouldStop())
        }

        clock.advance(1_200.milliseconds)
        progress++
        assertTrue(strategy.shouldStop())
        assertEquals(ProgressBasedStopReason.GLOBAL_SAFETY_TIMEOUT, strategy.terminationReason)
        assertEquals("Stop reason: global_safety_timeout", strategy.stopReason())
    }

    @Test
    fun `clock regression is rejected`() {
        val clock = FakeClock(2.seconds)
        val strategy = strategy(clock)

        assertFalse(strategy.shouldStop())
        clock.now = 1.seconds

        val error = assertFailsWith<IllegalStateException> { strategy.shouldStop() }
        assertTrue(error.message.orEmpty().contains("clock regressed", ignoreCase = true))
        assertNull(strategy.terminationReason)
    }

    @Test
    fun `empty target collection preserves legacy completion behavior`() {
        val targets = emptyList<TestTarget>()
        val legacyStrategy = TargetsReachedStopStrategy(targets)
        val progressStrategy = ProgressBasedStopStrategy(
            progressTimeout = 1.seconds,
            progressCounter = { targets.count(TestTarget::isRemoved).toLong() },
            // A mechanically computed `targets.size * timeout` is zero for this legacy edge case.
            globalSafetyTimeout = ZERO,
            isComplete = { targets.all(TestTarget::isRemoved) },
            clock = FakeClock()::read,
        )

        assertEquals(legacyStrategy.shouldStop(), progressStrategy.shouldStop())
        assertTrue(progressStrategy.shouldStop())
        assertEquals(ProgressBasedStopReason.TARGETS_REACHED, progressStrategy.terminationReason)
    }

    private fun strategy(
        clock: FakeClock,
        progressCounter: () -> Long = { 0L },
        progressTimeout: Duration = 1.seconds,
        globalSafetyTimeout: Duration = 10.seconds,
    ) = ProgressBasedStopStrategy(
        progressTimeout = progressTimeout,
        progressCounter = progressCounter,
        globalSafetyTimeout = globalSafetyTimeout,
        clock = clock::read,
    )

    private class FakeClock(var now: Duration = ZERO) {
        fun read(): Duration = now

        fun advance(duration: Duration) {
            now += duration
        }
    }

    private class TestTarget : UTarget<Unit, TestTarget>()
}
