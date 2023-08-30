package org.usvm.stopstrategies

import org.usvm.UTarget

class TargetsReachedStopStrategy(private val targets: Collection<UTarget<*, *, *, *>>) : StopStrategy {
    override fun shouldStop(): Boolean = targets.all { it.isRemoved }
}
