package org.usvm.machine.ps.strategies.impls

import org.usvm.machine.ps.strategies.DelayedForkGraph
import org.usvm.machine.ps.strategies.DelayedForkState
import org.usvm.machine.ps.strategies.PyPathSelectorAction
import kotlin.random.Random


abstract class Action<DFState : DelayedForkState, in DFGraph : DelayedForkGraph<DFState>> {
    abstract fun isAvailable(graph: DFGraph): Boolean
    abstract fun makeAction(graph: DFGraph, random: Random): PyPathSelectorAction<DFState>
}
