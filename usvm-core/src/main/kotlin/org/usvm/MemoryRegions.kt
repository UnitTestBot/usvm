package org.usvm

import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import java.util.LinkedList
import java.util.NoSuchElementException

//region Memory region

/**
 * Represents the result of memory write operation.
 */
interface UUpdateNode<Key, ValueSort : USort> {
    /**
     * @returns True if the address [key] got overwritten by this write operation in *any* possible concrete state.
     */
    fun includesConcretely(key: Key): Boolean

    /**
     * @return Symbolic condition expressing that the address [key] got overwritten by this memory write operation.
     * If [includesConcretely] returns true, then this method is obligated to return [UTrue].
     * Returned condition must imply [guard].
     */
    fun includesSymbolically(key: Key): UBoolExpr

    /**
     * @see [UMemoryRegion.split]
     */
    fun split(
        key: Key,
        predicate: (UExpr<ValueSort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<ValueSort>>>,
        guardBuilder: GuardBuilder
    ): UUpdateNode<Key, ValueSort>?

    /**
     * @return Value which has been written into the address [key] during this memory write operation.
     */
    fun value(key: Key): UExpr<ValueSort>

    /**
     * Guard is a symbolic condition for this update. That is, this update is done only in states satisfying this guard.
     */
    val guard: UBoolExpr

    /**
     * Returns node with updated [guard] condition.
     */
    fun guardWith(guard: UBoolExpr): UUpdateNode<Key, ValueSort>
}

/**
 * Represents a sequence of memory writes.
 */
interface UMemoryUpdates<Key, Sort : USort> : Sequence<UUpdateNode<Key, Sort>> {
    /**
     * @return Relevant updates for a given key
     */
    fun read(key: Key): UMemoryUpdates<Key, Sort>

    /**
     * @return Memory region which obtained from this one by overwriting the address [key] with value [value].
     */
    fun write(key: Key, value: UExpr<Sort>): UMemoryUpdates<Key, Sort>

    /**
     * @return Memory region which obtained from this one by overwriting the address [key] with value [value]
     * guarded with condition [guard].
     */
    fun guardedWrite(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryUpdates<Key, Sort>

    /**
     * @see [UMemoryRegion.split]
     */
    fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<Sort>>>,
        guardBuilder: GuardBuilder
    ): UMemoryUpdates<Key, Sort>

