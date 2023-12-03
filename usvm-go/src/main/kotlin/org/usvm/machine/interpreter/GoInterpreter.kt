package org.usvm.machine.interpreter

import org.usvm.StepResult
import org.usvm.UInterpreter
import org.usvm.bridge.GoBridge
import org.usvm.machine.state.GoState

class GoInterpreter(
    private val bridge: GoBridge
) : UInterpreter<GoState>() {
    override fun step(state: GoState): StepResult<GoState> {
        println(bridge.getMain())
        TODO("Not yet implemented")
    }
}