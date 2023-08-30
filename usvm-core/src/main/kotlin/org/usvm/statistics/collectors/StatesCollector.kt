package org.usvm.statistics.collectors

import org.usvm.statistics.UMachineObserver

/**
 * Interface for [UMachineObserver]s which are able to
 * collect states.
 */
interface StatesCollector<State> : UMachineObserver<State> {
    /**
     * Current list of collected states.
     */
    val collectedStates: List<State>
}
