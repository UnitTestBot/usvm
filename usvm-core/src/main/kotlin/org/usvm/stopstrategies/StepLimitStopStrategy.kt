package org.usvm.stopstrategies

import org.usvm.statistics.StepsStatistics

/**
 * [StopStrategy] which stops when the [limit] number of steps is reached.
 */
class StepLimitStopStrategy(private val limit: ULong, private val stepsStatistics: StepsStatistics<*, *>) : StopStrategy {
    override fun shouldStop(): Boolean = stepsStatistics.totalSteps > limit
}
