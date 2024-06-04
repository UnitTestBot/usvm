package org.usvm.stopstrategies

import org.usvm.UMachineOptions
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.targets.UTarget
import kotlin.time.Duration

fun createStopStrategy(
    options: UMachineOptions,
    targets: Collection<UTarget<*, *>>,
    timeStatisticsFactory: () -> TimeStatistics<*, *>? = { null },
    stepsStatisticsFactory: () -> StepsStatistics<*, *>? = { null },
    coverageStatisticsFactory: () -> CoverageStatistics<*, *, *>? = { null },
    getCollectedStatesCount: (() -> Int)? = null,
) : StopStrategy {
    val stepsStatistics = lazy { requireNotNull(stepsStatisticsFactory()) { "Steps statistics is required for selected stopping strategy" } }

    val stopStrategies = mutableListOf<StopStrategy>()

    val stepLimit = options.stepLimit
    if (stepLimit != null) {
        stopStrategies.add(StepLimitStopStrategy(stepLimit, stepsStatistics.value))
    }
    if (options.stopOnCoverage in 1..100) {
        val coverageStatistics = requireNotNull(coverageStatisticsFactory()) {
            "Coverage statistics is required for stopping on expected coverage achieved"
        }
        val coverageStopStrategy = StopStrategy { coverageStatistics.getTotalCoverage() >= options.stopOnCoverage }
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

    if (options.timeout < Duration.INFINITE) {
        val timeStatistics = requireNotNull(timeStatisticsFactory()) { "Time statistics is required for stopping on timeout" }
        stopStrategies.add(TimeoutStopStrategy(options.timeout, timeStatistics))
    }

    val stepsFromLastCovered = options.stepsFromLastCovered
    if (stepsFromLastCovered != null && getCollectedStatesCount != null) {
        val stepsFromLastCoveredStopStrategy = StepsFromLastCoveredStopStrategy(
            stepsFromLastCovered.toULong(),
            getCollectedStatesCount,
            stepsStatistics.value
        )
        stopStrategies.add(stepsFromLastCoveredStopStrategy)
    }

    if (options.stopOnTargetsReached) {
        stopStrategies.add(TargetsReachedStopStrategy(targets))
    }

    if (stopStrategies.isEmpty()) {
        return StopStrategy { false }
    }
    if (stopStrategies.size == 1) {
        return stopStrategies.first()
    }
    return GroupedStopStrategy(stopStrategies)
}
