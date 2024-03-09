package org.usvm.machine.interpreter

import mu.KLogging
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.api.Api
import org.usvm.bridge.GoBridge
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.GoContext
import org.usvm.machine.GoInst
import org.usvm.machine.GoMethod
import org.usvm.machine.GoTarget
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.state.GoState
import org.usvm.machine.type.GoType
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

typealias GoStepScope = StepScope<GoState, GoType, GoInst, GoContext>

class GoInterpreter(
    private val ctx: GoContext,
    private val bridge: GoBridge,
    private var forkBlackList: UForkBlackList<GoState, GoInst> = UForkBlackList.createDefault(),
) : UInterpreter<GoState>() {
    fun getInitialState(method: GoMethod, targets: List<GoTarget> = emptyList()): GoState = with(ctx) {
        val state = GoState(ctx, method, targets = UTargetsSet.from(targets))

        val methodInfo = bridge.methodInfo(method)
        logger.debug("Method: {}, info: {}", method, methodInfo)
        setMethodInfo(method, methodInfo)

        val solver = solver<GoType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        val entrypoint = bridge.entryPoints(method).first[0]
        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(methodInfo.parametersCount, methodInfo.variablesCount)
        state.newInst(entrypoint)
        return state
    }

    override fun step(state: GoState): StepResult<GoState> {
        logger.debug("Step: {} ({})", bridge.instInfo(state.currentStatement), state.currentStatement)

        val inst = state.currentStatement
        val scope = GoStepScope(state, forkBlackList)
        if (state.methodResult is GoMethodResult.Panic) {
            state.panic()
            return scope.stepResult()
        }

        val newInst = bridge.step(Api(ctx, bridge, scope), inst)
        if (newInst != 0L) {
            state.newInst(newInst)
        }
        return scope.stepResult()
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
