package org.usvm.machine.expr

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.sizeSort
import org.usvm.util.mkArrayLengthLValue

// Handles reading the `length` property.
fun TsContext.readLengthProperty(
    scope: TsStepScope,
    instanceLocal: EtsLocal,
    instance: UHeapRef,
    maxArraySize: Int,
): UExpr<*>? {
    // Determine the array type.
    val arrayType: EtsArrayType = when (val type = instanceLocal.type) {
        is EtsArrayType -> type

        is EtsAnyType, is EtsUnknownType -> {
            // If the type is not an array, and explicitly unknown,
            // we represent it is an array with unknown element type.
            EtsArrayType(EtsUnknownType, dimensions = 1)
        }

        is EtsStringType -> {
            // Strings are treated as arrays of characters (represented as strings).
            EtsArrayType(EtsStringType, dimensions = 1)
        }

        else -> error("Expected EtsArrayType, EtsAnyType or EtsUnknownType, but got: $type")
    }

    // Read the length of the array.
    return readArrayLength(scope, instance, arrayType, maxArraySize)
}

// Reads the length of the array and returns it as a fp64 expression.
fun TsContext.readArrayLength(
    scope: TsStepScope,
    array: UHeapRef,
    arrayType: EtsArrayType,
    maxArraySize: Int,
): UExpr<KFp64Sort>? {
    checkNotFake(array)

    // Read the length of the array.
    val length = scope.calcOnState {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        memory.read(lengthLValue)
    }

    // Check that the length is within the allowed bounds.
    checkLengthBounds(scope, length, maxArraySize) ?: return null

    // Convert the length to fp64.
    return mkBvToFpExpr(
        sort = fp64Sort,
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = length.asExpr(sizeSort),
        signed = true,
    )
}
