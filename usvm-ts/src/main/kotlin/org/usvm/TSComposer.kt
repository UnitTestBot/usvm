package org.usvm

import io.ksmt.utils.cast
import org.jacodb.ets.base.EtsType
import org.usvm.memory.UReadOnlyMemory

class TSComposer(
    ctx: UContext<TSSizeSort>,
    memory: UReadOnlyMemory<EtsType>
) : UComposer<EtsType, TSSizeSort>(ctx, memory) {

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> = with(expr) {
        memory.stack.readRegisterUnsafe(idx, sort).cast()
    }
}