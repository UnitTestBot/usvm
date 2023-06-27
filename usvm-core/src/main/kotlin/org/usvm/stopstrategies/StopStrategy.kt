package org.usvm.stopstrategies

/**
 * @see shouldStop
 */
fun interface StopStrategy {

    /**
     * If true, symbolic execution process is terminated.
     */
    fun shouldStop(): Boolean
}

class GroupedStopStrategy(
    private val strategies: List<StopStrategy>
) : StopStrategy {
    constructor(vararg strategies: StopStrategy) : this(strategies.toList())

    override fun shouldStop() = strategies.any { it.shouldStop() }
}
