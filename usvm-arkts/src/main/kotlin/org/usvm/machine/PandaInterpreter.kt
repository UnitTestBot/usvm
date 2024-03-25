package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.StepResult
import org.usvm.UInterpreter
import org.usvm.machine.state.PandaState
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

class PandaInterpreter(private val ctx: PandaContext) : UInterpreter<PandaState>() {
    override fun step(state: PandaState): StepResult<PandaState> {
        TODO("Not yet implemented")
    }

    fun getInitialState(method: PandaMethod, targets: List<PandaTarget>): PandaState {
        val state = PandaState(ctx, method, targets = UTargetsSet.from(targets))

        val solver = ctx.solver<PandaType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parameters.size, 15) // TODO locals count)
        state.pathNode += method.instructions.first()

        return state
    }
}
