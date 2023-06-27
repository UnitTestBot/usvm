package org.usvm.stopstrategies

import org.usvm.MachineOptions
import org.usvm.statistics.CoverageStatistics

fun createStopStrategy(
    options: MachineOptions,
    coverageStatistics: () -> CoverageStatistics<*, *, *>? = { null },
    getCollectedStatesCount: (() -> Int)? = null
) : StopStrategy {
    val stopStrategies = mutableListOf<StopStrategy>()

    val stepLimit = options.stepLimit
    if (stepLimit != null) {
        stopStrategies.add(StepLimitStopStrategy(stepLimit))
    }
    if (options.stopOnCoverage in 1..100) {
        val coverageStatisticsValue = requireNotNull(coverageStatistics()) { "Coverage statistics is required for stopping on expected coverage achieved" }
        stopStrategies.add(StopStrategy { coverageStatisticsValue.getTotalCoverage() >= options.stopOnCoverage })
    }
    val collectedStatesLimit = options.collectedStatesLimit
    if (collectedStatesLimit != null && collectedStatesLimit > 0) {
        requireNotNull(getCollectedStatesCount) { "Collected states count getter is required for stopping on collected states limit" }
        stopStrategies.add(StopStrategy { getCollectedStatesCount() >= collectedStatesLimit })
    }

    if (stopStrategies.isEmpty()) {
        return StopStrategy { false }
    }
    if (stopStrategies.size == 1) {
        return stopStrategies.first()
    }
    return GroupedStopStrategy(stopStrategies)
}
