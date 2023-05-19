package org.usvm.ps.stopstregies

fun interface StoppingStrategy {
    fun shouldStop(): Boolean
}

class GroupedStoppingStrategy(
    val strategies: List<StoppingStrategy>
) {
    constructor(vararg strategies: StoppingStrategy) : this(strategies.toList())

    fun shouldStop() = strategies.any { it.shouldStop() }
}