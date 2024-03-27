package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaCallInst
import org.jacodb.panda.dynamic.api.PandaIfInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaThrowInst
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.state.PandaMethodResult
import org.usvm.machine.state.PandaState
import org.usvm.machine.state.lastStmt
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

typealias PandaStepScope = StepScope<PandaState, PandaType, PandaInst, PandaContext>

@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
class PandaInterpreter(private val ctx: PandaContext) : UInterpreter<PandaState>() {

    private val forkBlackList: UForkBlackList<PandaState, PandaInst> = UForkBlackList.createDefault()

    override fun step(state: PandaState): StepResult<PandaState> {
        val stmt = state.lastStmt
        val scope = StepScope(state, forkBlackList)

        val result = state.methodResult
        if (result is PandaMethodResult.PandaException) {
            TODO()
        }

        when (stmt) {
            is PandaIfInst -> visitIfStmt(scope, stmt)
            is PandaReturnInst -> visitReturnStmt(scope, stmt)
            is PandaAssignInst -> visitAssignInst(scope, stmt)
            is PandaCallInst -> visitCallStmt(scope, stmt)
            is PandaThrowInst -> visitThrowStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }

        return scope.stepResult()
    }

    fun getInitialState(method: PandaMethod, targets: List<PandaTarget>): PandaState {
        val state = PandaState(ctx, method, targets = UTargetsSet.from(targets))

        val solver = ctx.solver<PandaType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parameters.size, method.localVarsCount)
        state.pathNode += method.instructions.first()

        return state
    }

    private fun visitIfStmt(scope: PandaStepScope, stmt: PandaIfInst) {
        TODO()
    }
    private fun visitReturnStmt(scope: PandaStepScope, stmt: PandaReturnInst) {
        TODO()
    }
    private fun visitAssignInst(scope: PandaStepScope, stmt: PandaAssignInst) {
        TODO()
    }
    private fun visitCallStmt(scope: PandaStepScope, stmt: PandaCallInst) {
        TODO()
    }
    private fun visitThrowStmt(scope: PandaStepScope, stmt: PandaThrowInst) {
        TODO()
    }

}