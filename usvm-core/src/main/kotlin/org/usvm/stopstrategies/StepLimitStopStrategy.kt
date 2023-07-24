package org.usvm.stopstrategies

/**
 * [StopStrategy] which stops when the [limit] number of steps is reached.
 */
class StepLimitStopStrategy(private val limit: ULong) : StopStrategy {
    private var counter = 0UL

    override fun shouldStop(): Boolean {
        return counter++ > limit
    }
}
