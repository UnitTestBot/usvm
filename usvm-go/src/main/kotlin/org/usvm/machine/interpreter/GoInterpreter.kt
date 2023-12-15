package org.usvm.machine.interpreter

import mu.KLogging
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UInterpreter
import org.usvm.api.GoApi
import org.usvm.bridge.Bridge
import org.usvm.bridge.GoJnaBridge
import org.usvm.bridge.GoJniBridge
import org.usvm.domain.GoInst
import org.usvm.domain.GoMethod
import org.usvm.domain.GoType
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.*
import org.usvm.machine.state.GoState
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

typealias GoStepScope = StepScope<GoState, GoType, GoInst, GoContext>

class GoInterpreter(
    private val bridge: Bridge,
    private val ctx: GoContext,
    private var forkBlackList: UForkBlackList<GoState, GoInst> = UForkBlackList.createDefault(),
) : UInterpreter<GoState>() {
    fun getInitialState(method: GoMethod, targets: List<GoTarget> = emptyList()): GoState {
        val state = GoState(ctx, method, targets = UTargetsSet.from(targets))
        val methodInfo = bridge.methodInfo(method)

        logger.debug("Method: {}, info: {}", method.name, methodInfo)

        ctx.setArgsCount(methodInfo.parametersCount)

        val solver = ctx.solver<GoType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        val entrypointInst = bridge.entryPoints(method)[0]
        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(methodInfo.parametersCount, methodInfo.localsCount)
        state.newInst(entrypointInst)
        return state
    }

    override fun step(state: GoState): StepResult<GoState> {
//        logger.debug("Step: {}", state.lastInst)

        val inst = state.lastInst
        val scope = GoStepScope(state, forkBlackList)
        val newInst = bridge.step(GoApi(ctx, scope), inst)
        if (!newInst.isEmpty()) {
            state.newInst(newInst)
        }
        return scope.stepResult()
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}