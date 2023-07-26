package org.usvm.stopstrategies

import org.usvm.UMachineOptions
import org.usvm.statistics.CoverageStatistics

fun createStopStrategy(
    options: UMachineOptions,
    coverageStatistics: () -> CoverageStatistics<*, *, *>? = { null },
    getCollectedStatesCount: (() -> Int)? = null,
) : StopStrategy {
    val stopStrategies = mutableListOf<StopStrategy>()

    val stepLimit = options.stepLimit
    if (stepLimit != null) {
        stopStrategies.add(StepLimitStopStrategy(stepLimit))
    }
    if (options.stopOnCoverage in 1..100) {
        val coverageStatisticsValue = requireNotNull(coverageStatistics()) {
            "Coverage statistics is required for stopping on expected coverage achieved"
        }
        val coverageStopStrategy = StopStrategy { coverageStatisticsValue.getTotalCoverage() >= options.stopOnCoverage }
        stopStrategies.add(coverageStopStrategy)
    }
    val collectedStatesLimit = options.collectedStatesLimit
    if (collectedStatesLimit != null && collectedStatesLimit > 0) {
        requireNotNull(getCollectedStatesCount) {
            "Collected states count getter is required for stopping on collected states limit"
        }
        val collectedStatesCountStopStrategy = StopStrategy { getCollectedStatesCount() >= collectedStatesLimit }
        stopStrategies.add(collectedStatesCountStopStrategy)
    }
    val timeoutMs = options.timeoutMs
    if (timeoutMs != null) {
        stopStrategies.add(TimeoutStopStrategy(timeoutMs, System::currentTimeMillis))
    }

    val stepsFromLastCovered = options.stepsFromLastCovered
    if (stepsFromLastCovered != null && getCollectedStatesCount != null) {
        val stepsFromLastCoveredStopStrategy = StepsFromLastCoveredStopStrategy(
            stepsFromLastCovered.toULong(),
            getCollectedStatesCount
        )
        stopStrategies.add(stepsFromLastCoveredStopStrategy)
    }

    if (stopStrategies.isEmpty()) {
        return StopStrategy { false }
    }
    if (stopStrategies.size == 1) {
        return stopStrategies.first()
    }
    return GroupedStopStrategy(stopStrategies)
}
