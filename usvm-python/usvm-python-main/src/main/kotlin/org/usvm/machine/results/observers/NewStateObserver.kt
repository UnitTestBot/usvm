package org.usvm.machine.results.observers

import org.usvm.machine.PyState

abstract class NewStateObserver {
    abstract fun onNewState(state: PyState)
}

object EmptyNewStateObserver : NewStateObserver() {
    override fun onNewState(state: PyState) = run {}
}
