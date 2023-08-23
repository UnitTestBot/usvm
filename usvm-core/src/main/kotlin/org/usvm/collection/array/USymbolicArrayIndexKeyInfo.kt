package org.usvm.collection.array

import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.key.USizeRegion
import org.usvm.util.ProductRegion

/**
 * A composite key for symbolic arrays: every entry is determined by heap address of target buffer and its numeric index.
 */
typealias USymbolicArrayIndex = Pair<UHeapRef, USizeExpr>
typealias USymbolicArrayIndexRegion = ProductRegion<UHeapRefRegion, USizeRegion>

/**
 * Provides information about keys of input arrays.
 */
object USymbolicArrayIndexKeyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex, USymbolicArrayIndexRegion> {
    override fun eqSymbolic(ctx: UContext, key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    USizeExprKeyInfo.eqSymbolic(ctx, key1.second, key2.second)
        }

    override fun eqConcrete(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && USizeExprKeyInfo.eqConcrete(key1.second, key2.second)

    override fun cmpSymbolic(ctx: UContext, key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    USizeExprKeyInfo.cmpSymbolic(ctx, key1.second, key2.second)
        }

    override fun cmpConcrete(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && USizeExprKeyInfo.cmpConcrete(key1.second, key2.second)

    override fun keyToRegion(key: USymbolicArrayIndex) =
        ProductRegion(
            UHeapRefKeyInfo.keyToRegion(key.first),
            USizeExprKeyInfo.keyToRegion(key.second)
        )

    override fun keyRangeRegion(from: USymbolicArrayIndex, to: USymbolicArrayIndex): USymbolicArrayIndexRegion {
        require(from.first == to.first)
        return ProductRegion(
            UHeapRefKeyInfo.keyToRegion(from.first),
            USizeExprKeyInfo.keyRangeRegion(from.second, to.second)
        )
    }

    override fun topRegion() = ProductRegion(
        UHeapRefKeyInfo.topRegion(),
        USizeExprKeyInfo.topRegion()
    )

    override fun bottomRegion() = ProductRegion(
        UHeapRefKeyInfo.bottomRegion(),
        USizeExprKeyInfo.bottomRegion()
    )
}
