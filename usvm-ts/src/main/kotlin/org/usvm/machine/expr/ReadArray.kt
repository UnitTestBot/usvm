package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.TsSizeSort
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.types.first
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue

private val logger = KotlinLogging.logger {}

internal fun TsExprResolver.handleArrayAccess(
    value: EtsArrayAccess,
): UExpr<*>? = with(ctx) {
    // Resolve the array.
    val array = run {
        val resolved = resolve(value.array) ?: return null
        if (resolved.isFakeObject()) {
            scope.assert(resolved.getFakeType(scope).refTypeExpr) ?: run {
                logger.warn { "UNSAT after ensuring fake object is ref-typed" }
                return null
            }
            resolved.extractRef(scope)
        } else {
            check(resolved.sort == addressSort) {
                "Expected address sort for array, got: ${resolved.sort}"
            }
            resolved.asExpr(addressSort)
        }
    }

    // Check for undefined or null array access.
    checkUndefinedOrNullPropertyRead(scope, array, propertyName = "[]") ?: return null

    // Resolve the index.
    val resolvedIndex = resolve(value.index) ?: return null
    check(resolvedIndex.sort == fp64Sort) {
        "Expected fp64 sort for index, got: ${resolvedIndex.sort}"
    }
    val index = resolvedIndex.asExpr(fp64Sort)

    // Convert the index to a bit-vector.
    val bvIndex = mkFpToBvExpr(
        roundingMode = fpRoundingModeSortDefaultValue(),
        value = index,
        bvSize = sizeSort.sizeBits.toInt(),
        isSigned = true,
    ).asExpr(sizeSort)

    // Determine the array type.
    val arrayType = if (isAllocatedConcreteHeapRef(array)) {
        scope.calcOnState { memory.typeStreamOf(array).first() }
    } else {
        value.array.type
    }
    check(arrayType is EtsArrayType) {
        "Expected EtsArrayType, got: ${value.array.type}"
    }

    // Read the array element.
    readArray(scope, array, bvIndex, arrayType)
}

fun TsContext.readArray(
    scope: TsStepScope,
    array: UHeapRef,
    index: UExpr<TsSizeSort>,
    arrayType: EtsArrayType,
): UExpr<*>? {
    checkNotFake(array)

    // Read the array length.
    val length = scope.calcOnState {
        val lengthLValue = mkArrayLengthLValue(array, arrayType)
        memory.read(lengthLValue)
    }

    // Check for out-of-bounds access.
    checkNegativeIndexRead(scope, index) ?: return null
    checkReadingInRange(scope, index, length) ?: return null

    // Determine the element sort.
    val sort = typeToSort(arrayType.elementType)

    // If the element type is known, we can read it directly.
    if (sort !is TsUnresolvedSort) {
        val lValue = mkArrayIndexLValue(
            sort = sort,
            ref = array,
            index = index,
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
            index = index,
            type = arrayType,
        )
        val fake = scope.calcOnState { memory.read(lValue) }
        check(fake.isFakeObject()) {
            "Expected fake object in concrete array with unresolved element type, got: $fake"
        }
        return fake
    }

    // If the element type is unresolved, we need to create a fake object
    // that can hold boolean, number, and reference values.
    // We read all three types from the array and combine them into a fake object.
    return scope.calcOnState {
        val boolArrayType = EtsArrayType(EtsBooleanType, dimensions = 1)
        val boolLValue = mkArrayIndexLValue(boolSort, array, index, boolArrayType)
        val bool = memory.read(boolLValue)

        val numberArrayType = EtsArrayType(EtsNumberType, dimensions = 1)
        val fpLValue = mkArrayIndexLValue(fp64Sort, array, index, numberArrayType)
        val fp = memory.read(fpLValue)

        val unknownArrayType = EtsArrayType(EtsUnknownType, dimensions = 1)
        val refLValue = mkArrayIndexLValue(addressSort, array, index, unknownArrayType)
        val ref = memory.read(refLValue)

        // If the read reference is already a fake object, we can return it directly.
        // Otherwise, we need to create a new fake object and write it back to the memory.
        // TODO: Think about the type constraint to get a consistent array resolution later
        if (ref.isFakeObject()) {
            ref
        } else {
            val fakeObj = mkFakeValue(scope, bool, fp, ref)
            lValuesToAllocatedFakeObjects += refLValue to fakeObj
            memory.write(refLValue, fakeObj, guard = trueExpr)
            fakeObj
        }
    }
}
