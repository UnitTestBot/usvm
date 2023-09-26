package org.usvm.stopstrategies

import org.usvm.targets.UTarget

/**
 * A stop strategy which stops when all terminal targets in [targets] are reached.
 */
class TargetsReachedStopStrategy(private val targets: Collection<UTarget<*, *, *>>) : StopStrategy {
    override fun shouldStop(): Boolean = targets.all { it.isRemoved }
}
