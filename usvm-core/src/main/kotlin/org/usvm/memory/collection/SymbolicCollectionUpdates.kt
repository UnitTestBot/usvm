package org.usvm.memory.collection

import org.usvm.*
import org.usvm.memory.GuardedExpr
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.URangedUpdateNode
import org.usvm.memory.UUpdateNode
import org.usvm.memory.collection.adapter.USymbolicCollectionAdapter
import org.usvm.memory.collection.id.KeyMapper
import org.usvm.memory.collection.id.USymbolicCollectionId
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.emptyRegionTree

/**
 * Represents a sequence of memory writes.
 */
interface USymbolicCollectionUpdates<Key, Sort : USort> : Sequence<UUpdateNode<Key, Sort>> {
    /**
     * @return Relevant updates for a given key.
     */
    fun read(key: Key): USymbolicCollectionUpdates<Key, Sort>

    /**
     * @return Symbolic collection which is obtained from this one by overwriting the address [key] with value [value]
     * guarded with condition [guard].
     */
    fun write(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr = value.ctx.trueExpr
    ): USymbolicCollectionUpdates<Key, Sort>

    /**
     * Splits this [USymbolicCollectionUpdates] into two parts:
     * * Values of [UUpdateNode]s satisfying [predicate] are added to the [matchingWrites].
     * * [UUpdateNode]s unsatisfying [predicate] remain in the result updates.
     *
     * The [guardBuilder] is used to build guards for values added to [matchingWrites]. In the end, the [guardBuilder]
     * is updated and contains a predicate indicating that the [key] can't be included in any of visited [UUpdateNode]s.
     *
     * @return new [USymbolicCollectionUpdates] without writes satisfying [predicate].
     * @see [UUpdateNode.split]
     */
    fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): USymbolicCollectionUpdates<Key, Sort>

    /**
     * Returns a mapped [USymbolicCollection] using [keyMapper] and [composer].
     * It is used in [UComposer] during memory composition.
     * Throws away all updates for which [keyMapper] returns null.
     */
    fun <Type, MappedKey, MappedReg : Region<MappedReg>> filterMap(
        keyMapper: KeyMapper<Key, MappedKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedKey, MappedReg>
    ): USymbolicCollectionUpdates<MappedKey, Sort>

    /**
     * @return Updates which express copying the slice of [fromCollection] guarded with
     * condition [guard].
     *
     * @see USymbolicCollection.copyRange
     */
    fun <CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey> copyRange(
        fromCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
        adapter: USymbolicCollectionAdapter<SrcKey, Key>,
        guard: UBoolExpr,
    ): USymbolicCollectionUpdates<Key, Sort>

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

    /**
     * Accepts the [visitor]. Implementations should call [UMemoryUpdatesVisitor.visitInitialValue] firstly, then call
     * [UMemoryUpdatesVisitor.visitUpdate] in the chronological order
     * (from the oldest to the newest) with accumulated [Result].
     *
     * Uses [lookupCache] to shortcut the traversal. The actual key is determined by the
     * [USymbolicCollectionUpdates] implementation. A caller is responsible to maintain the lifetime of the [lookupCache].
     *
     * @return the final result.
     */
    fun <Result> accept(
        visitor: UMemoryUpdatesVisitor<Key, Sort, Result>,
        lookupCache: MutableMap<Any?, Result>,
    ): Result
}

/**
 * A generic memory updates visitor. Doesn't think about a cache.
 */
interface UMemoryUpdatesVisitor<Key, Sort : USort, Result> {
    fun visitSelect(result: Result, key: Key): UExpr<Sort>

    fun visitInitialValue(): Result

    fun visitUpdate(previous: Result, update: UUpdateNode<Key, Sort>): Result
}


//region Flat memory updates

