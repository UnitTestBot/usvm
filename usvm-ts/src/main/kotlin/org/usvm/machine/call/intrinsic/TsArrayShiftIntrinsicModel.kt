package org.usvm.machine.call.intrinsic

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.memcpy
import org.usvm.machine.TsSizeSort
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.state.TsState
import org.usvm.machine.types.TsUnresolvedArrayKind
import org.usvm.machine.types.readUnresolvedArrayElement
import org.usvm.util.arrayStorageType
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
                shiftElements(input, fromSrc = one, fromDst = zero, length = newLength)
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

    private fun TsState.shiftElements(
        input: ArrayShiftInput,
        fromSrc: UExpr<TsSizeSort>,
        fromDst: UExpr<TsSizeSort>,
        length: UExpr<TsSizeSort>,
    ) = with(ctx) {
        if (input.elementSort !is TsUnresolvedSort) {
            copyArrayRegion(
                input = input,
                arrayType = input.arrayType,
                elementSort = input.elementSort,
                fromSrc = fromSrc,
                fromDst = fromDst,
                length = length,
            )
            return@with
        }

        if (input.array is UConcreteHeapRef) {
            copyArrayRegion(
                input = input,
                arrayType = input.arrayType,
                elementSort = addressSort,
                fromSrc = fromSrc,
                fromDst = fromDst,
                length = length,
            )
            return@with
        }

        copyArrayRegion(
            input = input,
            arrayType = EtsArrayType(EtsBooleanType, dimensions = 1),
            elementSort = boolSort,
            fromSrc = fromSrc,
            fromDst = fromDst,
            length = length,
        )
        copyArrayRegion(
            input = input,
            arrayType = EtsArrayType(EtsNumberType, dimensions = 1),
            elementSort = fp64Sort,
            fromSrc = fromSrc,
            fromDst = fromDst,
            length = length,
        )
        copyArrayRegion(
            input = input,
            arrayType = EtsArrayType(EtsUnknownType, dimensions = 1),
            elementSort = addressSort,
            fromSrc = fromSrc,
            fromDst = fromDst,
            length = length,
        )
        TsUnresolvedArrayKind.entries.forEach { kind ->
            memory.memcpy(
                srcRef = input.array,
                dstRef = input.array,
                type = kind,
                elementSort = boolSort,
                fromSrc = fromSrc,
                fromDst = fromDst,
                length = length,
            )
        }
    }

    private fun TsState.copyArrayRegion(
        input: ArrayShiftInput,
        arrayType: EtsArrayType,
        elementSort: USort,
        fromSrc: UExpr<TsSizeSort>,
        fromDst: UExpr<TsSizeSort>,
        length: UExpr<TsSizeSort>,
    ) {
        memory.memcpy(
            srcRef = input.array,
            dstRef = input.array,
            type = ctx.arrayDescriptorOf(arrayType),
            elementSort = elementSort,
            fromSrc = fromSrc,
            fromDst = fromDst,
            length = length,
        )
    }

    private class ArrayShiftInput(
        val array: UExpr<UAddressSort>,
        val arrayType: EtsArrayType,
        val elementSort: USort,
    )
}
