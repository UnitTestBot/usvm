package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.sizeSort
import org.usvm.util.mkArrayLengthLValue

// Handles reading the `length` property of an array.
internal fun TsExprResolver.readLengthArray(
    instanceLocal: EtsLocal,
    instance: UHeapRef, // array
): UExpr<*> = with(ctx) {
    // Assume that instance is always an array.
    val arrayType = instanceLocal.type as EtsArrayType

    // Read the length of the array.
    readArrayLength(scope, instance, arrayType)
}

// Handles reading the `length` property of a fake object.
internal fun TsExprResolver.readLengthFake(
    instanceLocal: EtsLocal,
    instance: UConcreteHeapRef,
): UExpr<*> = with(ctx) {
    require(instance.isFakeObject())

    val fakeType = instance.getFakeType(scope)

    // If we want to get length from a fake object,
    // we assume that it is an array (has address sort).
    scope.doWithState {
        pathConstraints += fakeType.refTypeExpr
    }

    val ref = instance.unwrapRef(scope)

    val arrayType = when (val type = instanceLocal.type) {
        is EtsArrayType -> type

        is EtsAnyType, is EtsUnknownType -> {
            // If the type is not an array, we assume it is a fake object with
            // a length property that behaves like an array.
            EtsArrayType(EtsUnknownType, dimensions = 1)
        }

        else -> error("Expected EtsArrayType, EtsAnyType or EtsUnknownType, but got: $type")
    }

    // Read the length of the array.
    readArrayLength(scope, ref, arrayType)
}

internal fun TsContext.readArrayLength(
    scope: TsStepScope,
    array: UHeapRef,
    arrayType: EtsArrayType,
): UExpr<*> {
    // Read the length of the array.
    val length = scope.calcOnState {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        memory.read(lengthLValue)
    }

    // Ensure that the length is non-negative.
    scope.doWithState {
        pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
    }

    // Convert the length to fp64.
    return mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = length.asExpr(sizeSort),
        signed = true,
    )
}
