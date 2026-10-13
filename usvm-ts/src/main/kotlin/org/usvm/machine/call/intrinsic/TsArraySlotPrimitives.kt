package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.call.TsEtsIrUnknownCallModelDomainGuard
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.expr.mkFpToUint32AfterValidation
import org.usvm.machine.expr.mkValidArrayIndexProperty
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.arrayStorageType
import org.usvm.util.hasDenseArrayShape
import org.usvm.util.knownArrayIndexPresence
import org.usvm.util.markDenseArrayOwnSlots
import org.usvm.util.mkArrayLengthLValue

/** Own-slot metadata operations used by the source reverse and unshift algorithms. */
internal object TsArraySlotPrimitives {
    val reverseDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        knownSlotGuard(state, call, inputs, additionalSlots = 0)
    }

    val unshiftDomain = TsEtsIrUnknownCallModelDomainGuard { state, call, inputs ->
        knownSlotGuard(state, call, inputs, additionalSlots = call.arguments.size)
    }

    private fun knownSlotGuard(
        state: TsState,
        call: TsUnknownCall,
        inputs: List<UExpr<*>>,
        additionalSlots: Int,
    ): UBoolExpr {
        val (array, type) = state.concreteArray(call, inputs) ?: return state.ctx.falseExpr
        val hasSlotProof = array in state.knownArrayPropertyPresence || state.hasDenseArrayShape(array, type)
        if (!hasSlotProof) return state.ctx.falseExpr

        return state.boundedArrayGuard(array, type, maximumLength = SOURCE_ARRAY_MODEL_CAPACITY - additionalSlots)
    }

    fun prepareOwnSlots(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? {
        val array = inputs.single() as? UConcreteHeapRef ?: return null
        val type = state.arrayStorageType(array, EtsArrayType(EtsUnknownType, dimensions = 1)) as? EtsArrayType
            ?: return null
        val hasKnownSlots = array in state.knownArrayPropertyPresence
        val hasSlotProof = hasKnownSlots || state.hasDenseArrayShape(array, type)
        if (!hasSlotProof) return null

        val guard = state.boundedArrayGuard(array, type, maximumLength = SOURCE_ARRAY_MODEL_CAPACITY)
        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.Normal {
                if (!hasKnownSlots) {
                    val length = memory.read(mkArrayLengthLValue(array, type))
                    markDenseArrayOwnSlots(array = array, length = length, capacity = SOURCE_ARRAY_MODEL_CAPACITY)
                }

                ctx.mkUndefinedValue()
            },
        )

        return TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = state.ctx.mkNot(guard),
        )
    }

    fun hasOwnSlot(state: TsState, inputs: List<UExpr<*>>): TsUnknownCallModelExecution? = with(state.ctx) {
        val array = inputs[0] as? UConcreteHeapRef ?: return null
        val number = inputs[1].takeIf { it.sort == fp64Sort }?.asExpr(fp64Sort) ?: return null
        val guard = mkValidArrayIndexProperty(number)
        val index = mkFpToUint32AfterValidation(number, guard).asExpr(sizeSort)
        val present = state.knownArrayIndexPresence(array, index) ?: return null
        val successor = TsUnknownCallModelSuccessor(
            guard = guard,
            completion = TsUnknownCallModelCompletion.Normal { present },
        )

        TsUnknownCallModelExecution(
            successors = listOf(successor),
            residualGuard = mkNot(guard),
        )
    }
}
