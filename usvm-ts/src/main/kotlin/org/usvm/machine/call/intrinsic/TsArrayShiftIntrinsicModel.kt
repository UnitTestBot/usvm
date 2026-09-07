package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.memcpy
import org.usvm.api.typeStreamOf
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.types.singleOrNull
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

/** Engine intrinsic for `Array.shift`, whose bulk move is implemented by symbolic-memory `memcpy`. */
internal object TsArrayShiftIntrinsicModel : TsUnknownCallModel {
    const val MODEL_ID: String = "ts.array.shift"

    override val id: String = MODEL_ID
    override val target = TsUnknownCallTarget(
        methodName = "shift",
        failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
    )

    override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution? = with(state.ctx) {
        val input = resolveInput(state, call) ?: return@with null
        val lengthLValue = mkArrayLengthLValue(input.array, input.arrayType)
        val length = state.memory.read(lengthLValue)
        val zero = mkBv(0)
        val emptyGuard = mkEq(length, zero)
        val nonEmptyGuard = mkNot(emptyGuard)
        val newLength = mkBvSubExpr(length, mkBv(1))
        val firstElementLValue = mkArrayIndexLValue(
            sort = input.elementSort,
            ref = input.array,
            index = zero,
            type = input.arrayType,
        )
        val firstElement = state.memory.read(firstElementLValue)

        val emptySuccessor = TsUnknownCallModelSuccessor(
            guard = emptyGuard,
            completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
        )
        val nonEmptySuccessor = TsUnknownCallModelSuccessor(
            guard = nonEmptyGuard,
            completion = TsUnknownCallModelCompletion.Normal { firstElement },
            applyStateChanges = {
                memory.memcpy(
                    srcRef = input.array,
                    dstRef = input.array,
                    type = input.arrayType,
                    elementSort = input.elementSort,
                    fromSrc = mkBv(1),
                    fromDst = zero,
                    length = newLength,
                )
                memory.write(lengthLValue, newLength, guard = trueExpr)
            },
        )

        TsUnknownCallModelExecution(successors = listOf(emptySuccessor, nonEmptySuccessor))
    }

    private fun resolveInput(state: TsState, call: TsUnknownCall): ArrayShiftInput? = with(state.ctx) {
        if (call.arguments.isNotEmpty()) {
            return@with null
        }

        val receiver = call.receiver ?: return@with null
        val receiverValue = receiver.resolved ?: return@with null
        if (receiverValue.sort != addressSort || receiverValue.containsFakeObject()) {
            return@with null
        }

        val array = receiverValue.asExpr(addressSort)
        val arrayType = (receiver.source.type as? EtsArrayType)
            ?: (state.memory.typeStreamOf(array).singleOrNull() as? EtsArrayType)
            ?: return@with null
        if (arrayType.dimensions != 1) {
            return@with null
        }

        val elementSort = typeToSort(arrayType.elementType)
        if (elementSort is TsUnresolvedSort) {
            return@with null
        }

        ArrayShiftInput(array, arrayType, elementSort)
    }

    private class ArrayShiftInput(
        val array: UExpr<UAddressSort>,
        val arrayType: EtsArrayType,
        val elementSort: USort,
    )
}
