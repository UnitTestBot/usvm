package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.TsSizeSort
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.machine.types.readUnresolvedArrayElement
import org.usvm.util.arrayStorageType
import org.usvm.util.copyArrayElements
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

/** Engine intrinsic for `Array.shift`, whose bulk move is implemented by symbolic-memory `memcpy`. */
internal object TsArrayShiftIntrinsicModel : TsBuiltInUnknownCallModel {
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
        val one = mkBv(1)
        val emptyGuard = mkEq(length, zero)
        val nonEmptyGuard = mkNot(emptyGuard)
        val newLength = mkBvSubExpr(length, one)
        val firstElementCompletion = state.firstElementCompletion(input, zero)

        val emptySuccessor = TsUnknownCallModelSuccessor(
            guard = emptyGuard,
            completion = TsUnknownCallModelCompletion.Normal { ctx.mkUndefinedValue() },
        )
        val nonEmptySuccessor = TsUnknownCallModelSuccessor(
            guard = nonEmptyGuard,
            completion = firstElementCompletion,
            applyStateChanges = {
                copyArrayElements(
                    srcRef = input.array,
                    dstRef = input.array,
                    arrayType = input.arrayType,
                    fromSrc = one,
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
        if (receiverValue.sort != addressSort) {
            return@with null
        }

        val array = receiverValue.asExpr(addressSort)
        val arrayType = state.arrayStorageType(array, receiver.source.type) as? EtsArrayType
            ?: return@with null
        if (arrayType.dimensions != 1) {
            return@with null
        }

        val elementSort = typeToSort(arrayType.elementType)
        ArrayShiftInput(array, arrayType, elementSort)
    }

    private fun TsState.firstElementCompletion(
        input: ArrayShiftInput,
        index: UExpr<TsSizeSort>,
    ): TsUnknownCallModelCompletion = with(ctx) {
        if (input.elementSort !is TsUnresolvedSort) {
            val firstElementLValue = mkArrayIndexLValue(
                sort = input.elementSort,
                ref = input.array,
                index = index,
                type = input.arrayType,
            )
            val firstElement = memory.read(firstElementLValue)

            return@with TsUnknownCallModelCompletion.Normal { firstElement }
        }

        val firstElement = readUnresolvedArrayElement(memory, input.array, index)
        TsUnknownCallModelCompletion.Unresolved(firstElement)
    }

    private class ArrayShiftInput(
        val array: UExpr<UAddressSort>,
        val arrayType: EtsArrayType,
        val elementSort: USort,
    )
}
