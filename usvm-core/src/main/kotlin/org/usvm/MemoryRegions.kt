package org.usvm

import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree

//region Memory region

/**
 * Represents the result of memory write operation.
 */
interface UUpdateNode<Key, ValueSort: USort> {
    /**
     * @returns True if the address [key] got overwritten by this memory write operation.
     */
    fun includesConcretely(key: Key): Boolean

    /**
     * @return Symbolic condition expressing that the address [key] got overwritten by this memory write operation.
     * If [includesConcretely] returns true, then this method is obligated to return [UTrue].
     */
    fun includesSymbolically(key: Key): UBoolExpr

    /**
     * @return Value which has been written into the address [key] during this memory write operation.
     */
    fun value(key: Key): UExpr<ValueSort>
}

/**
 * Represents a sequence of memory writes.
 */
interface UMemoryUpdates<Key, Sort: USort>: Sequence<UUpdateNode<Key, Sort>> {
    /**
     * @return Relevant updates for a given key
     */
    fun read(key: Key): UMemoryUpdates<Key, Sort>

    /**
     * @return Memory region which obtained from this one by overwriting the address [key] with value [value].
     */
    fun write(key: Key, value: UExpr<Sort>): UMemoryUpdates<Key, Sort>

    /**
     * @return Memory region which obtained from this one by overwriting the range of addresses [[fromKey] : [toKey]]
     * with values from memory region [fromRegion] read from range
     * of addresses [[keyConverter] ([fromKey]) : [keyConverter] ([toKey])]
     */
    fun copy(fromRegion: UMemoryRegion<Key, Sort>,
             fromKey: Key, toKey: Key,
             keyConverter: (Key) -> Key)
        : UMemoryUpdates<Key, Sort>
}

typealias UInstantiator<Key, Sort> = (key: Key, UMemoryRegion<Key, Sort>) -> UExpr<Sort>

/**
 * A uniform unbounded slice of memory. Indexed by [Key], stores symbolic values.
 */
data class UMemoryRegion<Key, Sort: USort>(
    val sort: Sort,
    private val updates: UMemoryUpdates<Key, Sort>,
    private val defaultValue: UExpr<Sort>?, // If defaultValue = null then this region is filled with symbolics
    private val instantiator: UInstantiator<Key, Sort>
)
{
    fun read(key: Key): UExpr<Sort> {
        val updates = updates.read(key)
        val iterator = updates.iterator()
        val hasUpdates = iterator.hasNext()
        if (!hasUpdates && defaultValue !== null) {
            // Reading from untouched array filled with defaultValue
            return defaultValue

        } else {
            if (hasUpdates) {
                val entry = iterator.next()
                if (entry.includesConcretely(key)) {
                    // The last write has overwritten the key
                    return entry.value(key)
                }
            }
            val localizedRegion =
                if (updates === this.updates) this
                else UMemoryRegion(sort, updates, defaultValue, instantiator)
            return instantiator(key, localizedRegion)
        }
    }

    fun write(key: Key, value: UExpr<Sort>): UMemoryRegion<Key, Sort> {
        assert(value.sort == sort)
        val newUpdates = updates.write(key, value)
        return UMemoryRegion(sort, newUpdates, defaultValue, instantiator)
    }
}

//endregion

//region Flat memory updates

/**
 * Represents a single write of  [value] into a memory address [key]
 */
class UPinpointUpdateNode<Key, ValueSort: USort>(private val key: Key, private val value: UExpr<ValueSort>,
                                                 private val keyEqualityComparer: (Key, Key) -> UBoolExpr)
    : UUpdateNode<Key, ValueSort>
{
    override fun includesConcretely(key: Key) = this.key == key

    override fun includesSymbolically(key: Key): UBoolExpr = keyEqualityComparer(this.key, key)

    override fun value(key: Key): UExpr<ValueSort> = this.value

    override fun equals(other: Any?): Boolean =
        other is UPinpointUpdateNode<*, *> && this.key == other.key  // Ignores value

    override fun hashCode(): Int = key.hashCode()  // Ignores value
}

/**
 * Represents a synchronous overwriting the range of addresses [[fromKey] : [toKey]]
 * with values from memory region [region] read from range
 * of addresses [[keyConverter] ([fromKey]) : [keyConverter] ([toKey])]
 */
