package org.usvm.stopstrategies

import org.usvm.statistics.CoverageStatistics

fun createStopStrategy(
    expectedCoverage: Int = 100,
    stepLimit: ULong? = null,
    coverageStatistics: () -> CoverageStatistics<*, *, *>? = { null }
) : StopStrategy {
    val stopStrategies = mutableListOf<StopStrategy>()
    if (stepLimit != null) {
        stopStrategies.add(StepLimitStopStrategy(stepLimit))
    }
    if (expectedCoverage in 1..100) {
        val coverageStatisticsValue = requireNotNull(coverageStatistics()) { "Coverage statistics is required for stopping on expected coverage achieved" }
        stopStrategies.add(StopStrategy { coverageStatisticsValue.getTotalCoverage() >= expectedCoverage })
    }

    if (stopStrategies.isEmpty()) {
        return StopStrategy { false }
    }
    if (stopStrategies.size == 1) {
        return stopStrategies.first()
    }
    return GroupedStopStrategy(stopStrategies)
}
