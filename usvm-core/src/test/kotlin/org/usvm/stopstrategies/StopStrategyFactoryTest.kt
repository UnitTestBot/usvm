package org.usvm.stopstrategies

import org.junit.jupiter.api.Test
import org.usvm.TestState
import org.usvm.TestTarget
import org.usvm.UMachineOptions
import org.usvm.mockState
import org.usvm.statistics.TimeStatistics
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class StopStrategyFactoryTest {
    @Test
    fun `default path keeps legacy targets-reached strategy`() {
        val target = TestTarget(method = "entry", offset = 0)

        val strategy = createStopStrategy(
            options = targetOptions(timeout = Duration.INFINITE),
            targets = listOf(target),
        )

        assertIs<TargetsReachedStopStrategy>(strategy)
        assertFalse(strategy.shouldStop())
    }

    @Test
    fun `default path keeps legacy plain timeout strategy`() {
        val timeStatistics = TimeStatistics<String, TestState>()

        val strategy = createStopStrategy(
            options = targetOptions(timeout = 10.seconds).copy(stopOnTargetsReached = false),
            targets = emptyList(),
            timeStatisticsFactory = { timeStatistics },
        )

        assertIs<TimeoutStopStrategy>(strategy)
    }

    @Test
    fun `opt-in replaces plain timeout and targets-reached strategies`() {
        val target = TestTarget(method = "entry", offset = 0)
        var timeStatisticsFactoryCalls = 0

        val strategy = createStopStrategy(
            options = targetOptions(timeout = 10.seconds),
            targets = listOf(target),
            timeStatisticsFactory = {
                timeStatisticsFactoryCalls++
                error("Plain timeout strategy must not be created in progress mode")
            },
            targetsProgressTimeout = 1.seconds,
        )

        assertIs<ProgressBasedStopStrategy>(strategy)
        assertEquals(0, timeStatisticsFactoryCalls)
        assertFalse(strategy.shouldStop())

        target.propagate(mockState(id = 0u, startMethod = "entry", targets = listOf(target)))

        assertTrue(strategy.shouldStop())
        assertEquals(ProgressBasedStopReason.TARGETS_REACHED, strategy.terminationReason)
    }

    @Test
    fun `opt-in keeps empty target completion at zero global ceiling`() {
        val strategy = createStopStrategy(
            options = targetOptions(timeout = Duration.ZERO),
            targets = emptyList(),
            targetsProgressTimeout = 1.seconds,
        )

        assertIs<ProgressBasedStopStrategy>(strategy)
        assertTrue(strategy.shouldStop())
        assertEquals(ProgressBasedStopReason.TARGETS_REACHED, strategy.terminationReason)
    }

    @Test
    fun `opt-in does not change empty target behavior when target stopping is disabled`() {
        val timeStatistics = TimeStatistics<String, TestState>()

        val strategy = createStopStrategy(
            options = targetOptions(timeout = 10.seconds).copy(stopOnTargetsReached = false),
            targets = emptyList(),
            timeStatisticsFactory = { timeStatistics },
            targetsProgressTimeout = 1.seconds,
        )

        assertIs<TimeoutStopStrategy>(strategy)
        assertFalse(strategy.shouldStop())
    }

    private fun targetOptions(timeout: Duration) = UMachineOptions(
        stopOnCoverage = 0,
        timeout = timeout,
        stopOnTargetsReached = true,
    )
}
