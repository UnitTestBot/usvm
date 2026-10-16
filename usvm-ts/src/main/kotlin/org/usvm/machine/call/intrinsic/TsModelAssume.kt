package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.state.TsState

/**
 * Restricts a private source-model path without admitting the condition's negative domain.
 * Source artifacts declare `TsModelPrimitives.assume(condition: boolean): void` and depend on [id].
 * The argument must already be a Boolean term; evaluating short-circuit source expressions may itself branch.
 */
internal object TsModelAssume : TsBuiltInUnknownCallModel {
    override val id: String = "ts.model.assume"
    override val target = TsUnknownCallTarget(
        methodName = "assume",
        enclosingClassName = "TsModelPrimitives",
        failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
    )

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
        val helperOwner = call.callee.enclosingClass
        val caller = call.callSite.location.method
        val callerOwner = caller.signature.enclosingClass
        val sameArtifact = helperOwner.file == callerOwner.file && helperOwner.namespace == null
        val privateSourceCaller = state.ctx.isSourceModelClass(callerOwner)
        val ownsActiveFrame = state.isUnknownCallModelFileActive(helperOwner)
        if (!sameArtifact || !privateSourceCaller || !ownsActiveFrame) return null

        check(call.receiver == null && call.arguments.size == 1) {
            "Private model assume requires one boolean argument"
        }
        val condition = requireNotNull(call.arguments.single().resolved) {
            "Private model assume requires an evaluated boolean argument"
        }
        check(condition.sort == state.ctx.boolSort) {
            "Private model assume requires a boolean term, got ${condition.sort}"
        }

        val successor = TsUnknownCallModelSuccessor(
            guard = condition.asExpr(state.ctx.boolSort),
            completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
        )
        return TsUnknownCallModelExecution(successors = listOf(successor))
    }
}
