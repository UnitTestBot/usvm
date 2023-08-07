package org.usvm.memory

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UConcreteSize
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexType
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.uctx
import org.usvm.util.Region
import org.usvm.util.SetRegion
import org.usvm.util.TrivialRegion

/**
 * Provides information about entities used as keys of symbolic collections.
 */
interface USymbolicCollectionKeyInfo<Key> {
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
    fun <Reg: Region<Reg>> keyToRegion(key: Key): Reg

    /**
     * Returns region that over-approximates the range of indices [[from] .. [to]]
     */
    fun <Reg: Region<Reg>> keyRangeRegion(from: Key, to: Key): Reg
}

/**
 * Provides information about heap references used as symbolic collection keys.
 */
class UHeapRefKeyInfo: USymbolicCollectionKeyInfo<UHeapRef> {
    override fun eqSymbolic(key1: UHeapRef, key2: UHeapRef): UBoolExpr =
        key1.uctx.mkHeapRefEq(key1, key2)

    override fun eqConcrete(key1: UHeapRef, key2: UHeapRef): Boolean =
        key1 == key2

    override fun cmpSymbolic(key1: UHeapRef, key2: UHeapRef): UBoolExpr =
        error("Heap references should not be compared!")

    override fun cmpConcrete(key1: UHeapRef, key2: UHeapRef): Boolean =
        error("Heap references should not be compared!")

    @Suppress("UNCHECKED_CAST")
    override fun <Reg: Region<Reg>> keyToRegion(key: UHeapRef): Reg =
        if (key is UConcreteHeapRef){
            SetRegion.singleton(key) as Reg
        } else {
            SetRegion.universe<UConcreteHeapAddress>() as Reg
        }

    override fun <Reg: Region<Reg>> keyRangeRegion(from: UHeapRef, to: UHeapRef): Reg =
        error("This should not be called!")
}

fun indexEq(idx1: USizeExpr, idx2: USizeExpr) =
    idx1.uctx.mkEq(idx1, idx2)

fun indexCmpSymbolic(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkBvSignedLessOrEqualExpr(idx1, idx2)

fun indexCmpConcrete(idx1: USizeExpr, idx2: USizeExpr): Boolean =
    idx1 == idx2 || (idx1 is UConcreteSize && idx2 is UConcreteSize && idx1.numberValue <= idx2.numberValue)

// TODO: change it to intervals region
typealias UArrayIndexRegion = SetRegion<UIndexType>

fun indexRegion(idx: USizeExpr): UArrayIndexRegion =
    when (idx) {
        is UConcreteSize -> SetRegion.singleton(idx.numberValue)
        else -> SetRegion.universe()
    }

fun indexRangeRegion(idx1: USizeExpr, idx2: USizeExpr): UArrayIndexRegion =
    when (idx1) {
        is UConcreteSize ->
            when (idx2) {
                is UConcreteSize -> SetRegion.ofSequence((idx1.numberValue..idx2.numberValue).asSequence())
                else -> SetRegion.universe()
            }

        else -> SetRegion.universe()
    }

/**
 * Provides information about numeric values used as symbolic collection keys.
 */
class USizeExprKeyInfo: USymbolicCollectionKeyInfo<USizeExpr> {
    override fun eqSymbolic(key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        indexEq(key1, key2)

    override fun eqConcrete(key1: USizeExpr, key2: USizeExpr): Boolean =
        key1 === key2

    override fun cmpSymbolic(key1: USizeExpr, key2: USizeExpr): UBoolExpr =
        indexCmpSymbolic(key1, key2)

    override fun cmpConcrete(key1: USizeExpr, key2: USizeExpr): Boolean =
        indexCmpConcrete(key1, key2)

    @Suppress("UNCHECKED_CAST")
    override fun <Reg: Region<Reg>> keyToRegion(key: USizeExpr): Reg =
        indexRegion(key) as Reg

    @Suppress("UNCHECKED_CAST")
    override fun <Reg: Region<Reg>> keyRangeRegion(from: USizeExpr, to: USizeExpr): Reg =
        indexRangeRegion(from, to) as Reg
}

/**
 * A composite key for symbolic arrays: every entry is determined by heap address of target buffer and its numeric index.
 */
typealias USymbolicArrayIndex = Pair<UHeapRef, USizeExpr>

/**
 * Provides information about keys of input arrays.
 */
class USymbolicArrayIndexKeyInfo: USymbolicCollectionKeyInfo<USymbolicArrayIndex> {
    override fun eqSymbolic(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): UBoolExpr = with(key1.first.ctx) {
        return@with (key1.first eq key2.first) and indexEq(key1.second, key2.second)
    }

    override fun eqConcrete(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): Boolean =
        key1.first == key2.first && key1.second == key2.second

    override fun cmpSymbolic(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): UBoolExpr = with(key1.first.ctx) {
        return@with (key1.first eq key2.first) and indexCmpSymbolic(key1.second, key2.second)
    }

    override fun cmpConcrete(key1: USymbolicArrayIndex, key2: USymbolicArrayIndex): Boolean =
        key1.first == key2.first && indexCmpConcrete(key1.second, key2.second)

    @Suppress("UNCHECKED_CAST")
    override fun <Reg: Region<Reg>> keyToRegion(key: USymbolicArrayIndex): Reg =
        indexRegion(key.second) as Reg

    @Suppress("UNCHECKED_CAST")
    override fun <Reg: Region<Reg>> keyRangeRegion(from: USymbolicArrayIndex, to: USymbolicArrayIndex): Reg =
        indexRangeRegion(from.second, to.second) as Reg
}

typealias USymbolicMapKey<KeySort> = Pair<UHeapRef, UExpr<KeySort>>

fun indexFullRangeRegion(): UArrayIndexRegion = SetRegion.universe()

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyRegion(
    descriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key: USymbolicMapKey<KeySort>
): Reg = descriptor.mkKeyRegion(key.second)

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyRangeRegion(
    descriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): Reg = descriptor.mkKeyRangeRegion(key1.second, key2.second)

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyEq(
    descriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): UBoolExpr = with(key1.first.ctx) {
    (key1.first eq key2.first) and descriptor.keyEqSymbolic(key1.second, key2.second)
}

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyCmpSymbolic(
    keyDescriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): UBoolExpr = with(key1.first.ctx) {
    (key1.first eq key2.first) and keyDescriptor.keyCmpSymbolic(key1.second, key2.second)
}

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyCmpConcrete(
    keyDescriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): Boolean = (key1.first == key2.first) && keyDescriptor.keyCmpConcrete(key1.second, key2.second)


