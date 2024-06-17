package org.usvm.memory

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.isFalse
import org.usvm.regions.Region
import org.usvm.regions.RegionTree
import org.usvm.regions.emptyRegionTree

/**
 * Represents a sequence of memory writes.
 */
interface USymbolicCollectionUpdates<Key, Sort : USort> : Sequence<UUpdateNode<Key, Sort>> {
    /**
     * Reads from collection updates and composes relevant ones. May filter out irrelevant updates for the [key].
     *
     * @return Relevant updates for a given key.
     */
    fun read(key: Key, composer: UComposer<*, *>?): USymbolicCollectionUpdates<Key, Sort>

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
        composer: UComposer<*, *>?,
    ): USymbolicCollectionUpdates<Key, Sort>

    /**
     * Writes to this [USymbolicCollectionUpdates] preserving the invariant
     * that all records satisfying predicate are hierarchically older than those that do not satisfy it.
     *
     * If [value] satisfies predicate, calls [write]. Otherwise traverses current collection from newest records to
     * oldest, modifying their guard so that they are not overwritten by [key], and places current record under
     * invariant tree.
     *
     * @return new [USymbolicCollectionUpdates] with the preserved invariant.
     */
    fun splitWrite(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr = value.ctx.trueExpr,
        composer: UComposer<*, *>? = null,
        predicate: (UExpr<Sort>) -> Boolean,
    ): USymbolicCollectionUpdates<Key, Sort>

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

    override fun read(key: Key, composer: UComposer<*, *>?): UFlatUpdates<Key, Sort> =
        when {
            node != null && node.update.includesSymbolically(key, composer).isFalse -> node.next.read(key, composer)
            else -> this
        }

    override fun write(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): UFlatUpdates<Key, Sort> =
        UFlatUpdates(
            UFlatUpdatesNode(
                UPinpointUpdateNode(
                    key,
                    keyInfo,
                    value,
                    guard
                ), this
            ), keyInfo
        )

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
        composer: UComposer<*, *>?,
    ): UFlatUpdates<Key, Sort> {
        node ?: return this
        val splitNode = node.update.split(key, predicate, matchingWrites, guardBuilder, composer)

        // False in the guardBuilder means that no nodes after this one are relevant
        val splitNext = if (guardBuilder.isFalse) {
            UFlatUpdates(keyInfo)
        } else {
            node.next.split(key, predicate, matchingWrites, guardBuilder, composer)
        }

        if (splitNode == null) {
            return splitNext
        }

        if (splitNext === node.next && splitNode === node.update) {
            return this
        }

        return UFlatUpdates(
            UFlatUpdatesNode(splitNode, splitNext),
            keyInfo
        )
    }

    /**
     * Returns updates in the FIFO order: the iterator emits updates from the oldest updates to the most recent one.
     * It means that the `initialNode` from the [UFlatUpdatesIterator] will be returned as the last element.
     */
    override fun iterator(): Iterator<UUpdateNode<Key, Sort>> =
        UFlatUpdatesIterator(initialNode = this)

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

    override fun splitWrite(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        composer: UComposer<*, *>?,
        predicate: (UExpr<Sort>) -> Boolean,
    ): USymbolicCollectionUpdates<Key, Sort> = write(key, value, guard)


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
    override fun read(key: Key, composer: UComposer<*, *>?): USymbolicCollectionUpdates<Key, Sort> {
        val reg = keyInfo.keyToRegion(key)
        val updates = updates.localize(reg) { !it.includesSymbolically(key, composer).isFalse }
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
        val reg = keyInfo.keyToRegion(key)
        val newUpdates = updates.write(
            reg,
            update
        ) { !it.isIncludedByUpdateConcretely(update) }

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
            update
        ) { !it.isIncludedByUpdateConcretely(update) }

        return this.copy(updates = newUpdates)
    }

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
        composer: UComposer<*, *>?,
    ): UTreeUpdates<Key, Reg, Sort> {
        var restRecordsAreIrrelevant = false
        val symbolicSubtreesStack =
            mutableListOf<Pair<Reg, Pair<UUpdateNode<Key, Sort>, RegionTree<Reg, UUpdateNode<Key, Sort>>>>>()

        fun traverseTreeWhilePredicateSatisfied(tree: RegionTree<Reg, UUpdateNode<Key, Sort>>) {
            // process nodes of the same level from newest to oldest
            for ((reg, entry) in tree.entries.toList().reversed()) {
                val (node, subtree) = entry
                val splitUpdate = node.split(key, predicate, matchingWrites, guardBuilder, composer)
                // range nodes are considered to belong to invariant tree since they can contain concretes
                if (splitUpdate === null || node is URangedUpdateNode<*, *, Key, Sort>) {
                    traverseTreeWhilePredicateSatisfied(subtree)
                    if (restRecordsAreIrrelevant) break
                } else {
                    // since [splitWrite] preserves invariant that records satisfying predicate are always
                    // at the top of the tree  we can just add rest branch to splitTreeEntries
                    symbolicSubtreesStack += reg to entry
                }

                if (guardBuilder.isFalse) {
                    restRecordsAreIrrelevant = true
                    break
                }
            }
        }

        traverseTreeWhilePredicateSatisfied(updates)

        val splitTreeEntries =
            mutableMapOf<Reg, Pair<UUpdateNode<Key, Sort>, RegionTree<Reg, UUpdateNode<Key, Sort>>>>()

        // reconstruct region tree, including all updates unsatisfying `predicate(update.value(key))` in the same order
        while (symbolicSubtreesStack.isNotEmpty()) {
            val (reg, entry) = symbolicSubtreesStack.removeLast()
            splitTreeEntries[reg] = entry
        }

        return this.copy(updates = RegionTree(splitTreeEntries.toPersistentMap()))
    }

    override fun splitWrite(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        composer: UComposer<*, *>?,
        predicate: (UExpr<Sort>) -> Boolean,
    ): UTreeUpdates<Key, Reg, Sort> {
        if (predicate(value)) {
            return write(key, value, guard)
        }
        val update = UPinpointUpdateNode(key, keyInfo, value, guard)
        val region = keyInfo.keyToRegion(key)
        val (included, disjoint) = updates.split(region) { !it.isIncludedByUpdateConcretely(update) }

        /*
         * Traverses tree while nodes satisfy predicate, guarding them from rewriting by [key], then
         * places [UUpdateNode] corresponding to current write operation under invariant tree.
         *
         * Adds additional nodes if needed since sum of regions of all nodes corresponding to current write operation
         * must be equal to  region of [key]: consider series of writes: a[1] = u, a[i] = v, where u satisfies
         * predicate and v does not. We need to hang a[i] = v with region 1 under a[1] = u and add node a[i] = v with
         * region Int \ 1.
         */
        fun placeUpdateUnderInvariantTree(
            region: Reg,
            tree: RegionTree<Reg, UUpdateNode<Key, Sort>>,
        ): RegionTree<Reg, UUpdateNode<Key, Sort>> {
            var dict = tree.entries
            var restRegion = region
            val ctx = guard.ctx

            for ((reg, entry) in tree.entries) {
                val (node, subtree) = entry
                if (predicate(node.value(key, composer)) || node is URangedUpdateNode<*, *, Key, Sort>) {
                    val excludeCondition = ctx.mkNot(node.includesSymbolically(key, composer))
                    val guardedNode = node.addGuard(excludeCondition)
                    val modifiedTree = placeUpdateUnderInvariantTree(reg, subtree)
                    dict = dict.put(reg, guardedNode to modifiedTree)
                } else {
                    dict = dict.put(reg, update to RegionTree(persistentMapOf(reg to entry)))
                }
                restRegion = region.subtract(reg)
            }

            return if (restRegion.isEmpty) {
                RegionTree(dict)
            } else {
                RegionTree(dict.put(restRegion, update to emptyRegionTree()))
            }
        }

        val modifiedTree = placeUpdateUnderInvariantTree(region, included)
        val unionTree = RegionTree(disjoint.entries.putAll(modifiedTree.entries))
        return this.copy(updates = unionTree)
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
        // Reference equality on UUpdateNodes is very important here.
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
