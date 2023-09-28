package org.usvm.collection.array

import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.key.USizeExprBv32KeyInfo
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.memory.key.USizeExprInt32KeyInfo
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.key.USizeRegion
import org.usvm.regions.ProductRegion

/**
 * A composite key for symbolic arrays: every entry is determined by heap address of target buffer and its numeric index.
 */
typealias USymbolicArrayIndex<USizeSort> = Pair<UHeapRef, UExpr<USizeSort>>
typealias USymbolicArrayIndexRegion = ProductRegion<UHeapRefRegion, USizeRegion>

/**
 * Provides information about keys of input arrays.
 */
abstract class USymbolicArrayIndexKeyInfo<USizeSort : USort> : USymbolicCollectionKeyInfo<USymbolicArrayIndex<USizeSort>, USymbolicArrayIndexRegion> {
    abstract val indexKeyInfo: USizeExprKeyInfo<USizeSort>

    override fun mapKey(key: USymbolicArrayIndex<USizeSort>, transformer: UTransformer<*, *>?): USymbolicArrayIndex<USizeSort> {
        val ref = UHeapRefKeyInfo.mapKey(key.first, transformer)
        val index = indexKeyInfo.mapKey(key.second, transformer)
        return if (ref === key.first && index === key.second) key else ref to index
    }

    override fun eqSymbolic(ctx: UContext<*>, key1: USymbolicArrayIndex<USizeSort>, key2: USymbolicArrayIndex<USizeSort>): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    indexKeyInfo.eqSymbolic(ctx, key1.second, key2.second)
        }

    override fun eqConcrete(key1: USymbolicArrayIndex<USizeSort>, key2: USymbolicArrayIndex<USizeSort>): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && indexKeyInfo.eqConcrete(key1.second, key2.second)

    override fun cmpSymbolicLe(ctx: UContext<*>, key1: USymbolicArrayIndex<USizeSort>, key2: USymbolicArrayIndex<USizeSort>): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    indexKeyInfo.cmpSymbolicLe(ctx, key1.second, key2.second)
        }

    override fun cmpConcreteLe(key1: USymbolicArrayIndex<USizeSort>, key2: USymbolicArrayIndex<USizeSort>): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && indexKeyInfo.cmpConcreteLe(key1.second, key2.second)

    override fun keyToRegion(key: USymbolicArrayIndex<USizeSort>) =
        ProductRegion(
            UHeapRefKeyInfo.keyToRegion(key.first),
            indexKeyInfo.keyToRegion(key.second)
        )

    override fun keyRangeRegion(from: USymbolicArrayIndex<USizeSort>, to: USymbolicArrayIndex<USizeSort>): USymbolicArrayIndexRegion {
        require(from.first == to.first)

        return ProductRegion(
            UHeapRefKeyInfo.keyToRegion(from.first),
            indexKeyInfo.keyRangeRegion(from.second, to.second)
        )
    }

    override fun topRegion() = ProductRegion(
        UHeapRefKeyInfo.topRegion(),
        indexKeyInfo.topRegion()
    )

    override fun bottomRegion() = ProductRegion(
        UHeapRefKeyInfo.bottomRegion(),
        indexKeyInfo.bottomRegion()
    )
}

object USymbolicArrayIndexBv32KeyInfo : USymbolicArrayIndexKeyInfo<UBv32Sort>() {
    override val indexKeyInfo: USizeExprKeyInfo<UBv32Sort> = USizeExprBv32KeyInfo
}

object USymbolicArrayIndexInt32KeyInfo : USymbolicArrayIndexKeyInfo<KIntSort>() {
    override val indexKeyInfo: USizeExprKeyInfo<KIntSort> = USizeExprInt32KeyInfo
}
