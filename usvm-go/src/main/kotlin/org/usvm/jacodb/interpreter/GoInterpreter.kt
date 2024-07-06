package org.usvm.jacodb.interpreter

import mu.KLogging
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoType
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.jacodb.GoContext
import org.usvm.jacodb.GoExprVisitor
import org.usvm.jacodb.GoInstVisitor
import org.usvm.jacodb.GoMethodInfo
import org.usvm.jacodb.GoTarget
import org.usvm.jacodb.state.GoFlowStatus
import org.usvm.jacodb.state.GoState
import org.usvm.solver.USatResult
import org.usvm.statistics.ApplicationGraph
import org.usvm.targets.UTargetsSet

typealias GoStepScope = StepScope<GoState, GoType, GoInst, GoContext>

class GoInterpreter(
    private val ctx: GoContext,
    private val applicationGraph: ApplicationGraph<GoMethod, GoInst>,
    private var forkBlackList: UForkBlackList<GoState, GoInst> = UForkBlackList.createDefault(),
) : UInterpreter<GoState>() {
    fun getInitialState(method: GoMethod, targets: List<GoTarget> = emptyList()): GoState = with(ctx) {
        val state = GoState(ctx, method, targets = UTargetsSet.from(targets))

        val solver = solver<GoType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        val entrypoint = method.blocks[0].insts[0]
        val localsCount = method.blocks.flatMap { it.insts }.filterIsInstance<GoAssignInst>().size
        val argumentsCount = method.operands.size

        ctx.setMethodInfo(method, GoMethodInfo(localsCount, argumentsCount))

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(argumentsCount, localsCount)
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

        val exprVisitor = GoExprVisitor(ctx, scope, applicationGraph)
        val instVisitor = GoInstVisitor(ctx, scope, exprVisitor, applicationGraph)
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
