package org.usvm.machine.results.observers

import org.usvm.machine.PyState

interface NewStateObserver {
    fun onNewState(state: PyState)
}

object EmptyNewStateObserver : NewStateObserver {
    override fun onNewState(state: PyState) = run {}
}
