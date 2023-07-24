package org.usvm.stopstrategies

/**
 * @see shouldStop
 */
fun interface StopStrategy {

    /**
     * If true, symbolic execution process is terminated.
     */
    fun shouldStop(): Boolean

    fun stopReason(): String = "Stop reason: ${this::class.simpleName ?: this}"
}

class GroupedStopStrategy(
    private val strategies: List<StopStrategy>,
) : StopStrategy {
    constructor(vararg strategies: StopStrategy) : this(strategies.toList())

    override fun shouldStop() = strategies.any { it.shouldStop() }

    override fun stopReason(): String = strategies
        .filter { it.shouldStop() }
        .joinToString(prefix = "Stop reasons: ", separator = ", ") { "${it::class.simpleName ?: it}" }
}
