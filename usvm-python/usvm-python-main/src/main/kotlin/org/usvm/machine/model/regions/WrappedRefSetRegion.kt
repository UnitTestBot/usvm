package org.usvm.machine.model.regions

import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.PyContext
import org.usvm.memory.UReadOnlyMemoryRegion

class WrappedRefSetRegion<SetType>(
    private val ctx: PyContext,
    private val region: UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>,
    private val keys: Set<UConcreteHeapRef>,
) : UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {
    override fun read(key: URefSetEntryLValue<SetType>): UExpr<UBoolSort> {
        if (!isAllocatedConcreteHeapRef(key.setRef) && key.setElement !in keys) {
            return ctx.falseExpr
        }
        return region.read(key)
    }
}