class URangedUpdateNode<Key, ValueSort: USort>(private val fromKey: Key, private val toKey: Key,
                                               private val region: UMemoryRegion<Key, ValueSort>,
                                               private val concreteComparer: (Key, Key) -> Boolean,
                                               private val symbolicComparer: (Key, Key) -> UBoolExpr,
                                               private val keyConverter: (Key) -> Key)
    : UUpdateNode<Key, ValueSort>
{
    override fun includesConcretely(key: Key): Boolean =
        concreteComparer(fromKey, key) && concreteComparer(key, toKey)

    override fun includesSymbolically(key: Key): UBoolExpr {
        val leftIsLefter = symbolicComparer(fromKey, key)
        val rightIsRighter = symbolicComparer(key, toKey)
        val ctx = leftIsLefter.ctx
        return ctx.mkAnd(leftIsLefter, rightIsRighter) // TODO: use simplifying and!
    }

    override fun value(key: Key): UExpr<ValueSort> =
        region.read(key)

    override fun equals(other: Any?): Boolean =
        other is URangedUpdateNode<*, *> && this.fromKey == other.fromKey && this.toKey == other.toKey  // Ignores update

    override fun hashCode(): Int = 31 * fromKey.hashCode() + toKey.hashCode()  // Ignores update
}

class UEmptyUpdates<Key, Sort: USort>(private val symbolicEq: (Key, Key) -> UBoolExpr,
                                      private val concreteCmp: (Key, Key) -> Boolean,
                                      private val symbolicCmp: (Key, Key) -> UBoolExpr)
    : UMemoryUpdates<Key, Sort>
{
    private class EmptyIterator<Key, Sort: USort>: Iterator<UUpdateNode<Key, Sort>> {
        override fun hasNext(): Boolean = false
        override fun next(): UUpdateNode<Key, Sort> = error("Advancing empty iterator")
    }

    override fun read(key: Key): UMemoryUpdates<Key, Sort> = this

    override fun write(key: Key, value: UExpr<Sort>): UMemoryUpdates<Key, Sort> =
        UFlatUpdates(UPinpointUpdateNode(key, value, symbolicEq), null, symbolicEq, concreteCmp, symbolicCmp)

    override fun copy(fromRegion: UMemoryRegion<Key, Sort>, fromKey: Key, toKey: Key, keyConverter: (Key) -> Key) =
        UFlatUpdates(URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter), null,
                     symbolicEq, concreteCmp, symbolicCmp)

    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> = EmptyIterator()
}

data class UFlatUpdates<Key, Sort: USort>(val node: UUpdateNode<Key, Sort>,
                                          val next: UFlatUpdates<Key, Sort>?,
                                          private val symbolicEq: (Key, Key) -> UBoolExpr,
                                          private val concreteCmp: (Key, Key) -> Boolean,
                                          private val symbolicCmp: (Key, Key) -> UBoolExpr)
    : UMemoryUpdates<Key, Sort>
{
    private class UFlatUpdatesIterator<Key, Sort: USort>(private var current: UFlatUpdates<Key, Sort>?)
        : Iterator<UUpdateNode<Key, Sort>>
    {
        override fun hasNext(): Boolean = current === null
        override fun next(): UUpdateNode<Key, Sort> = current!!.node
    }

    override fun read(key: Key): UMemoryUpdates<Key, Sort> = this

    override fun write(key: Key, value: UExpr<Sort>): UMemoryUpdates<Key, Sort> =
        UFlatUpdates(UPinpointUpdateNode(key, value, symbolicEq), this, symbolicEq, concreteCmp, symbolicCmp)

    override fun copy(fromRegion: UMemoryRegion<Key, Sort>, fromKey: Key, toKey: Key, keyConverter: (Key) -> Key) =
        UFlatUpdates(URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter), this,
                     symbolicEq, concreteCmp, symbolicCmp)

    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> = UFlatUpdatesIterator(this)
}

//endregion

//region Tree memory updates

