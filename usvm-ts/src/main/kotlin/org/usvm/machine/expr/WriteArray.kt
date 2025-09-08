package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.sizeSort
import org.usvm.types.first
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

internal fun TsExprResolver.handleAssignToArrayIndex(
    lhv: EtsArrayAccess,
    expr: UExpr<*>,
): Unit? = with(ctx) {
    // Resolve the array.
    val resolvedArray = resolve(lhv.array) ?: return null
    check(resolvedArray.sort == addressSort) {
        "Expected address sort for array, got: ${resolvedArray.sort}"
    }
    val array = resolvedArray.asExpr(addressSort)

    // Check for undefined or null array access.
    checkUndefinedOrNullPropertyRead(scope, array, propertyName = "[]") ?: return null

    // Resolve the index.
    val resolvedIndex = resolve(lhv.index) ?: return null
    check(resolvedIndex.sort == fp64Sort) {
        "Expected fp64 sort for index, got: ${resolvedIndex.sort}"
    }
    val index = resolvedIndex.asExpr(fp64Sort)

    // Convert the index to a bit-vector.
    val bvIndex = mkFpToBvExpr(
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = index,
        bvSize = 32,
        isSigned = true,
    ).asExpr(sizeSort)

    // Determine the array type.
    // TODO: handle the case when `lhv.array.type` is NOT an array.
    //  In this case, it could be created manually: `EtsArrayType(EtsUnknownType, 1)`.
    val arrayType = if (isAllocatedConcreteHeapRef(array)) {
        scope.calcOnState { memory.typeStreamOf(array).first() }
    } else {
        lhv.array.type
    }
    check(arrayType is EtsArrayType) {
        "Expected EtsArrayType, got: ${lhv.array.type}"
    }

    return assignToArrayIndex(scope, array, bvIndex, expr, arrayType)
}

internal fun TsContext.assignToArrayIndex(
    scope: TsStepScope,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    expr: UExpr<*>,
    arrayType: EtsArrayType,
): Unit? {
    // Read the array length.
    val length = scope.calcOnState {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        memory.read(lengthLValue)
    }

    // Note: out-of-bound write is not an error in JS, since it can grow the array.
    //  However, we decided to forbid this behavior in our model for simplicity.
    //  Instead, we only allow writing to existing indices.

    // Check for out-of-bounds access.
    checkNegativeIndexRead(scope, index) ?: return null
    checkReadingInRange(scope, index, length) ?: return null

    val elementSort = typeToSort(arrayType.elementType)

    // If the element sort is known, write directly.
    if (elementSort !is TsUnresolvedSort) {
        val lValue = mkArrayIndexLValue(
            sort = elementSort,
            ref = array,
            index = index.asExpr(sizeSort),
            type = arrayType,
        )
        return scope.doWithState {
            memory.write(lValue, expr.asExpr(elementSort), guard = trueExpr)
        }
    }

    // If the element sort is unknown, we need to employ a fake object.
    val lValue = mkArrayIndexLValue(
        sort = addressSort,
        ref = array,
        index = index.asExpr(sizeSort),
        type = arrayType,
    )
    val fakeExpr = expr.toFakeObject(scope)
    return scope.doWithState {
        lValuesToAllocatedFakeObjects += lValue to fakeExpr
        memory.write(lValue, fakeExpr, guard = trueExpr)
    }
}
