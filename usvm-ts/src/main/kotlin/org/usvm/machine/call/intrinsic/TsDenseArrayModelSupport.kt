package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsStringType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.evalTypeEquals
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsEtsIrUnknownCallModelInputAdapter
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.state.TsState
import org.usvm.util.arrayStorageType
import org.usvm.util.hasDenseArrayShape
import org.usvm.util.mkArrayLengthLValue

internal const val SOURCE_ARRAY_MODEL_CAPACITY = 16

internal object TsDenseArrayModelSupport {
    val denseReceiverDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        val (array, arrayType) = state.concreteArray(call, inputs)
            ?: return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        if (!state.hasDenseArrayShape(array, arrayType)) {
            return@TsEtsIrUnknownCallModelDomainGuard state.ctx.falseExpr
        }
        state.boundedArrayGuard(array, arrayType, maximumLength = SOURCE_ARRAY_MODEL_CAPACITY)
    }

    val joinAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs() ?: return@TsEtsIrUnknownCallModelInputAdapter null
        if (call.arguments.size > 1) return@TsEtsIrUnknownCallModelInputAdapter null
        val separator = inputs.getOrNull(1)
            ?.takeUnless { it == state.ctx.mkUndefinedValue() }
            ?: state.mkInitializedStringConstant(",")
        if (separator !is UConcreteHeapRef || separator in state.associatedFunction ||
            with(state.ctx) { separator.hasFakeValueBranch() }
        ) {
            return@TsEtsIrUnknownCallModelInputAdapter null
        }
        listOf(inputs.first(), separator)
    }

    val joinDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        with(state.ctx) {
            val receiverGuard = denseReceiverDomain.evaluate(state, call, inputs)
            val separatorGuard = state.memory.types.evalTypeEquals(inputs[1].asExpr(addressSort), EtsStringType)
            mkAnd(
                receiverGuard,
                separatorGuard,
            )
        }
    }

    val reduceAdapter = TsEtsIrUnknownCallModelInputAdapter { state, call ->
        val inputs = call.resolvedInstanceInputs() ?: return@TsEtsIrUnknownCallModelInputAdapter null
        if (call.arguments.size !in 1..2) return@TsEtsIrUnknownCallModelInputAdapter null
        val callback = inputs[1] as? UConcreteHeapRef
            ?: return@TsEtsIrUnknownCallModelInputAdapter null
        if (callback !in state.associatedFunction) return@TsEtsIrUnknownCallModelInputAdapter null
        listOf(
            inputs.first(),
            callback,
            inputs.getOrNull(2) ?: state.ctx.mkUndefinedValue(),
            state.ctx.mkBool(call.arguments.size == 2),
        )
    }
}

internal fun TsState.concreteArray(
    call: TsUnknownCall,
    inputs: List<UExpr<*>>,
): Pair<UConcreteHeapRef, EtsArrayType>? {
    val receiver = inputs.firstOrNull() as? UConcreteHeapRef ?: return null
    if (with(ctx) { receiver.hasFakeValueBranch() }) return null

    val staticType = call.receiver?.source?.type ?: return null
    val arrayType = arrayStorageType(receiver, staticType) as? EtsArrayType ?: return null
    if (arrayType.dimensions != 1) return null

    return receiver to arrayType
}

internal fun TsState.boundedArrayGuard(
    array: UConcreteHeapRef,
    arrayType: EtsArrayType,
    maximumLength: Int,
): UBoolExpr = with(ctx) {
    val length = memory.read(mkArrayLengthLValue(array, arrayType))
    val minimumLength = mkBv(0)
    val maximumLengthExpr = mkBv(maximumLength)
    mkAnd(
        memory.types.evalIsSubtype(array, arrayType),
        mkBvSignedGreaterOrEqualExpr(length, minimumLength),
        mkBvSignedLessOrEqualExpr(length, maximumLengthExpr),
    )
}

internal fun TsUnknownCall.resolvedInstanceInputs(): List<UExpr<*>>? {
    val resolvedReceiver = receiver?.resolved ?: return null
    val resolvedArguments = arguments.map { argument -> argument.resolved ?: return null }

    return listOf(resolvedReceiver) + resolvedArguments
}
