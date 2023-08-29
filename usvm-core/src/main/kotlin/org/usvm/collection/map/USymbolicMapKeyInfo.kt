package org.usvm.collection.map

import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.util.ProductRegion
import org.usvm.util.Region

typealias USymbolicMapKey<KeySort> = Pair<UHeapRef, UExpr<KeySort>>
typealias USymbolicMapKeyRegion<KeyReg> = ProductRegion<UHeapRefRegion, KeyReg>

/**
 * Provides information about keys of symbolic maps.
 */
data class USymbolicMapKeyInfo<KeySort: USort, KeyReg: Region<KeyReg>>(
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, KeyReg>
): USymbolicCollectionKeyInfo<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<KeyReg>> {
    override fun eqSymbolic(ctx: UContext, key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    keyInfo.eqSymbolic(ctx, key1.second, key2.second)
        }

    override fun eqConcrete(key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && keyInfo.eqConcrete(key1.second, key2.second)

    override fun cmpSymbolicLe(ctx: UContext, key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    keyInfo.cmpSymbolicLe(ctx, key1.second, key2.second)
        }

    override fun cmpConcreteLe(key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && keyInfo.cmpConcreteLe(key1.second, key2.second)

    override fun keyToRegion(key: USymbolicMapKey<KeySort>) =
        ProductRegion(
            UHeapRefKeyInfo.keyToRegion(key.first),
            keyInfo.keyToRegion(key.second)
        )

    override fun keyRangeRegion(
        from: USymbolicMapKey<KeySort>,
        to: USymbolicMapKey<KeySort>
    ): USymbolicMapKeyRegion<KeyReg> {
        require(from.first == to.first)
        return ProductRegion(
            UHeapRefKeyInfo.keyToRegion(from.first),
            keyInfo.keyRangeRegion(from.second, to.second)
        )
    }

    override fun topRegion() =
        ProductRegion(UHeapRefKeyInfo.topRegion(), keyInfo.topRegion())

    override fun bottomRegion() =
        ProductRegion(UHeapRefKeyInfo.bottomRegion(), keyInfo.bottomRegion())
}
