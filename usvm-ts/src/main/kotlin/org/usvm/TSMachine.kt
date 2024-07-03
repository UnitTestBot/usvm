package org.usvm

import org.usvm.state.TSState

class TSMachine(
    private val options: UMachineOptions
) : UMachine<TSState>() {
    override fun close() {
        TODO("Not yet implemented")
    }
}
