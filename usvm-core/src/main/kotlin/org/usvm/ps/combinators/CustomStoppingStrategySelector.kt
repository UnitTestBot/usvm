package org.usvm.ps.combinators

import org.usvm.UPathSelector
import org.usvm.ps.stopstregies.StoppingStrategy

class CustomStoppingStrategySelector<State>(
    val pathSelector: UPathSelector<State>,
    val stoppingStrategy: StoppingStrategy
) : UPathSelector<State> by pathSelector {
    override fun isEmpty(): Boolean = stoppingStrategy.shouldStop() || pathSelector.isEmpty()
}