package org.usvm.memory.collections

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UConcreteSize
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeType
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.uctx
import org.usvm.util.ProductRegion
import org.usvm.util.Region
import org.usvm.util.SetRegion

/**
 * Provides information about entities used as keys of symbolic collections.
 */
interface USymbolicCollectionKeyInfo<Key, Reg: Region<Reg>> {
    /**
     * Returns symbolic expression guaranteeing that [key1] is same as [key2].
     */
    fun eqSymbolic(key1: Key, key2: Key): UBoolExpr

    /**
     * Returns if [key1] is same as [key2] in all possible models.
     */
    fun eqConcrete(key1: Key, key2: Key): Boolean

    /**
     * Returns symbolic expression guaranteeing that [key1] is less or equal to [key2].
     * Assumes that [Key] domain is linearly ordered.
     */
    fun cmpSymbolic(key1: Key, key2: Key): UBoolExpr

    /**
     * Returns if [key1] is less or equal to [key2] in all possible models.
     * Assumes that [Key] domain is linearly ordered.
     */
    fun cmpConcrete(key1: Key, key2: Key): Boolean

    /**
     * Returns region that over-approximates the possible values of [key].
     */
    fun keyToRegion(key: Key): Reg

    /**
     * Returns region that over-approximates the range of indices [[from] .. [to]]
     */
    fun keyRangeRegion(from: Key, to: Key): Reg

    /**
     * Returns region that represents any possible key.
     */

    fun topRegion(): Reg

    /**
     * Returns region that represents empty set of keys.
     */
    fun bottomRegion(): Reg
}

typealias UHeapRefRegion = SetRegion<UConcreteHeapAddress>

/**
 * Provides information about heap references used as symbolic collection keys.
 */
object UHeapRefKeyInfo: USymbolicCollectionKeyInfo<UHeapRef, UHeapRefRegion> {
    override fun eqSymbolic(key1: UHeapRef, key2: UHeapRef): UBoolExpr =
        key1.uctx.mkHeapRefEq(key1, key2)

    override fun eqConcrete(key1: UHeapRef, key2: UHeapRef): Boolean =
        key1 == key2

    override fun cmpSymbolic(key1: UHeapRef, key2: UHeapRef): UBoolExpr =
        error("Heap references should not be compared!")

    override fun cmpConcrete(key1: UHeapRef, key2: UHeapRef): Boolean =
        error("Heap references should not be compared!")

    override fun keyToRegion(key: UHeapRef) =
        if (key is UConcreteHeapRef){
            SetRegion.singleton(key.address)
        } else {
            SetRegion.universe()
        }

    override fun keyRangeRegion(from: UHeapRef, to: UHeapRef) =
        error("This should not be called!")

    override fun topRegion() =
        SetRegion.universe<UConcreteHeapAddress>()

    override fun bottomRegion() =
        SetRegion.empty<UConcreteHeapAddress>()
}

// TODO: change it to intervals region
typealias USizeRegion = SetRegion<USizeType>

/**
 * Provides information about numeric values used as symbolic collection keys.
 */
object USizeExprKeyInfo : USymbolicCollectionKeyInfo<USizeExpr, USizeRegion> {
    override fun eqSymbolic(key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        key1.uctx.mkEq(key1, key2)

    override fun eqConcrete(key1: USizeExpr, key2: USizeExpr): Boolean =
        key1 === key2

    override fun cmpSymbolic(key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        key1.ctx.mkBvSignedLessOrEqualExpr(key1, key2)

    override fun cmpConcrete(key1: USizeExpr, key2: USizeExpr): Boolean =
        key1 == key2 || (key1 is UConcreteSize && key2 is UConcreteSize && key1.numberValue <= key2.numberValue)

    override fun keyToRegion(key: USizeExpr) =
        when (key) {
            is UConcreteSize -> SetRegion.singleton(key.numberValue)
            else -> SetRegion.universe()
        }

    override fun keyRangeRegion(from: USizeExpr, to: USizeExpr) =
        when (from) {
            is UConcreteSize ->
                when (to) {
                    is UConcreteSize -> SetRegion.ofSequence((from.numberValue..to.numberValue).asSequence())
                    else -> SetRegion.universe()
                }

            else -> SetRegion.universe()
        }

    override fun topRegion() =
        SetRegion.universe<USizeType>()

    override fun bottomRegion() =
        SetRegion.empty<USizeType>()
}

/**
 * A composite key for symbolic arrays: every entry is determined by heap address of target buffer and its numeric index.
 */
typealias USymbolicArrayIndex = Pair<UHeapRef, USizeExpr>
typealias USymbolicArrayIndexRegion = ProductRegion<UHeapRefRegion, USizeRegion>

/**
 * Provides information about keys of input arrays.
 */
object USymbolicArrayIndexKeyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex, USymbolicArrayIndexRegion> {
    override fun eqSymbolic(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): UBoolExpr = with(key1.first.ctx) {
        return@with UHeapRefKeyInfo.eqSymbolic(key1.first, key2.first) and USizeExprKeyInfo.eqSymbolic(
            key1.second,
            key2.second
        )
    }

    override fun eqConcrete(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && USizeExprKeyInfo.eqConcrete(key1.second, key2.second)

    override fun cmpSymbolic(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): UBoolExpr = with(key1.first.ctx) {
        return@with UHeapRefKeyInfo.eqSymbolic(key1.first, key2.first) and USizeExprKeyInfo.cmpSymbolic(
            key1.second,
            key2.second
        )
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

typealias USymbolicMapKey<KeySort> = Pair<UHeapRef, UExpr<KeySort>>
typealias USymbolicMapKeyRegion<KeyReg> = ProductRegion<UHeapRefRegion, KeyReg>

/**
 * Provides information about keys of symbolic maps.
 */
class USymbolicMapKeyInfo<KeySort: USort, KeyReg: Region<KeyReg>>(
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, KeyReg>
): USymbolicCollectionKeyInfo<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<KeyReg>> {
    override fun eqSymbolic(key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): UBoolExpr =
        with(key1.first.ctx) {
            return@with UHeapRefKeyInfo.eqSymbolic(key1.first, key2.first) and keyInfo.eqSymbolic(key1.second, key2.second)
        }

    override fun eqConcrete(key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && keyInfo.eqConcrete(key1.second, key2.second)

    override fun cmpSymbolic(key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): UBoolExpr =
        with(key1.first.ctx) {
            return@with UHeapRefKeyInfo.eqSymbolic(key1.first, key2.first) and keyInfo.cmpSymbolic(key1.second, key2.second)
        }

    override fun cmpConcrete(key1: USymbolicMapKey<KeySort>, key2: USymbolicMapKey<KeySort>): Boolean =
        UHeapRefKeyInfo.eqConcrete(key1.first, key2.first) && keyInfo.cmpConcrete(key1.second, key2.second)
    override fun keyToRegion(key: USymbolicMapKey<KeySort>) =
        ProductRegion(
            UHeapRefKeyInfo.keyToRegion(key.first),
            keyInfo.keyToRegion(key.second)
        )
    override fun keyRangeRegion(from: USymbolicMapKey<KeySort>, to: USymbolicMapKey<KeySort>): USymbolicMapKeyRegion<KeyReg> {
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
