package org.usvm.machine.model.regions

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.PyContext
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.key.USizeRegion

class WrappedSetRegion<SetType>(
    private val ctx: PyContext,
    private val region: UReadOnlyMemoryRegion<USetEntryLValue<SetType, KIntSort, USizeRegion>, UBoolSort>,
    private val keys: Set<KInterpretedValue<KIntSort>>,
) : UReadOnlyMemoryRegion<USetEntryLValue<SetType, KIntSort, USizeRegion>, UBoolSort> {
    override fun read(key: USetEntryLValue<SetType, KIntSort, USizeRegion>): UExpr<UBoolSort> {
        if (!isAllocatedConcreteHeapRef(key.setRef) && key.setElement !in keys) {
            return ctx.falseExpr
        }
        return region.read(key)
    }
}
