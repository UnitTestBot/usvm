package org.usvm.machine.model.regions

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.isTrue
import org.usvm.machine.PyContext
import org.usvm.machine.types.ArrayType
import org.usvm.memory.UReadOnlyMemoryRegion

class WrappedArrayLengthRegion(
    private val ctx: PyContext,
    private val region: UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort>,
) : UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort> {
    override fun read(key: UArrayLengthLValue<ArrayType, KIntSort>): UExpr<KIntSort> {
        val underlyingResult = region.read(key)
        if (ctx.mkArithLt(underlyingResult, ctx.mkIntNum(0)).isTrue) {
            return ctx.mkIntNum(0)
        }
        return underlyingResult
    }
}
