package org.usvm.machine.call.intrinsic

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
    private val implementation: (TsState, List<UExpr<*>>) -> TsUnknownCallModelExecution?,
) : TsUnknownCallModel {
    override val id: String = "$idPrefix.$methodName"
    override val target = TsUnknownCallTarget(
        methodName = methodName,
        enclosingClassName = enclosingClassName,
        failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
    )

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? {
        if (call.receiver != null || call.arguments.size != arity) {
            return null
        }

        val inputs = call.arguments.map { argument -> argument.resolved ?: return null }
        return implementation(state, inputs)
    }
}
