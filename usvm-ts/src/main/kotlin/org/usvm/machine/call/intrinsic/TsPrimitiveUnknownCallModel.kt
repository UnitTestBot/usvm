package org.usvm.machine.call.intrinsic

import org.jacodb.ets.model.EtsClassSignature
import org.usvm.UExpr
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.state.TsState

/** Dispatches a helper method used by an EtsIR source model. */
internal class TsPrimitiveUnknownCallModel(
    idPrefix: String,
    methodName: String,
    enclosingClassName: String,
    private val arity: Int,
    private val helperClassSignature: () -> EtsClassSignature,
    private val sourceClassSignature: () -> EtsClassSignature,
    private val owningModelIds: () -> Set<String>,
    private val implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
) : TsUnknownCallModel {
    override val id: String = "$idPrefix.$methodName"
    override val target = TsUnknownCallTarget(
        methodName = methodName,
        enclosingClassName = enclosingClassName,
        failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
    )

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
        val helperOwnerMatches = call.callee.enclosingClass == helperClassSignature()
        val callerSignature = call.callSite.location.method.signature
        val sourceCallerMatches = callerSignature.enclosingClass == sourceClassSignature()
        val sourceModelIsActive = owningModelIds().any(state::isUnknownCallModelActive)
        if (!helperOwnerMatches || !sourceCallerMatches || !sourceModelIsActive) return null

        if (call.receiver != null || call.arguments.size != arity) {
            return null
        }

        val inputs = call.arguments.map { argument -> argument.resolved ?: return null }
        return implementation(state, inputs)
    }
}