    /**
     * @return Updates expressing copying the slice of [fromRegion] (see UMemoryRegion.copy)
     */
    fun copy(
        fromRegion: UMemoryRegion<Key, Sort>,
        fromKey: Key, toKey: Key,
        keyConverter: (Key) -> Key
    ): UMemoryUpdates<Key, Sort>
}

typealias UInstantiator<Key, Sort> = (key: Key, UMemoryRegion<Key, Sort>) -> USymbol<Sort>

/**
 * A uniform unbounded slice of memory. Indexed by [Key], stores symbolic values.
 */
data class UMemoryRegion<Key, Sort : USort>(
    val sort: Sort,
    private val updates: UMemoryUpdates<Key, Sort>,
    private val defaultValue: UExpr<Sort>?, // If defaultValue = null then this region is filled with symbolics
    private val instantiator: UInstantiator<Key, Sort>
) {
    private fun read(key: Key, updates: UMemoryUpdates<Key, Sort>): UExpr<Sort> {
        val iterator = updates.iterator()

        val hasUpdates = iterator.hasNext()
        if (!hasUpdates && defaultValue != null) {
            // Reading from untouched array filled with defaultValue
            return defaultValue
        }

        if (hasUpdates) {
            val entry = iterator.next()
            if (entry.includesConcretely(key)) {
                // The last write has overwritten the key
                return entry.value(key)
            }
        }

        val localizedRegion = if (updates === this.updates) {
            this
        } else {
            UMemoryRegion(sort, updates, defaultValue, instantiator)
        }

        return instantiator(key, localizedRegion)
    }

    fun read(key: Key): UExpr<Sort> {
        if (sort == sort.uctx.addressSort) {
            // Here we split concrete heap addresses from symbolic ones to optimize further memory operations.
            return splittingRead(key) { it is UConcreteHeapRef }
        }

        val updates = updates.read(key)
        return read(key, updates)
    }

    /**
     * Reads key from this memory region, but 'bubbles up' entries satisfying predicates.
     * For example, imagine we read for example key z from array A with two updates: v written into x and w into y.
     * Usual [read] produces the expression
     *      A{x <- v}{y <- w}[z]
     * If v satisfies [predicate] and w does not, then [splittingRead] instead produces the expression
     *      ite(y <> z /\ x = z, v, A{y <- w}[z]).
     * These two expressions are semantically equivalent, but the second one 'splits' v out of the rest
     * memory updates.
     */
    private fun splittingRead(key: Key, predicate: (UExpr<Sort>) -> Boolean): UExpr<Sort> {
        val ctx = sort.ctx
        val guardBuilder = GuardBuilder(ctx.trueExpr, ctx.trueExpr)
        val matchingWrites = LinkedList<Pair<UBoolExpr, UExpr<Sort>>>()
        val splittingUpdates = split(key, predicate, matchingWrites, guardBuilder).updates

        if (matchingWrites.isEmpty()) {
            return instantiator(key, this)
        }

        val reading = read(key, splittingUpdates)
        var iteAcc = reading

        for (write in matchingWrites) {
            iteAcc = ctx.mkIte(write.first, write.second, iteAcc)
        }

        return iteAcc
    }

    fun write(key: Key, value: UExpr<Sort>): UMemoryRegion<Key, Sort> {
        assert(value.sort == sort)

        val newUpdates = updates.write(key, value)
        return UMemoryRegion(sort, newUpdates, defaultValue, instantiator)
    }

    internal fun split(
        key: Key, predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<Sort>>>,
        guardBuilder: GuardBuilder
    ): UMemoryRegion<Key, Sort> {
        // TODO: either check in UMemoryRegion constructor that we do not construct memory region with
        //       non-null reference as default value, or implement splitting by default value.
        assert(defaultValue == null || !predicate(defaultValue))

        val count = matchingWrites.size
        val splittingUpdates = updates.read(key).split(key, predicate, matchingWrites, guardBuilder)
        val sizeRemainedUnchanged = matchingWrites.size == count

        return if (sizeRemainedUnchanged) this else UMemoryRegion(sort, splittingUpdates, defaultValue, instantiator)
    }

    /**
     * @return Memory region which obtained from this one by overwriting the range of addresses [[fromKey] : [toKey]]
     * with values from memory region [fromRegion] read from range
     * of addresses [[keyConverter] ([fromKey]) : [keyConverter] ([toKey])]
     */
    fun copy(
        fromRegion: UMemoryRegion<Key, Sort>,
        fromKey: Key, toKey: Key,
        keyConverter: (Key) -> Key
    ): UMemoryRegion<Key, Sort> {
        val updatesCopy = updates.copy(fromRegion, fromKey, toKey, keyConverter)
        return UMemoryRegion(sort, updatesCopy, defaultValue, instantiator)
    }
}

class GuardBuilder(var matchingUpdatesGuard: UBoolExpr, var nonMatchingUpdatesGuard: UBoolExpr)

//endregion

//region Flat memory updates

/**
 * Represents a single write of [value] into a memory address [key]
 */
class UPinpointUpdateNode<Key, ValueSort : USort>(
    val key: Key,
    private val value: UExpr<ValueSort>,
    private val keyEqualityComparer: (Key, Key) -> UBoolExpr,
    override val guard: UBoolExpr = value.ctx.trueExpr
) : UUpdateNode<Key, ValueSort> {
    override fun includesConcretely(key: Key) = this.key == key && guard == guard.ctx.trueExpr

    override fun includesSymbolically(key: Key): UBoolExpr =
        guard.ctx.mkAnd(keyEqualityComparer(this.key, key), guard) // TODO: use simplifying and!

    override fun value(key: Key): UExpr<ValueSort> = this.value

    override fun split(
        key: Key,
        predicate: (UExpr<ValueSort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<ValueSort>>>,
        guardBuilder: GuardBuilder
    ): UUpdateNode<Key, ValueSort>? {
        val keyEq = keyEqualityComparer(key, this.key)
        val ctx = value.ctx
        val keyDiseq = ctx.mkNot(keyEq)

        if (predicate(value)) {
            val guard = ctx.mkAnd(guardBuilder.nonMatchingUpdatesGuard, keyEq)
            matchingWrites.add(Pair(guard, value))
            guardBuilder.matchingUpdatesGuard = ctx.mkAnd(guardBuilder.matchingUpdatesGuard, keyDiseq)
            return null
        }

        val hadNonMatchingUpdates = guardBuilder.nonMatchingUpdatesGuard != ctx.trueExpr
        guardBuilder.nonMatchingUpdatesGuard = ctx.mkAnd(guardBuilder.nonMatchingUpdatesGuard, ctx.mkNot(keyEq))

        return if (hadNonMatchingUpdates) this.guardWith(guardBuilder.matchingUpdatesGuard) else this
    }

    override fun guardWith(guard: UBoolExpr): UUpdateNode<Key, ValueSort> =
        if (guard == guard.ctx.trueExpr) {
            this
        } else {
            val guardExpr = guard.ctx.mkAnd(this.guard, guard)
            UPinpointUpdateNode(key, value, keyEqualityComparer, guardExpr)
        }

    override fun equals(other: Any?): Boolean =
        other is UPinpointUpdateNode<*, *> && this.key == other.key  // Ignores value

    override fun hashCode(): Int = key.hashCode()  // Ignores value

    override fun toString(): String = "{$key <- $value}"
}

/**
 * Represents a synchronous overwriting the range of addresses [[fromKey] : [toKey]]
 * with values from memory region [region] read from range
 * of addresses [[keyConverter] ([fromKey]) : [keyConverter] ([toKey])]
 */
class URangedUpdateNode<Key, ValueSort : USort>(
    val fromKey: Key,
    val toKey: Key,
    private val region: UMemoryRegion<Key, ValueSort>,
    private val concreteComparer: (Key, Key) -> Boolean,
    private val symbolicComparer: (Key, Key) -> UBoolExpr,
    private val keyConverter: (Key) -> Key,
    override val guard: UBoolExpr = region.sort.ctx.trueExpr
) : UUpdateNode<Key, ValueSort> {
    override fun includesConcretely(key: Key): Boolean =
        concreteComparer(fromKey, key) && concreteComparer(key, toKey) && guard == guard.ctx.trueExpr

    override fun includesSymbolically(key: Key): UBoolExpr {
        val leftIsLefter = symbolicComparer(fromKey, key)
        val rightIsRighter = symbolicComparer(key, toKey)
        val ctx = leftIsLefter.ctx

        return ctx.mkAnd(leftIsLefter, rightIsRighter, guard) // TODO: use simplifying and!
    }

    override fun value(key: Key): UExpr<ValueSort> = region.read(keyConverter(key))

    override fun guardWith(guard: UBoolExpr): UUpdateNode<Key, ValueSort> =
        if (guard == guard.ctx.trueExpr) {
            this
        } else {
            val guardExpr = guard.ctx.mkAnd(this.guard, guard)
            URangedUpdateNode(fromKey, toKey, region, concreteComparer, symbolicComparer, keyConverter, guardExpr)
        }

    override fun equals(other: Any?): Boolean =
        other is URangedUpdateNode<*, *> && this.fromKey == other.fromKey && this.toKey == other.toKey  // Ignores update

    override fun hashCode(): Int = 31 * fromKey.hashCode() + toKey.hashCode()  // Ignores update

    override fun split(
        key: Key, predicate: (UExpr<ValueSort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<ValueSort>>>,
        guardBuilder: GuardBuilder
    ): UUpdateNode<Key, ValueSort> {
        val splittedRegion = region.split(key, predicate, matchingWrites, guardBuilder)
        if (splittedRegion === region) {
            return this
        }

        return URangedUpdateNode(fromKey, toKey, splittedRegion, concreteComparer, symbolicComparer, keyConverter)
    }
}

class UEmptyUpdates<Key, Sort : USort>(
    private val symbolicEq: (Key, Key) -> UBoolExpr,
    private val concreteCmp: (Key, Key) -> Boolean,
    private val symbolicCmp: (Key, Key) -> UBoolExpr
) : UMemoryUpdates<Key, Sort> {
    override fun read(key: Key): UMemoryUpdates<Key, Sort> = this

    override fun write(key: Key, value: UExpr<Sort>): UMemoryUpdates<Key, Sort> =
        UFlatUpdates(
            UPinpointUpdateNode(key, value, symbolicEq),
            next = null,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun guardedWrite(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryUpdates<Key, Sort> =
        UFlatUpdates(
            UPinpointUpdateNode(key, value, symbolicEq, guard),
            next = null,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun copy(fromRegion: UMemoryRegion<Key, Sort>, fromKey: Key, toKey: Key, keyConverter: (Key) -> Key) =
        UFlatUpdates(
            URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter),
            next = null,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<Sort>>>,
        guardBuilder: GuardBuilder
    ) = this

    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> = EmptyIterator()

    private class EmptyIterator<Key, Sort : USort> : Iterator<UUpdateNode<Key, Sort>> {
        override fun hasNext(): Boolean = false
        override fun next(): UUpdateNode<Key, Sort> = error("Advancing empty iterator")
    }
}

data class UFlatUpdates<Key, Sort : USort>(
    val node: UUpdateNode<Key, Sort>,
    val next: UMemoryUpdates<Key, Sort>?,
    private val symbolicEq: (Key, Key) -> UBoolExpr,
    private val concreteCmp: (Key, Key) -> Boolean,
    private val symbolicCmp: (Key, Key) -> UBoolExpr
) : UMemoryUpdates<Key, Sort> {
    override fun read(key: Key): UMemoryUpdates<Key, Sort> = this

    override fun write(key: Key, value: UExpr<Sort>): UMemoryUpdates<Key, Sort> =
        UFlatUpdates(
            UPinpointUpdateNode(key, value, symbolicEq),
            next = this,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun guardedWrite(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryUpdates<Key, Sort> =
        UFlatUpdates(
            UPinpointUpdateNode(key, value, symbolicEq, guard),
            next = this,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun copy(fromRegion: UMemoryRegion<Key, Sort>, fromKey: Key, toKey: Key, keyConverter: (Key) -> Key) =
        UFlatUpdates(
            URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter),
            next = this,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun split(
        key: Key, predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<Sort>>>,
        guardBuilder: GuardBuilder
    ): UMemoryUpdates<Key, Sort> {
        val splittingNode = node.split(key, predicate, matchingWrites, guardBuilder)
        val splittingNext = next?.split(key, predicate, matchingWrites, guardBuilder)

        if (splittingNode == null) {
            return splittingNext ?: UEmptyUpdates(symbolicEq, concreteCmp, symbolicCmp)
        }

        if (splittingNext === next) {
            return this
        }

        return UFlatUpdates(splittingNode, splittingNext, symbolicEq, concreteCmp, symbolicCmp)
    }

    /**
     * Returns updates in the FIFO order: the iterator emits updates from the oldest updates to the most recent one.
     * It means that the `initialNode` from the [UFlatUpdatesIterator] will be returned as the last element.
     */
    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> = UFlatUpdatesIterator(initialNode = this)

    private class UFlatUpdatesIterator<Key, Sort : USort>(
        initialNode: UFlatUpdates<Key, Sort>,
    ) : Iterator<UUpdateNode<Key, Sort>> {
        private val iterator: Iterator<UUpdateNode<Key, Sort>>

        init {
            val elements = mutableListOf<UUpdateNode<Key, Sort>>()
            var current: UFlatUpdates<Key, Sort>? = initialNode

            // Traverse over linked list of updates nodes and extract them into an array list
            while (current != null) {
                elements += current.node
                // We can safely apply `as?` since we are interested only in non-empty updates
                // and there are no `treeUpdates` as a `next` element of the `UFlatUpdates`
                current = current.next as? UFlatUpdates<Key, Sort>
            }

            iterator = elements.asReversed().iterator()
        }

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): UUpdateNode<Key, Sort> = iterator.next()
    }
}

//endregion

//region Tree memory updates

data class UTreeUpdates<Key, Reg : Region<Reg>, Sort : USort>(
    private val updates: RegionTree<UUpdateNode<Key, Sort>, Reg>,
    private val keyToRegion: (Key) -> Reg,
    private val keyRangeToRegion: (Key, Key) -> Reg,
    private val symbolicEq: (Key, Key) -> UBoolExpr,
    private val concreteCmp: (Key, Key) -> Boolean,
    private val symbolicCmp: (Key, Key) -> UBoolExpr
) : UMemoryUpdates<Key, Sort> {
    override fun read(key: Key): UTreeUpdates<Key, Reg, Sort> {
        val reg = keyToRegion(key)
        val updates = updates.localize(reg)
        if (updates === this.updates) {
            return this
        }

        return UTreeUpdates(updates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun write(key: Key, value: UExpr<Sort>): UTreeUpdates<Key, Reg, Sort> {
        val update = UPinpointUpdateNode(key, value, symbolicEq)
        val newUpdates = updates.write(keyToRegion(key), update, keyFilter = { it == key })

        return UTreeUpdates(newUpdates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun guardedWrite(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UTreeUpdates<Key, Reg, Sort> {
        val update = UPinpointUpdateNode(key, value, symbolicEq, guard)
        val newUpdates = updates.write(keyToRegion(key), update, keyFilter = { it == key })

        return UTreeUpdates(newUpdates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun copy(
        fromRegion: UMemoryRegion<Key, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: (Key) -> Key
    ): UTreeUpdates<Key, Reg, Sort> {
        val region = keyRangeToRegion(fromKey, toKey)
        val update = URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter)
        val newUpdates = updates.write(region, update, keyFilter = { it == update })

        return UTreeUpdates(newUpdates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<Sort>>>,
        guardBuilder: GuardBuilder
    ): UMemoryUpdates<Key, Sort> {
        TODO("Not yet implemented")
    }

    /**
     * Returns updates in the FIFO order: the iterator emits updates from the oldest updates to the most recent one.
     * Note that if some key in the tree is presented in more than one node, it will be returned exactly ones.
     */
    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> = TreeIterator(updates.iterator())

    override fun toString(): String {
        return "$updates"
    }

    private inner class TreeIterator(
        private val treeUpdatesIterator: Iterator<Pair<UUpdateNode<Key, Sort>, Reg>>
    ) : Iterator<UUpdateNode<Key, Sort>> {
        // A set of values we already emitted by this iterator.
        // Note that it contains ONLY elements that have duplicates by key in the RegionTree.
        private val emittedUpdates = hashSetOf<UUpdateNode<Key, Sort>>()

        // We can return just `hasNext` value without checking for duplicates since
        // the last node contains a unique key (because non-unique keys might occur only
        // as a result of splitting because of some other write operation).
        override fun hasNext(): Boolean = treeUpdatesIterator.hasNext()

        override fun next(): UUpdateNode<Key, Sort> {
            while (treeUpdatesIterator.hasNext()) {
                val (update, region) = treeUpdatesIterator.next()

                // To check, whether we have a duplicate for a particular key,
                // we have to check if an initial region (by USVM estimation) is equal
                // to the one stored in the current node.
                val initialRegion = when (update) {
                    is UPinpointUpdateNode<Key, Sort> -> keyToRegion(update.key)
                    is URangedUpdateNode<Key, Sort> -> keyRangeToRegion(update.fromKey, update.toKey)
                    else -> error("An unsupported type of UpdateNode is provided: ${update::class}")
                }
                val wasCloned = initialRegion != region

                // If a region from the current node is equal to the initial region,
                // it means that there were no write operation that caused nodes split,
                // and the node doesn't have `duplicates` in the tree.
                if (!wasCloned) {
                    return update
                }

                // If there are duplicates, we have to emit exactly one of them -- the first we encountered.
                // Otherwise, we might have a problem. For example, we write by key `j` that belongs to {1, 2} region.
                // Then we wrote 1 with region {1} and 2 with region {2}. We have the following tree:
                // ({1} -> (1, {1} -> j), {2} -> (2, {2} -> j)). Without any additional actions, its iterator
                // will emit the following values: (j, 1, j, 2). We don't want to deal with their region
                // during encoding, so, we want to go through this sequence and apply updates, but we cannot do it.
                // We write by key `j`, then by `1`, then again by `j`, which overwrites a more recent update
                // in the region {1} and causes the following memory: [j, 2] instead of [1, 2].
                if (update in emittedUpdates) {
                    continue
                }

                emittedUpdates += update

                return update
            }

            throw NoSuchElementException()
        }
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

fun indexLeSymbolic(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkBvSignedLessOrEqualExpr(idx1, idx2)  // TODO: use simplified comparison!

fun indexLeConcrete(idx1: USizeExpr, idx2: USizeExpr): Boolean =
    // TODO: to optimize things up, we could pass path constraints here and lookup the numeric bounds for idx1 and idx2
    idx1 == idx2 || (idx1 is UConcreteSize && idx2 is UConcreteSize && idx1.numberValue <= idx2.numberValue)

fun refIndexEq(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    // TODO: use simplified operations!
    return@with (idx1.first eq idx2.first) and indexEq(idx1.second, idx2.second)
}

fun refIndexCmpSymbolic(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    return@with (idx1.first eq idx2.first) and indexLeSymbolic(idx1.second, idx2.second)
}

fun refIndexCmpConcrete(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): Boolean =
    idx1.first == idx2.first && indexLeConcrete(idx1.second, idx2.second)

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

fun refIndexRegion(idx: USymbolicArrayIndex): UArrayIndexRegion = indexRegion(idx.second)
fun refIndexRangeRegion(
    idx1: USymbolicArrayIndex,
    idx2: USymbolicArrayIndex
): UArrayIndexRegion = indexRangeRegion(idx1.second, idx2.second)

typealias UInputFieldMemoryRegion<Sort> = UMemoryRegion<UHeapRef, Sort>
typealias UAllocatedArrayMemoryRegion<Sort> = UMemoryRegion<USizeExpr, Sort>
typealias UInputArrayMemoryRegion<Sort> = UMemoryRegion<USymbolicArrayIndex, Sort>
typealias UArrayLengthMemoryRegion = UMemoryRegion<UHeapRef, USizeSort>

fun <Sort : USort> emptyFlatRegion(
    sort: Sort,
    defaultValue: UExpr<Sort>?,
    instantiator: UInstantiator<UHeapRef, Sort>
) = UMemoryRegion(
    sort,
    UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
    defaultValue,
    instantiator
)

fun <Sort : USort> emptyAllocatedArrayRegion(
    sort: Sort,
    instantiator: UInstantiator<USizeExpr, Sort>
): UAllocatedArrayMemoryRegion<Sort> {
    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::indexRegion, ::indexRangeRegion, ::indexEq, ::indexLeConcrete, ::indexLeSymbolic
    )
    return UMemoryRegion(sort, updates, sort.uctx.mkDefault(sort), instantiator)
}

fun <Sort : USort> emptyInputArrayRegion(
    sort: Sort,
    instantiator: UInstantiator<USymbolicArrayIndex, Sort>
): UInputArrayMemoryRegion<Sort> {
    val updates = UTreeUpdates<USymbolicArrayIndex, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::refIndexRegion, ::refIndexRangeRegion, ::refIndexEq, ::refIndexCmpConcrete, ::refIndexCmpSymbolic
    )
    return UMemoryRegion(sort, updates, null, instantiator)
}

fun emptyArrayLengthRegion(ctx: UContext, instantiator: UInstantiator<UHeapRef, USizeSort>): UArrayLengthMemoryRegion =
    UMemoryRegion(
        ctx.sizeSort, UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
        defaultValue = null, instantiator
    )

//endregion
