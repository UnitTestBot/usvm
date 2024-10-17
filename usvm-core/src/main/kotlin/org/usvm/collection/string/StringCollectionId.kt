package org.usvm.collection.string

import org.usvm.UBoolExpr
import org.usvm.UCharSort
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.key.USizeRegion
import org.usvm.regions.emptyRegionTree

class UStringCollectionId<USizeSort: USort>(
    val ctx: UContext<USizeSort>,
    val string: UStringExpr
) : USymbolicCollectionId<UExpr<USizeSort>, UCharSort, UStringCollectionId<USizeSort>> {
    override val sort: UCharSort = ctx.charSort

    override fun keyInfo(): USymbolicCollectionKeyInfo<UExpr<USizeSort>, USizeRegion> =
        USizeExprKeyInfo()

    override fun emptyCollection(): USymbolicCollection<UStringCollectionId<USizeSort>, UExpr<USizeSort>, UCharSort> {
        val updates = UTreeUpdates<UExpr<USizeSort>, USizeRegion, UCharSort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates) { true }
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UExpr<USizeSort>,
        value: UExpr<UCharSort>,
        guard: UBoolExpr
    ) =
        error("Strings are immutable, content should not be written")

    override fun instantiate(
        collection: USymbolicCollection<UStringCollectionId<USizeSort>, UExpr<USizeSort>, UCharSort>,
        key: UExpr<USizeSort>,
        composer: UComposer<*, *>?
    ): UExpr<UCharSort> {
        val composedString = composer?.compose(string) ?: string
        return charAt(ctx, composedString, key)
    }
}