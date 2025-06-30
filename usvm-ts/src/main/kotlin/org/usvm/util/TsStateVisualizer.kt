package org.usvm.util

import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver

class TsStateVisualizer : TsInterpreterObserver, UMachineObserver<TsState> {
    override fun onStatePeeked(state: TsState) {
        state.renderGraph()
    }
}