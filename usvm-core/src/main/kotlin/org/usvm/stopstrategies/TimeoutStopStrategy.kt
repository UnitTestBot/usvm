package org.usvm.stopstrategies

/**
 * [StopStrategy] implementation which stops execution on timeout. Time measurement is started on first
 * [shouldStop] call.
 */
class TimeoutStopStrategy(private val timeoutMs: Long, private val getCurrentTime: () -> Long) : StopStrategy {
    private var startTime: Long? = null

    override fun shouldStop(): Boolean {
        val currentTime = getCurrentTime()
        val startTimeValue = startTime

        if (startTimeValue == null) {
            startTime = currentTime
            return false
        }

        return currentTime - startTimeValue > timeoutMs
    }
}
