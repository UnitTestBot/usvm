package org.usvm.stopstrategies

import org.usvm.statistics.TimeStatistics

/**
 * [StopStrategy] which stops when the [limit] number of steps is reached.
 */
class StepLimitStopStrategy(private val limit: ULong, private val timeStatistics: TimeStatistics<*, *>) : StopStrategy {
    override fun shouldStop(): Boolean = timeStatistics.totalSteps > limit
}
