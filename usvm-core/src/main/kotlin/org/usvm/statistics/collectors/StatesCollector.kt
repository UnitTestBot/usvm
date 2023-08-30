package org.usvm.statistics.collectors

import org.usvm.statistics.UMachineObserver

interface StatesCollector<State> : UMachineObserver<State> {
    val collectedStates: List<State>
}
