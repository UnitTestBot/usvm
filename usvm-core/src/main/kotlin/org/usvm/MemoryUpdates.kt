package org.usvm

import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.emptyRegionTree
import java.util.*

/**
 * Represents a sequence of memory writes.
 */
interface UMemoryUpdates<Key, Sort : USort> : Sequence<UUpdateNode<Key, Sort>> {
    /**
     * @return Relevant updates for a given key
     */
    fun read(key: Key): UMemoryUpdates<Key, Sort>

    /**
     * @return Memory region which obtained from this one by overwriting the address [key] with value [value]
     * guarded with condition [guard].
     */
    fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryUpdates<Key, Sort>

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
     * Returns a mapped [UMemoryRegion] using [keyMapper] and [composer].
     * It is used in [UComposer] during memory composition.
     */
    fun <Field, Type> map(keyMapper: (Key) -> Key, composer: UComposer<Field, Type>): UMemoryUpdates<Key, Sort>

    /**
     * @return Updates expressing copying the slice of [fromRegion] (see UMemoryRegion.copy)
     */
    fun <ArrayType, RegionId : UArrayId<ArrayType, SrcKey>, SrcKey> copy(
        fromRegion: UMemoryRegion<RegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): UMemoryUpdates<Key, Sort>

    /**
     * Returns the last updated element if there were any updates or null otherwise.
     *
     * Note: this operation might significantly affect performance, it should be implemented
     * considering that it will be called very often.
     */
    fun lastUpdatedElementOrNull(): UUpdateNode<Key, Sort>?

    /**
     * Returns true if there were any updates and false otherwise.
     */
    fun isEmpty(): Boolean
}


//region Flat memory updates

class UEmptyUpdates<Key, Sort : USort>(
    private val symbolicEq: (Key, Key) -> UBoolExpr,
    private val concreteCmp: (Key, Key) -> Boolean,
    private val symbolicCmp: (Key, Key) -> UBoolExpr
) : UMemoryUpdates<Key, Sort> {
    override fun read(key: Key): UEmptyUpdates<Key, Sort> = this

    override fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UFlatUpdates<Key, Sort> =
        UFlatUpdates(
            UPinpointUpdateNode(key, value, symbolicEq, guard),
            next = null,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun <ArrayType, RegionId : UArrayId<ArrayType, SrcKey>, SrcKey> copy(
        fromRegion: UMemoryRegion<RegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): UFlatUpdates<Key, Sort> = UFlatUpdates(
        URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter, guard),
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
    ): UEmptyUpdates<Key, Sort> = this

    override fun <Field, Type> map(
        keyMapper: (Key) -> Key,
        composer: UComposer<Field, Type>
    ): UEmptyUpdates<Key, Sort> = this

    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> = EmptyIterator()

    private class EmptyIterator<Key, Sort : USort> : Iterator<UUpdateNode<Key, Sort>> {
        override fun hasNext(): Boolean = false
        override fun next(): UUpdateNode<Key, Sort> = error("Advancing empty iterator")
    }

    override fun lastUpdatedElementOrNull(): UUpdateNode<Key, Sort>? = null

    override fun isEmpty(): Boolean = true
}

