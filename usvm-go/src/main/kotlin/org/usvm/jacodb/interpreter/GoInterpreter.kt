package org.usvm.jacodb.interpreter

import mu.KLogging
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.jacodb.GoContext
import org.usvm.jacodb.GoExprVisitorImpl
import org.usvm.jacodb.GoInst
import org.usvm.jacodb.GoInstVisitorImpl
import org.usvm.jacodb.GoMethod
import org.usvm.jacodb.GoNullInst
import org.usvm.jacodb.GoTarget
import org.usvm.jacodb.GoType
import org.usvm.jacodb.state.GoFlowStatus
import org.usvm.jacodb.state.GoState
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

typealias GoStepScope = StepScope<GoState, GoType, GoInst, GoContext>

class GoInterpreter(
    private val ctx: GoContext,
    private var forkBlackList: UForkBlackList<GoState, GoInst> = UForkBlackList.createDefault(),
) : UInterpreter<GoState>() {
    fun getInitialState(method: GoMethod, targets: List<GoTarget> = emptyList()): GoState = with(ctx) {
        val state = GoState(ctx, method, targets = UTargetsSet.from(targets))

        val solver = solver<GoType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        val entrypoint = method.blocks[0].insts[0]
        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(2, 1)
        state.newInst(entrypoint)
        return state
    }

    override fun step(state: GoState): StepResult<GoState> {
        val inst = state.currentStatement
        val method = state.lastEnteredMethod
        val scope = GoStepScope(state, forkBlackList)

        logger.debug("State {}: Step: {}", state.id, inst)

        if (state.isExceptional && state.data.flowStatus != GoFlowStatus.DEFER) {
            if (state.data.getDeferredCalls(method).isEmpty()) {
                state.handlePanic()
                return scope.stepResult()
            } else {
                state.runDefers(method, null)
            }
        }

        val exprVisitor = GoExprVisitorImpl(ctx, scope)
        val instVisitor = GoInstVisitorImpl(ctx, scope, exprVisitor)
        val nextInst: GoInst = when(state.data.flowStatus) {
            GoFlowStatus.NORMAL -> state.getRecoverInst(method)
            GoFlowStatus.DEFER -> state.getDeferInst(method, inst)
        } ?: inst.accept(instVisitor)

        if (nextInst !is GoNullInst) {
            state.newInst(nextInst)
        }
        return scope.stepResult()
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
