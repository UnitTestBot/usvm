package org.usvm

import io.ksmt.utils.asExpr
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsMethod
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.memory.URegisterStackLValue
import org.usvm.solver.USatResult
import org.usvm.state.TSMethodResult
import org.usvm.state.TSState
import org.usvm.state.lastStmt
import org.usvm.targets.UTargetsSet

class TSInterpreter(
    private val ctx: TSContext,
    private val applicationGraph: TSApplicationGraph,
) : UInterpreter<TSState>() {
    private val forkBlackList: UForkBlackList<TSState, EtsStmt> = UForkBlackList.createDefault()

    override fun step(state: TSState): StepResult<TSState> {
        val stmt = state.lastStmt
        val scope = StepScope(state, forkBlackList)

        val result = state.methodResult
        if (result is TSMethodResult.TSException) {
            // TODO catch processing
            scope.doWithState {
                val returnSite = callStack.pop()

                if (callStack.isNotEmpty()) {
                    memory.stack.pop()
                }

                if (returnSite != null) {
                    newStmt(returnSite)
                }
            }

            return scope.stepResult()
        }

        // TODO: interpreter
        stmt.nextStmt?.let { nextStmt ->
            scope.doWithState { newStmt(nextStmt) }
        }

        return scope.stepResult()
    }

    fun getInitialState(method: EtsMethod, targets: List<TSTarget>): TSState {
        val state = TSState(ctx, method, targets = UTargetsSet.from(targets))

        with(ctx) {
            val params = List(method.parameters.size) { idx ->
                URegisterStackLValue(addressSort, idx)
            }
            val refs = params.map { state.memory.read(it) }

            // TODO check correctness of constraints and process this instance
            state.pathConstraints += mkAnd(refs.map { mkEq(it.asExpr(addressSort), nullRef).not() })
        }

        val solver = ctx.solver<EtsType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parameters.size, method.localsCount)
        state.pathNode += method.cfg.instructions.first()

        return state
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt get() = applicationGraph.successors(this).firstOrNull()
}
