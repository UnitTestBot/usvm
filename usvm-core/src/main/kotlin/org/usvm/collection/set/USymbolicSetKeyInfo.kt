package org.usvm.collection.set

import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.regions.ProductRegion
import org.usvm.regions.Region

typealias USymbolicSetElement<ElementSort> = Pair<UHeapRef, UExpr<ElementSort>>
typealias USymbolicSetElementRegion<ElementReg> = ProductRegion<UHeapRefRegion, ElementReg>

/**
 * Provides information about elements of symbolic sets.
 */
data class USymbolicSetKeyInfo<ElementSort : USort, ElementReg : Region<ElementReg>>(
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<ElementSort>, ElementReg>
) : USymbolicCollectionKeyInfo<USymbolicSetElement<ElementSort>, USymbolicSetElementRegion<ElementReg>> {
    override fun mapKey(
        key: USymbolicSetElement<ElementSort>,
        transformer: UTransformer<*>?
    ): USymbolicSetElement<ElementSort> {
        val setRef = UHeapRefKeyInfo.mapKey(key.first, transformer)
        val setElement = keyInfo.mapKey(key.second, transformer)
        return if (setRef === key.first && setElement === key.second) key else setRef to setElement
    }

    override fun eqSymbolic(
        ctx: UContext,
        key1: USymbolicSetElement<ElementSort>,
        key2: USymbolicSetElement<ElementSort>
    ): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    keyInfo.eqSymbolic(ctx, key1.second, key2.second)
        }

    override fun eqConcrete(key1: USymbolicSetElement<ElementSort>, key2: USymbolicSetElement<ElementSort>): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && keyInfo.eqConcrete(key1.second, key2.second)

    override fun cmpSymbolicLe(
        ctx: UContext,
        key1: USymbolicSetElement<ElementSort>,
        key2: USymbolicSetElement<ElementSort>
    ): UBoolExpr =
        with(ctx) {
            UHeapRefKeyInfo.eqSymbolic(ctx, key1.first, key2.first) and
                    keyInfo.cmpSymbolicLe(ctx, key1.second, key2.second)
        }

    override fun cmpConcreteLe(
        key1: USymbolicSetElement<ElementSort>,
        key2: USymbolicSetElement<ElementSort>
    ): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && keyInfo.cmpConcreteLe(key1.second, key2.second)

    override fun keyToRegion(key: USymbolicSetElement<ElementSort>) =
        ProductRegion(
            UHeapRefKeyInfo.keyToRegion(key.first),
            keyInfo.keyToRegion(key.second)
        )

    override fun keyRangeRegion(
        from: USymbolicSetElement<ElementSort>,
        to: USymbolicSetElement<ElementSort>
    ): USymbolicSetElementRegion<ElementReg> {
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

    companion object {
        fun <ElementReg : Region<ElementReg>> addSetRefRegion(
            region: ElementReg,
            setRefRegion: UHeapRefRegion
        ): USymbolicSetElementRegion<ElementReg> = ProductRegion(setRefRegion, region)

        fun <ElementReg : Region<ElementReg>> removeSetRefRegion(
            region: USymbolicSetElementRegion<ElementReg>,
            elementKeyInfo: USymbolicCollectionKeyInfo<*, ElementReg>
        ): ElementReg = region.products
            .map { it.second }
            .reduceOrNull { res, elementReg -> res.union(elementReg) }
            ?: elementKeyInfo.bottomRegion()

        fun <ElementReg : Region<ElementReg>> changeSetRefRegion(
            region: USymbolicSetElementRegion<ElementReg>,
            setRefRegion: UHeapRefRegion,
            elementKeyInfo: USymbolicCollectionKeyInfo<*, ElementReg>
        ): USymbolicSetElementRegion<ElementReg> = ProductRegion(
            setRefRegion,
            removeSetRefRegion(region, elementKeyInfo)
        )
    }
}