data class UFlatUpdates<Key, Sort : USort>(
    val node: UUpdateNode<Key, Sort>,
    val next: UMemoryUpdates<Key, Sort>?,
    private val symbolicEq: (Key, Key) -> UBoolExpr,
    private val concreteCmp: (Key, Key) -> Boolean,
    private val symbolicCmp: (Key, Key) -> UBoolExpr
) : UMemoryUpdates<Key, Sort> {
    override fun read(key: Key): UMemoryUpdates<Key, Sort> = this

    override fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UFlatUpdates<Key, Sort> =
        UFlatUpdates(
            UPinpointUpdateNode(key, value, symbolicEq, guard),
            next = this,
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun <ArrayType, RegionId : UArrayId<ArrayType, SrcKey>, SrcKey> copy(
        fromRegion: UMemoryRegion<RegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): UMemoryUpdates<Key, Sort> = UFlatUpdates(
        URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter, guard),
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

    override fun <Field, Type> map(
        keyMapper: (Key) -> Key,
        composer: UComposer<Field, Type>
    ): UFlatUpdates<Key, Sort> {
        // Map the current node and the next values recursively
        val mappedNode = node.map(keyMapper, composer)
        val mappedNext = next?.map(keyMapper, composer)

        // If nothing changed, return this updates
        if (mappedNode === node && mappedNext === next) {
            return this
        }

        // Otherwise, construct a new one using the mapped values
        return UFlatUpdates(mappedNode, mappedNext, symbolicEq, concreteCmp, symbolicCmp)
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

    override fun lastUpdatedElementOrNull(): UUpdateNode<Key, Sort> = node

    override fun isEmpty(): Boolean = false
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

    override fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UTreeUpdates<Key, Reg, Sort> {
        val update = UPinpointUpdateNode(key, value, symbolicEq, guard)
        val region = keyToRegion(key)

        val newUpdates = updates.write(region, update, keyFilter = { it == update })

        return UTreeUpdates(newUpdates, keyToRegion, keyRangeToRegion, symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun <ArrayType, RegionId : UArrayId<ArrayType, SrcKey>, SrcKey> copy(
        fromRegion: UMemoryRegion<RegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): UMemoryUpdates<Key, Sort> {
        val region = keyRangeToRegion(fromKey, toKey)
        val update = URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter, guard)
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

    override fun <Field, Type> map(
        keyMapper: (Key) -> Key,
        composer: UComposer<Field, Type>
    ): UTreeUpdates<Key, Reg, Sort> {
        var mappedNodeFound = false

        // Traverse [updates] using its iterator and fold them into a new updates tree with new mapped nodes
        val initialEmptyTree = emptyRegionTree<UUpdateNode<Key, Sort>, Reg>()
        val mappedUpdates = updates.fold(initialEmptyTree) { mappedUpdatesTree, updateNodeWithRegion ->
            val (updateNode, oldRegion) = updateNodeWithRegion
            // Map current node
            val mappedUpdateNode = updateNode.map(keyMapper, composer)

            // Save information about whether something changed in the current node or not
            if (mappedUpdateNode !== updateNode) {
                mappedNodeFound = true
            }

            // Note that following code should be executed after checking for reference equality of a mapped node.
            // Otherwise, it is possible that for a tree with several impossible writes
            // it will be returned as a result, instead of an empty one.

            // Extract a new region by the mapped node
            val newRegion = when (updateNode) {
                is UPinpointUpdateNode -> {
                    mappedUpdateNode as UPinpointUpdateNode
                    val currentRegion = keyToRegion(mappedUpdateNode.key)
                    oldRegion.intersect(currentRegion)
                }

                is URangedUpdateNode<*, *, *, Key, Sort> -> {
                    mappedUpdateNode as URangedUpdateNode<*, *, *, Key, Sort>
                    val currentRegion = keyRangeToRegion(mappedUpdateNode.fromKey, mappedUpdateNode.toKey)
                    oldRegion.intersect(currentRegion)
                }
            }

            // Ignore nodes estimated with an empty region
            if (newRegion.isEmpty) {
                return@fold mappedUpdatesTree
            }

            // Otherwise, write the mapped node by a new region with a corresponding
            // key filter for deduplication
            mappedUpdatesTree.write(newRegion, mappedUpdateNode) { it == mappedUpdateNode }
        }

        // If at least one node was changed, return a new updates, otherwise return this
        return if (mappedNodeFound) copy(updates = mappedUpdates) else this
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
                    is URangedUpdateNode<*, *, *, Key, Sort> -> keyRangeToRegion(update.fromKey, update.toKey)
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

    override fun lastUpdatedElementOrNull(): UUpdateNode<Key, Sort>? =
        updates.entries.entries.lastOrNull()?.value?.first

    override fun isEmpty(): Boolean = updates.entries.isEmpty()
}

//endregion