class UFlatUpdates<Key, Sort : USort> private constructor(
    internal val node: UFlatUpdatesNode<Key, Sort>?,
    private val keyInfo: USymbolicCollectionKeyInfo<Key, *>,
) : USymbolicCollectionUpdates<Key, Sort> {
    constructor(keyInfo: USymbolicCollectionKeyInfo<Key, *>) : this(node = null, keyInfo)

    internal data class UFlatUpdatesNode<Key, Sort : USort>(
        val update: UUpdateNode<Key, Sort>,
        val next: UFlatUpdates<Key, Sort>,
    )

    override fun read(key: Key): UFlatUpdates<Key, Sort> =
        when {
            node != null && node.update.includesSymbolically(key).isFalse -> node.next.read(key)
            else -> this
        }

    override fun write(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UFlatUpdates<Key, Sort> =
        UFlatUpdates(UFlatUpdatesNode(UPinpointUpdateNode(key, keyInfo, value, guard), this), keyInfo)

    override fun <CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey> copyRange(
        fromCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
        adapter: USymbolicCollectionAdapter<SrcKey, Key>,
        guard: UBoolExpr,
    ): USymbolicCollectionUpdates<Key, Sort> = UFlatUpdates(
        UFlatUpdatesNode(
            URangedUpdateNode(fromCollection, adapter, guard),
            this
        ),
        keyInfo
    )

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): UFlatUpdates<Key, Sort> {
        node ?: return this
        val splitNode = node.update.split(key, predicate, matchingWrites, guardBuilder)
        val splitNext = node.next.split(key, predicate, matchingWrites, guardBuilder)

        if (splitNode == null) {
            return splitNext
        }

        if (splitNext === node.next && splitNode === node.update) {
            return this
        }

        return UFlatUpdates(UFlatUpdatesNode(splitNode, splitNext), keyInfo)
    }

    override fun <Type, MappedKey, MappedReg: Region<MappedReg>> filterMap(
        keyMapper: KeyMapper<Key, MappedKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedKey, MappedReg>
    ): UFlatUpdates<MappedKey, Sort> {
        @Suppress("UNCHECKED_CAST")
        node ?: return (this as UFlatUpdates<MappedKey, Sort>)
        // Map the current node and the next values recursively
        val mappedNode = node.update.map(keyMapper, composer, mappedKeyInfo)
        val mappedNext = node.next.filterMap(keyMapper, composer, mappedKeyInfo)
        if (mappedNode == null) {
            return mappedNext
        }

        // Doesn't apply the node, if its guard maps to `false`
        if (mappedNode.guard.isFalse) {
            return mappedNext
        }

        // If nothing changed, return this updates
        if (mappedNode === node.update && mappedNext === node.next) {
            // In this case Key = MappedKey is guaranteed, but type system can't express this
            @Suppress("UNCHECKED_CAST")
            return (this as UFlatUpdates<MappedKey, Sort>)
        }

        // Otherwise, construct a new one using the mapped values
        return UFlatUpdates(UFlatUpdatesNode(mappedNode, mappedNext), mappedKeyInfo)
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
            var current: UFlatUpdates<Key, Sort> = initialNode

            // Traverse over linked list of updates nodes and extract them into an array list
            while (true) {
                val node = current.node ?: break
                elements += node.update
                current = node.next
            }

            iterator = elements.asReversed().iterator()
        }

        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): UUpdateNode<Key, Sort> = iterator.next()
    }

    override fun lastUpdatedElementOrNull(): UUpdateNode<Key, Sort>? = node?.update

    override fun isEmpty(): Boolean = node == null

    override fun <Result> accept(
        visitor: UMemoryUpdatesVisitor<Key, Sort, Result>,
        lookupCache: MutableMap<Any?, Result>,
    ): Result = UFlatMemoryUpdatesFolder(visitor, lookupCache).fold()

    private inner class UFlatMemoryUpdatesFolder<Result>(
        private val visitor: UMemoryUpdatesVisitor<Key, Sort, Result>,
        private val cache: MutableMap<Any?, Result>,
    ) {
        fun fold() = foldFlatUpdates(this@UFlatUpdates)
        private fun foldFlatUpdates(updates: UFlatUpdates<Key, Sort>): Result =
            cache.getOrPut(updates) {
                val node = updates.node ?: return@getOrPut visitor.visitInitialValue()
                val accumulated = foldFlatUpdates(node.next)
                visitor.visitUpdate(accumulated, node.update)
            }
    }
}

//endregion

//region Tree memory updates

