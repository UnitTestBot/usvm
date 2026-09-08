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
import org.usvm.api.typeStreamOf
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.machine.TsSizeSort
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModel
import org.usvm.machine.call.TsUnknownCallModelCompletion
import org.usvm.machine.call.TsUnknownCallModelExecution
import org.usvm.machine.call.TsUnknownCallModelSuccessor
import org.usvm.machine.call.TsUnknownCallTarget
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.readSymbolicUnresolvedArrayElement
import org.usvm.machine.state.TsState
import org.usvm.machine.types.findMaterializedFakeValue
import org.usvm.sizeSort
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

        if (input.array is UConcreteHeapRef) {
            val firstElementLValue = mkArrayIndexLValue(
                sort = addressSort,
                ref = input.array,
                index = index,
                type = input.arrayType,
            )
            val firstElement = memory.read(firstElementLValue)

            return@with TsUnknownCallModelCompletion.Normal {
                check(firstElement.isFakeObject()) {
                    "Expected fake object in concrete array with unresolved element type, got: $firstElement"
                }
                firstElement
            }
        }

        val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
        val firstElementLValue = mkArrayIndexLValue(addressSort, input.array, index, unknownArrayType)
        val materializedFirstElement = findMaterializedFakeValue(firstElementLValue)
        if (materializedFirstElement != null) {
            return@with TsUnknownCallModelCompletion.Normal { materializedFirstElement }
        }

        val firstElement = readSymbolicUnresolvedArrayElement(input.array, index)
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
            shiftMaterializedFakeValues(input)
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
        shiftMaterializedFakeValues(input)
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

    private fun TsState.shiftMaterializedFakeValues(input: ArrayShiftInput) = with(ctx) {
        val arrayDescriptor = if (input.array is UConcreteHeapRef) {
            arrayDescriptorOf(input.arrayType)
        } else {
            arrayDescriptorOf(EtsArrayType(EtsUnknownType, dimensions = 1))
        }
        val zero = mkBv(0)
        val one = mkBv(1)
        val shiftedValues = lValuesToAllocatedFakeObjects.mapNotNull { (lValue, fakeValue) ->
            if (
                lValue !is UArrayIndexLValue<*, *, *> ||
                lValue.ref != input.array ||
                lValue.arrayType != arrayDescriptor
            ) {
                return@mapNotNull null
            }

            val sourceIndex = lValue.index.asExpr(sizeSort)
            if (sourceIndex == zero) {
                return@mapNotNull null
            }

            val destinationIndex = mkBvSubExpr(sourceIndex, one)
            val destinationLValue = UArrayIndexLValue(
                addressSort,
                input.array,
                destinationIndex,
                arrayDescriptor,
            )
            destinationLValue to fakeValue
        }

        lValuesToAllocatedFakeObjects += shiftedValues
    }

    private class ArrayShiftInput(
        val array: UExpr<UAddressSort>,
        val arrayType: EtsArrayType,
        val elementSort: USort,
    )
}
