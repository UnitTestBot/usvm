package org.usvm.stopstrategies

import org.usvm.UMachineOptions
import org.usvm.statistics.CoverageStatistics

fun createStopStrategy(
    options: UMachineOptions,
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
    val timeoutMs = options.timeoutMs
    if (timeoutMs != null) {
        stopStrategies.add(TimeoutStopStrategy(timeoutMs, System::currentTimeMillis))
    }

    if (stopStrategies.isEmpty()) {
        return StopStrategy { false }
    }
    if (stopStrategies.size == 1) {
        return stopStrategies.first()
    }
    return GroupedStopStrategy(stopStrategies)
}
