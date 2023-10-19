package org.usvm.statistics.collectors

import org.usvm.UState
import org.usvm.statistics.UMachineObserver

/**
 * Interface for [UMachineObserver]s which are able to
 * collect states.
 */
interface StatesCollector<State : UState<*, *, *, *, *, *>> : UMachineObserver<State> {
    /**
     * Current list of collected states.
     */
    val collectedStates: List<State>
}
