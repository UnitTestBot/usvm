package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KLogging
import org.usvm.StepResult
import org.usvm.UHeapRef
import org.usvm.UInterpreter
import org.usvm.bridge.GoBridge
import org.usvm.machine.*
import org.usvm.machine.state.GoState
import org.usvm.memory.URegisterStackLValue
import org.usvm.solver.USatResult
import org.usvm.targets.UTargetsSet

class GoInterpreter(
    private val bridge: GoBridge,
    private val ctx: GoContext,
) : UInterpreter<GoState>() {
    fun getInitialState(method: GoMethod, targets: List<GoTarget> = emptyList()): GoState {
        val state = GoState(ctx, method, targets = UTargetsSet.from(targets))
        val entrypointArguments = mutableListOf<Pair<GoType, UHeapRef>>()
        val methodInfo = bridge.methodInfo(method)

        methodInfo.parameters.forEachIndexed { idx, type ->
            with(ctx) {
                // TODO is it really addressSort for every type?
                val argumentLValue = URegisterStackLValue(addressSort, method.localIdx(idx))
                val ref = state.memory.read(argumentLValue).asExpr(addressSort)
                state.pathConstraints += mkIsSubtypeExpr(ref, type)

                entrypointArguments += type to ref
            }
        }

        val solver = ctx.solver<GoType>()
        val model = (solver.check(state.pathConstraints) as USatResult).model
        state.models = listOf(model)

        val entrypointInst = bridge.entryPoints(method)[0]
        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(methodInfo.parameters.size, methodInfo.localsCount)
        state.newInst(entrypointInst)
        return state
    }

    override fun step(state: GoState): StepResult<GoState> {
        TODO("Not yet implemented")
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}