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
    private val strategies: List<StopStrategy>,
) : StopStrategy {
    constructor(vararg strategies: StopStrategy) : this(strategies.toList())

    override fun shouldStop() = strategies.any { it.shouldStop() }

    fun stopReason(): String = strategies
        .filter { it.shouldStop() }
        .joinToString(prefix = "Stop reasons: ", separator = ", ") { "${it::class.simpleName ?: it}" }
}

fun stopReason(stopStrategy: StopStrategy): String =
    if (stopStrategy is GroupedStopStrategy) {
        stopStrategy.stopReason()
    } else {
        "Stop reason: ${stopStrategy::class.simpleName ?: stopStrategy}"
    }
