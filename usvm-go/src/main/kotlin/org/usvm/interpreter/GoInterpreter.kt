package org.usvm.interpreter

import mu.KLogging
import org.jacodb.go.api.GoAssignInst
import org.jacodb.go.api.GoFunction
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
        state.data.flowStack.add(GoFlowStatus.NORMAL)

        val init = pkg.findMethod(INIT_FUNCTION)
        setMethodInfo(init)

        state.addCall(GoCall(init, applicationGraph.entryPoints(init).first(), emptyArray()), entrypoint)
        return state
    }

    override fun step(state: GoState): StepResult<GoState> {
        val inst = state.currentStatement
        val scope = GoStepScope(state, forkBlackList)
        val exprVisitor = GoExprVisitor(ctx, pkg, scope, applicationGraph)
        val instVisitor = GoInstVisitor(ctx, pkg, scope, exprVisitor, applicationGraph)

        logger.debug("State {}: Step: {}", state.id, inst)

        val nextInst = next(state, inst, instVisitor)
        if (nextInst !is GoNullInst) {
            state.newInst(nextInst)
        }
        return scope.stepResult()
    }

    private fun next(state: GoState, inst: GoInst, instVisitor: GoInstVisitor): GoInst {
        val method = state.lastEnteredMethod
        return when (state.data.flowStatus) {
            GoFlowStatus.NORMAL -> inst.accept(instVisitor)
            GoFlowStatus.DEFER -> {
                val deferred = state.data.getDeferredCalls(method)
                if (deferred.isEmpty()) {
                    state.data.flowStack.removeLast()
                    return next(state, inst, instVisitor)
                }

                state.addCall(deferred.removeLast(), inst)
                return GoNullInst(method)
            }

            GoFlowStatus.PANIC -> {
                if (!state.isExceptional) { // recovered
                    state.data.flowStack.removeLast()
                    val function = method as GoFunction
                    function.setRecover()
                    return function.recover!!.instructions.first()
                }

                if (state.data.getDeferredCalls(method).isEmpty()) {
                    state.handlePanic()
                    return GoNullInst(method)
                }

                state.runDefers()
                return next(state, inst, instVisitor)
            }
        }
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
