package org.usvm.stopstrategies

import org.usvm.statistics.TimeStatistics
import kotlin.time.Duration

/**
 * [StopStrategy] implementation which stops execution on timeout. Time measurement is started on first
 * [shouldStop] call.
 */
class TimeoutStopStrategy(private val timeout: Duration, private val timeStatistics: TimeStatistics<*, *>) : StopStrategy {
    override fun shouldStop(): Boolean =
        timeStatistics.runningTime > timeout
}
