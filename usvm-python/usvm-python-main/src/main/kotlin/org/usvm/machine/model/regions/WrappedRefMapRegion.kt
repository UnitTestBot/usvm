package org.usvm.machine.model.regions

import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.machine.PyContext
import org.usvm.machine.types.PythonType
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelBase

class WrappedRefMapRegion<MapType>(
    private val ctx: PyContext,
    private val region: UReadOnlyMemoryRegion<URefMapEntryLValue<MapType, UAddressSort>, UAddressSort>,
    private val keys: Set<UConcreteHeapRef>,
    private val underlyingModel: UModelBase<PythonType>,
) : UReadOnlyMemoryRegion<URefMapEntryLValue<MapType, UAddressSort>, UAddressSort> {
    override fun read(key: URefMapEntryLValue<MapType, UAddressSort>): UExpr<UAddressSort> {
        if (key.mapKey !in keys) {
            return underlyingModel.eval(ctx.nullRef)
        }
        return region.read(key)
    }
}