data class UTreeUpdates<Key, Reg: Region<Reg>, Sort: USort>(
        private val updates: RegionTree<UUpdateNode<Key, Sort>, Reg>,
        private val keyToRegion: (Key) -> Reg,
        private val keyRangeToRegion: (Key, Key) -> Reg,
        private val symbolicEq: (Key, Key) -> UBoolExpr,
        private val concreteCmp: (Key, Key) -> Boolean,
        private val symbolicCmp: (Key, Key) -> UBoolExpr)
    : UMemoryUpdates<Key, Sort>
{
    override fun read(key: Key): UTreeUpdates<Key, Reg, Sort> {
        val reg = keyToRegion(key)
        val updates = updates.localize(reg)
        if (updates === this.updates)
            return this
        return UTreeUpdates(updates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun write(key: Key, value: UExpr<Sort>): UTreeUpdates<Key, Reg, Sort> {
        val update = UPinpointUpdateNode(key, value, symbolicEq)
        val newUpdates = updates.write(keyToRegion(key), update)
        return UTreeUpdates(newUpdates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun copy(fromRegion: UMemoryRegion<Key, Sort>, fromKey: Key, toKey: Key, keyConverter: (Key) -> Key)
        : UTreeUpdates<Key, Reg, Sort>
    {
        val region = keyRangeToRegion(fromKey, toKey)
        val update = URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter)
        val newUpdates = updates.write(region, update)
        return UTreeUpdates(newUpdates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> {
        TODO("Not yet implemented")
    }
}

//endregion

//region Instantiations

typealias USymbolicArrayIndex = Pair<UHeapRef, USizeExpr>

fun heapRefEq(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    ref1.ctx.mkEq(ref1, ref2)  // TODO: use simplified equality!

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpSymbolic(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    error("Heap references should not be compared!")

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpConcrete(ref1: UHeapRef, ref2: UHeapRef): Boolean =
    error("Heap references should not be compared!")

fun indexEq(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkEq(idx1, idx2)  // TODO: use simplified equality!

fun indexCmpSymbolic(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkBvSignedLessExpr(idx1, idx2)  // TODO: use simplified comparison!

fun indexCmpConcrete(idx1: USizeExpr, idx2: USizeExpr): Boolean =
    // TODO: to optimize things up, we could pass path constraints here and lookup the numeric bounds for idx1 and idx2
    idx1 == idx2 || (idx1 is UConcreteSize && idx2 is UConcreteSize && idx1.numberValue <= idx2.numberValue)

fun refIndexEq(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    // TODO: use simplified operations!
    return@with (idx1.first eq idx2.first) and indexEq(idx1.second, idx2.second)
}

fun refIndexCmpSymbolic(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    return@with (idx1.first eq idx2.first) and indexCmpSymbolic(idx1.second, idx2.second)
}

fun refIndexCmpConcrete(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): Boolean =
    idx1.first == idx2.first && indexCmpConcrete(idx1.second, idx2.second)


// TODO: change it to intervals region
typealias UArrayIndexRegion = SetRegion<UIndexType>


fun indexRegion(idx: USizeExpr): UArrayIndexRegion =
    when(idx) {
        is UConcreteSize -> SetRegion.singleton(idx.numberValue)
        else -> SetRegion.universe()
    }
fun indexRangeRegion(idx1: USizeExpr, idx2: USizeExpr): UArrayIndexRegion =
    when(idx1) {
        is UConcreteSize ->
            when(idx2) {
                is UConcreteSize -> SetRegion.ofSequence((idx1.numberValue .. idx2.numberValue).asSequence())
                else -> SetRegion.universe()
            }
        else -> SetRegion.universe()
    }

fun refIndexRegion(idx: USymbolicArrayIndex): UArrayIndexRegion = indexRegion(idx.second)
fun refIndexRangeRegion(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UArrayIndexRegion =
    indexRangeRegion(idx1.second, idx2.second)

typealias UVectorMemoryRegion<Sort> = UMemoryRegion<UHeapRef, Sort>
typealias UAllocatedArrayMemoryRegion<Sort> = UMemoryRegion<USizeExpr, Sort>
typealias UInputArrayMemoryRegion<Sort> = UMemoryRegion<USymbolicArrayIndex, Sort>
typealias UArrayLengthMemoryRegion = UMemoryRegion<UHeapRef, USizeSort>

fun <Sort: USort> emptyFlatRegion(sort: Sort, defaultValue: UExpr<Sort>?, instantiator: UInstantiator<UHeapRef, Sort>) =
    UMemoryRegion(sort, UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic), defaultValue, instantiator)

fun <Sort: USort> emptyArrayRegion(sort: Sort, instantiator: UInstantiator<USizeExpr, Sort>): UAllocatedArrayMemoryRegion<Sort> {
    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(emptyRegionTree(),
        ::indexRegion, ::indexRangeRegion, ::indexEq, ::indexCmpConcrete, ::indexCmpSymbolic)
    return UMemoryRegion(sort, updates, sort.uctx.mkDefault(sort), instantiator)
}

fun <Sort: USort> emptyArrayCollectionRegion(sort: Sort, instantiator: UInstantiator<USymbolicArrayIndex, Sort>): UInputArrayMemoryRegion<Sort> {
    val updates = UTreeUpdates<USymbolicArrayIndex, UArrayIndexRegion, Sort>(emptyRegionTree(),
        ::refIndexRegion, ::refIndexRangeRegion, ::refIndexEq, ::refIndexCmpConcrete, ::refIndexCmpSymbolic)
    return UMemoryRegion(sort, updates, null, instantiator)
}

fun emptyArrayLengthRegion(ctx: UContext, instantiator: UInstantiator<UHeapRef, USizeSort>): UArrayLengthMemoryRegion =
    UMemoryRegion(ctx.sizeSort, UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
                  null, instantiator)

//endregion
