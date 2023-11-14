package org.usvm.machine.model

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.isTrue
import org.usvm.language.types.ArrayType
import org.usvm.machine.UPythonContext
import org.usvm.memory.UReadOnlyMemoryRegion

class WrappedArrayLengthRegion(
    val ctx: UPythonContext,
    val region: UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort>
): UReadOnlyMemoryRegion<UArrayLengthLValue<ArrayType, KIntSort>, KIntSort> {
    override fun read(key: UArrayLengthLValue<ArrayType, KIntSort>): UExpr<KIntSort> {
        val underlyingResult = region.read(key)
        if (ctx.mkArithLt(underlyingResult, ctx.mkIntNum(0)).isTrue) {
            return ctx.mkIntNum(0)
        }
        return underlyingResult
    }
}