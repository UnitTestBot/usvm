package org.usvm.interpreter

import mu.KLogging
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoType
import org.usvm.GoCall
import org.usvm.GoContext
import org.usvm.GoExprVisitor
import org.usvm.GoInstVisitor
import org.usvm.GoPackage
import org.usvm.GoTarget
import org.usvm.INIT_FUNCTION
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.solver.USatResult
import org.usvm.state.GoFlowStatus
import org.usvm.state.GoState
import org.usvm.statistics.ApplicationGraph
import org.usvm.targets.UTargetsSet

typealias GoStepScope = StepScope<GoState, GoType, GoInst, GoContext>

class GoInterpreter(
    private val ctx: GoContext,
    private val pkg: GoPackage,
    private val applicationGraph: ApplicationGraph<GoMethod, GoInst>,
    private var forkBlackList: UForkBlackList<GoState, GoInst> = UForkBlackList.createDefault(),
) : UInterpreter<GoState>() {
    fun getInitialState(method: GoMethod, targets: List<GoTarget> = emptyList()): GoState = with(ctx) {
        val initOwnership = MutabilityOwnership()
        val state = GoState(ctx, initOwnership, method, targets = UTargetsSet.from(targets))

        val solver = solver<GoType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        val entrypoint = method.blocks[0].instructions[0]
        val localsCount = method.blocks.flatMap { it.instructions }.filterIsInstance<GoAssignInst>().size
        val argumentsCount = method.parameters.size

        setMethodInfo(method)
        for (global in pkg.globals) {
            addGlobal(global, state.mkPointer(global.type))
        }

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(argumentsCount, localsCount)

        val init = pkg.findMethod(INIT_FUNCTION)
        setMethodInfo(init)

        state.addCall(GoCall(init, applicationGraph.entryPoints(init).first(), emptyArray()), entrypoint)
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

        val exprVisitor = GoExprVisitor(ctx, pkg, scope, applicationGraph)
        val instVisitor = GoInstVisitor(ctx, pkg, scope, exprVisitor, applicationGraph)
        val nextInst: GoInst = when (state.data.flowStatus) {
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
