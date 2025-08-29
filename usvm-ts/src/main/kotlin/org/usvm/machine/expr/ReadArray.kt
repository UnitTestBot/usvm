package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.types.first
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

internal fun TsExprResolver.handleArrayAccess(
    value: EtsArrayAccess,
): UExpr<*>? = with(ctx) {
    val resolvedArray = resolve(value.array) ?: return null
    if (resolvedArray.sort != addressSort) {
        error("Expected address sort for array, got: ${resolvedArray.sort}")
    }
    val array = resolvedArray.asExpr(addressSort)

    checkUndefinedOrNullPropertyRead(scope, array, "[]") ?: return null

    val resolvedIndex = resolve(value.index) ?: return null
    if (resolvedIndex.sort != fp64Sort) {
        error("Expected fp64 sort for index, got: ${resolvedIndex.sort}")
    }
    val index = resolvedIndex.asExpr(fp64Sort)
    val bvIndex = mkFpToBvExpr(
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = index,
        bvSize = sizeSort.sizeBits.toInt(),
        isSigned = true,
    ).asExpr(sizeSort)

    val arrayType = if (isAllocatedConcreteHeapRef(array)) {
        scope.calcOnState { memory.typeStreamOf(array).first() }
    } else {
        value.array.type
    }
    check(arrayType is EtsArrayType) {
        "Expected EtsArrayType, got: ${value.array.type}"
    }
    val sort = typeToSort(arrayType.elementType)

    val lengthLValue = mkArrayLengthLValue(array, arrayType)
    val length = scope.calcOnState { memory.read(lengthLValue) }

    checkNegativeIndexRead(scope, bvIndex) ?: return null
    checkReadingInRange(scope, bvIndex, length) ?: return null

    // If the element type is known, we can read it directly.
    if (sort !is TsUnresolvedSort) {
        val lValue = mkArrayIndexLValue(
            sort = sort,
            ref = array,
            index = bvIndex,
            type = arrayType,
        )
        return scope.calcOnState { memory.read(lValue) }
    }

    // Concrete arrays with the unresolved sort should consist of fake objects only.
    if (array is UConcreteHeapRef) {
        // Read a fake object from the array.
        val lValue = mkArrayIndexLValue(
            sort = addressSort,
            ref = array,
            index = bvIndex,
            type = arrayType,
        )
        return scope.calcOnState { memory.read(lValue) }
    }

    // If the element type is unresolved, we need to create a fake object
    // that can hold boolean, number, and reference values.
    // We read all three types from the array and combine them into a fake object.
    scope.calcOnState {
        val boolArrayType = EtsArrayType(EtsBooleanType, dimensions = 1)
        val boolLValue = mkArrayIndexLValue(boolSort, array, bvIndex, boolArrayType)
        val bool = memory.read(boolLValue)

        val numberArrayType = EtsArrayType(EtsNumberType, dimensions = 1)
        val fpLValue = mkArrayIndexLValue(fp64Sort, array, bvIndex, numberArrayType)
        val fp = memory.read(fpLValue)

        val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
        val refLValue = mkArrayIndexLValue(addressSort, array, bvIndex, unknownArrayType)
        val ref = memory.read(refLValue)

        // If the read reference is already a fake object, we can return it directly.
        // Otherwise, we need to create a new fake object and write it back to the memory.
        if (ref.isFakeObject()) {
            ref
        } else {
            val fakeObj = mkFakeValue(bool, fp, ref)
            lValuesToAllocatedFakeObjects += refLValue to fakeObj
            memory.write(refLValue, fakeObj, guard = trueExpr)
            fakeObj
        }
    }
}
