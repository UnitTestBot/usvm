package org.usvm.machine.model.regions

import io.ksmt.sort.KIntSort
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.language.types.ArrayLikeConcretePythonType
import org.usvm.machine.UPythonContext
import org.usvm.machine.model.PyModel
import org.usvm.machine.utils.PyModelWrapper
import org.usvm.memory.UReadOnlyMemoryRegion

class WrappedArrayIndexRegion<ArrayType, Sort: USort>(
    private val region: UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, KIntSort>, UAddressSort>,
    private val model: PyModel,
    private val ctx: UPythonContext,
    private val nullRef: UConcreteHeapRef
) : UReadOnlyMemoryRegion<UArrayIndexLValue<ArrayType, Sort, KIntSort>, UAddressSort> {
    override fun read(key: UArrayIndexLValue<ArrayType, Sort, KIntSort>): UExpr<UAddressSort> {
        val underlyingResult = region.read(key)
        val array = key.ref as UConcreteHeapRef
        if (array.address > 0)  // allocated object
            return underlyingResult
        val arrayType = PyModelWrapper(model).getConcreteType(array)
        require(arrayType != null && arrayType is ArrayLikeConcretePythonType)
        val constraints = arrayType.elementConstraints
        if (constraints.all { it.applyInterpreted(array, underlyingResult as UConcreteHeapRef, model, ctx) }) {
            return underlyingResult
        }
        return nullRef
    }
}