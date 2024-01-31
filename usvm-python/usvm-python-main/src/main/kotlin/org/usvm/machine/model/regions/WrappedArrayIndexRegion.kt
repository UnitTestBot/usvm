package org.usvm.machine.model.regions

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.PyContext
import org.usvm.machine.model.PyModel
import org.usvm.machine.model.getFirstType
import org.usvm.machine.symbolicobjects.memory.DEFAULT_ELEMENT_INDEX
import org.usvm.memory.UReadOnlyMemoryRegion

class WrappedArrayIndexRegion<ArrayType, Sort: USort>(
    private val region: UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, KIntSort>, UAddressSort>,
    private val model: PyModel,
    private val ctx: PyContext,
    private val nullRef: UConcreteHeapRef
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, KIntSort>, UAddressSort> {
    override fun read(key: UArrayIndexLValue<ArrayType, Sort, KIntSort>): UExpr<UAddressSort> {
        val underlyingResult = region.read(key) as UConcreteHeapRef
        val array = key.ref as UConcreteHeapRef
        if (array.address > 0)  // allocated object
            return underlyingResult
        val arrayType = model.getFirstType(array)
        require(arrayType != null && arrayType is ArrayLikeConcretePythonType)
        val constraints = arrayType.elementConstraints
        val typeSystem = model.types.typeSystem as PythonTypeSystem
        val isNonNull = arrayType.innerType?.let { typeSystem.isNonNullType(it) } ?: false
        // println("First inner type: ${arrayType.innerType}")
        if (constraints.all { it.applyInterpreted(array, underlyingResult, model, ctx) } &&
            (!isNonNull || underlyingResult.address != 0)) {
            return underlyingResult
        }
        val defaultIndex = ctx.mkIntNum(DEFAULT_ELEMENT_INDEX)
        if (ctx.mkEq(defaultIndex, key.index).isTrue) {
            // println("Default index is null")
            return nullRef
        }
        return read(UArrayIndexLValue(key.sort, key.ref, defaultIndex, key.arrayType))
    }
}