data class UTreeUpdates<Key, Reg : Region<Reg>, Sort : USort>(
    private val updates: RegionTree<Reg, UUpdateNode<Key, Sort>>,
    private val keyInfo: USymbolicCollectionKeyInfo<Key, Reg>
) : USymbolicCollectionUpdates<Key, Sort> {
    override fun read(key: Key): UTreeUpdates<Key, Reg, Sort> {
        val reg = keyInfo.keyToRegion(key)
        val updates = updates.localize(reg) { it.includesSymbolically(key).isFalse }
        if (updates === this.updates) {
            return this
        }

        return this.copy(updates = updates)
    }

    override fun write(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UTreeUpdates<Key, Reg, Sort> {
        val update = UPinpointUpdateNode(key, keyInfo, value, guard)
        val newUpdates = updates.write(
            keyInfo.keyToRegion(key),
            update,
            valueFilter = { it.isIncludedByUpdateConcretely(update) }
        )

        return this.copy(updates = newUpdates)
    }

    override fun <CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey> copyRange(
        fromCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
        adapter: USymbolicCollectionAdapter<SrcKey, Key>,
        guard: UBoolExpr
    ): UTreeUpdates<Key, Reg, Sort> {
        val update = URangedUpdateNode(fromCollection, adapter, guard)
        val newUpdates = updates.write(
            adapter.region(),
            update,
            valueFilter = { it.isIncludedByUpdateConcretely(update) }
        )

        return this.copy(updates = newUpdates)
    }

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): UTreeUpdates<Key, Reg, Sort> {
        // reconstructed region tree, including all updates unsatisfying `predicate(update.value(key))` in the same order
        var splitRegionTree = emptyRegionTree<Reg, UUpdateNode<Key, Sort>>()

        // add an update to result tree
        fun applyUpdate(update: UUpdateNode<Key, Sort>) {
            val region = when (update) {
                is UPinpointUpdateNode<Key, Sort> -> keyInfo.keyToRegion(update.key)
                is URangedUpdateNode<*, *, Key, Sort> -> update.adapter.region()
            }
            splitRegionTree =
                splitRegionTree.write(region, update, valueFilter = { it.isIncludedByUpdateConcretely(update) })
        }


        var hasChanged = false
        // here we split updates from the newest to the oldest
        val splitUpdates = toMutableList<UUpdateNode<Key, Sort>?>().apply { reverse() }
        for ((idx, update) in splitUpdates.withIndex()) {
            val splitUpdate = update?.split(key, predicate, matchingWrites, guardBuilder)
            hasChanged = hasChanged or (update !== splitUpdate)
            splitUpdates[idx] = splitUpdate
        }

        // if nothing changed, return this
        if (!hasChanged) {
            return this
        }

        // traverse all updates one by one from the oldest one
        // here we collect matchingWrites and update guardBuilder in the correct order (from the newest to the oldest)
        for (update in splitUpdates.asReversed()) {
            if (update != null) {
                applyUpdate(update)
            }
        }


        return this.copy(updates = splitRegionTree)
    }


    override fun <Type, MappedKey, MappedReg : Region<MappedReg>> filterMap(
        keyMapper: KeyMapper<Key, MappedKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedKey, MappedReg>
    ): UTreeUpdates<MappedKey, MappedReg, Sort> {
        var mappedNodeFound = false

        // Traverse [updates] using its iterator and fold them into a new updates tree with new mapped nodes
        val initialEmptyTree = emptyRegionTree<MappedReg, UUpdateNode<MappedKey, Sort>>()
        val mappedUpdates = updates.fold(initialEmptyTree) { mappedUpdatesTree, updateNodeWithRegion ->
            val (updateNode, _) = updateNodeWithRegion
            // Map current node
            val mappedUpdateNode = updateNode.map(keyMapper, composer, mappedKeyInfo)


            // Save information about whether something changed in the current node or not
            if (mappedUpdateNode !== updateNode) {
                mappedNodeFound = true
            }

            // Ignore nodes which don't go into [targetCollectionId] after mapping
            if (mappedUpdateNode == null) {
                return@fold mappedUpdatesTree
            }

            // Note that following code should be executed after checking for reference equality of a mapped node.
            // Otherwise, it is possible that for a tree with several impossible writes
            // it will be returned as a result, instead of an empty one.

            // Extract a new region by the mapped node
            val newRegion = when (mappedUpdateNode) {
                is UPinpointUpdateNode -> {
                    mappedKeyInfo.keyToRegion(mappedUpdateNode.key)
                }

                is URangedUpdateNode<*, *, *, Sort> -> {
                    mappedUpdateNode as URangedUpdateNode<*, *, MappedKey, Sort>
                    mappedUpdateNode.adapter.region()
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
        return if (mappedNodeFound || mappedKeyInfo != keyInfo) {
            UTreeUpdates(updates = mappedUpdates, mappedKeyInfo)
        } else {
            @Suppress("UNCHECKED_CAST")
            this as UTreeUpdates<MappedKey, MappedReg, Sort>
        }
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
        private val treeUpdatesIterator: Iterator<Pair<UUpdateNode<Key, Sort>, Reg>>,
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

                val wasCloned = checkWasCloned(update, region)

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

    internal fun checkWasCloned(update: UUpdateNode<Key, Sort>, region: Region<*>): Boolean {
        // To check, whether we have a duplicate for a particular key,
        // we have to check if an initial region (by USVM estimation) is equal
        // to the one stored in the current node.
        val initialRegion = when (update) {
            is UPinpointUpdateNode<Key, Sort> -> keyInfo.keyToRegion(update.key)
            is URangedUpdateNode<*, *, Key, Sort> -> update.adapter.region()
        }
        val wasCloned = initialRegion != region
        return wasCloned
    }

    override fun lastUpdatedElementOrNull(): UUpdateNode<Key, Sort>? =
        updates.entries.entries.lastOrNull()?.value?.first

    override fun isEmpty(): Boolean = updates.entries.isEmpty()
    override fun <Result> accept(
        visitor: UMemoryUpdatesVisitor<Key, Sort, Result>,
        lookupCache: MutableMap<Any?, Result>,
    ): Result = UTreeMemoryUpdatesFolder(visitor, lookupCache).fold()

    private inner class UTreeMemoryUpdatesFolder<Result>(
        private val visitor: UMemoryUpdatesVisitor<Key, Sort, Result>,
        private val cache: MutableMap<Any?, Result>,
    ) {
        fun fold() = leftMostFold(updates)

        private val emittedUpdates = hashSetOf<UUpdateNode<Key, Sort>>()

        /**
         * [leftMostFold] visits only nodes marked by a star `*`.
         * Nodes marked by an at `@` visited in [notLeftMostFold].
         *
         * ```
         *       * @
         *      /   \
         *     /     \
         *    * @ @   @@
         *   /  |  \
         *  /   @@  @@
         * *
         *```
         */
        private fun leftMostFold(updates: RegionTree<*, UUpdateNode<Key, Sort>>): Result =
            cache.getOrPut(updates) {
                val entryIterator = updates.entries.iterator()
                if (!entryIterator.hasNext()) {
                    visitor.visitInitialValue()
                } else {
                    val (update, nextUpdates) = entryIterator.next().value
                    var result = leftMostFold(nextUpdates)
                    result = visitor.visitUpdate(result, update)
                    notLeftMostFold(result, entryIterator)
                }
            }

        private fun notLeftMostFold(
            accumulator: Result,
            iterator: Iterator<Map.Entry<Region<*>, Pair<UUpdateNode<Key, Sort>, RegionTree<*, UUpdateNode<Key, Sort>>>>>,
        ): Result {
            var accumulated = accumulator
            while (iterator.hasNext()) {
                val (reg, entry) = iterator.next()
                val (update, tree) = entry
                accumulated = notLeftMostFold(accumulated, tree.entries.iterator())

                accumulated = addIfNeeded(accumulated, update, reg)
            }
            return accumulated
        }

        private fun addIfNeeded(accumulated: Result, update: UUpdateNode<Key, Sort>, region: Region<*>): Result {
            if (checkWasCloned(update, region)) {
                if (update in emittedUpdates) {
                    return accumulated
                }
                emittedUpdates += update
                visitor.visitUpdate(accumulated, update)
            }

            return visitor.visitUpdate(accumulated, update)
        }
    }
}

//endregion
