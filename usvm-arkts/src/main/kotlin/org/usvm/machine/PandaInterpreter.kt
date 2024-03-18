package org.usvm.machine

import org.usvm.StepResult
import org.usvm.UInterpreter
import org.usvm.machine.state.PandaState

class PandaInterpreter(private val ctx: PandaContext) : UInterpreter<PandaState>() {
    override fun step(state: PandaState): StepResult<PandaState> {
        TODO("Not yet implemented")
    }
}