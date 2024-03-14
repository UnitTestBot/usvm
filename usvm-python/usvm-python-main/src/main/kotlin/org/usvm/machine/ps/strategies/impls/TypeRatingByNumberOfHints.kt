package org.usvm.machine.ps.strategies.impls

import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.DelayedForkStrategy
import org.usvm.machine.ps.strategies.TypeRating

class TypeRatingByNumberOfHints<DFState : DelayedForkState> : DelayedForkStrategy<DFState> {
    override fun chooseTypeRating(state: DFState): TypeRating {
        require(state.size > 0)
        val idx = List(state.size) { it }.maxBy {
            val rating = state.getAt(it)
            rating.numberOfHints - rating.numberOfUsed
        }
        return state.getAt(idx)
    }
}
