package org.usvm

import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.emptyRegionTree

/**
 * Represents a sequence of memory writes.
 */
interface UMemoryUpdates<Key, Sort : USort> : Sequence<UUpdateNode<Key, Sort>> {
    /**
     * @return Relevant updates for a given key.
     */
    fun read(key: Key): UMemoryUpdates<Key, Sort>

    /**
     * @return Memory region which is obtained from this one by overwriting the address [key] with value [value]
     * guarded with condition [guard].
     */
    fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr = value.ctx.trueExpr): UMemoryUpdates<Key, Sort>

    /**
     * Splits this [UMemoryUpdates] into two parts:
     * * Values of [UUpdateNode]s satisfying [predicate] are added to the [matchingWrites].
     * * [UUpdateNode]s unsatisfying [predicate] remain in the result updates.
     *
     * The [guardBuilder] is used to build guards for values added to [matchingWrites]. In the end, the [guardBuilder]
     * is updated and contains a predicate indicating that the [key] can't be included in any of visited [UUpdateNode]s.
     *
     * @return new [UMemoryUpdates] without writes satisfying [predicate].
     * @see [UUpdateNode.split]
     */
    fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): UMemoryUpdates<Key, Sort>

    /**
     * Returns a mapped [USymbolicMemoryRegion] using [keyMapper] and [composer].
     * It is used in [UComposer] during memory composition.
     */
    fun <Field, Type> map(keyMapper: KeyMapper<Key>, composer: UComposer<Field, Type>): UMemoryUpdates<Key, Sort>

    /**
     * @return Updates which express copying the slice of [fromRegion]  guarded with
     * condition [guard].
     *
     * @see USymbolicMemoryRegion.copyRange
     */
    fun <RegionId : UArrayId<*, SrcKey, Sort, RegionId>, SrcKey> copyRange(
        fromRegion: USymbolicMemoryRegion<RegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr,
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

    /**
     * Accepts the [visitor]. Calls `visitInitialValue` firstly, then calls `visitUpdateNode` in the chronological order
     * (from the oldest to the newest) with accumulated [Result].
     *
     * Uses [lookupCache] to shortcut the traversal. The actual key is determined by the
     * [UMemoryUpdates] implementation. It's caller's responsibility to maintain the lifetime of the [lookupCache].
     *
     * @return the final result.
     */
    fun <Result> accept(
        visitor: UMemoryUpdatesVisitor<Key, Sort, Result>,
        lookupCache: MutableMap<Any?, Result>,
    ): Result
}


//region Flat memory updates

class UFlatUpdates<Key, Sort : USort> private constructor(
    internal val node: UFlatUpdatesNode<Key, Sort>?,
    private val symbolicEq: (Key, Key) -> UBoolExpr,
    private val concreteCmp: (Key, Key) -> Boolean,
    private val symbolicCmp: (Key, Key) -> UBoolExpr,
) : UMemoryUpdates<Key, Sort> {
    constructor(
        symbolicEq: (Key, Key) -> UBoolExpr,
        concreteCmp: (Key, Key) -> Boolean,
        symbolicCmp: (Key, Key) -> UBoolExpr,
    ) : this(node = null, symbolicEq, concreteCmp, symbolicCmp)

    internal data class UFlatUpdatesNode<Key, Sort : USort>(
        val update: UUpdateNode<Key, Sort>,
        val next: UFlatUpdates<Key, Sort>,
    )

    override fun read(key: Key): UMemoryUpdates<Key, Sort> = this

    override fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UFlatUpdates<Key, Sort> =
        UFlatUpdates(
            UFlatUpdatesNode(UPinpointUpdateNode(key, value, symbolicEq, guard), this),
            symbolicEq,
            concreteCmp,
            symbolicCmp
        )

    override fun <RegionId : UArrayId<*, SrcKey, Sort, RegionId>, SrcKey> copyRange(
        fromRegion: USymbolicMemoryRegion<RegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr,
    ): UMemoryUpdates<Key, Sort> = UFlatUpdates(
        UFlatUpdatesNode(
            URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter, guard),
            this
        ),
        symbolicEq,
        concreteCmp,
        symbolicCmp
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

        return UFlatUpdates(UFlatUpdatesNode(splitNode, splitNext), symbolicEq, concreteCmp, symbolicCmp)
    }

    override fun <Field, Type> map(
        keyMapper: KeyMapper<Key>,
        composer: UComposer<Field, Type>
    ): UFlatUpdates<Key, Sort> {
        node ?: return this
        // Map the current node and the next values recursively
        val mappedNode = node.update.map(keyMapper, composer)
        val mappedNext = node.next.map(keyMapper, composer)

        // If nothing changed, return this updates
        if (mappedNode === node.update && mappedNext === node.next) {
            return this
        }

        // Otherwise, construct a new one using the mapped values
        return UFlatUpdates(UFlatUpdatesNode(mappedNode, mappedNext), symbolicEq, concreteCmp, symbolicCmp)
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
    ): Result =
        UFlatMemoryUpdatesFolder(visitor, lookupCache).fold()

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
    private val updates: RegionTree<UUpdateNode<Key, Sort>, Reg>,
    private val keyToRegion: (Key) -> Reg,
    private val keyRangeToRegion: (Key, Key) -> Reg,
    private val symbolicEq: (Key, Key) -> UBoolExpr,
    private val concreteCmp: (Key, Key) -> Boolean,
    private val symbolicCmp: (Key, Key) -> UBoolExpr,
) : UMemoryUpdates<Key, Sort> {
    override fun read(key: Key): UTreeUpdates<Key, Reg, Sort> {
        val reg = keyToRegion(key)
        val updates = updates.localize(reg)
        if (updates === this.updates) {
            return this
        }

        return this.copy(updates = updates)
    }

    override fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UTreeUpdates<Key, Reg, Sort> {
        val update = UPinpointUpdateNode(key, value, symbolicEq, guard)
        val newUpdates = updates.write(
            keyToRegion(key),
            update,
            keyFilter = { it.isIncludedByUpdateConcretely(update) }
        )

        return this.copy(updates = newUpdates)
    }

    override fun <RegionId : UArrayId<*, SrcKey, Sort, RegionId>, SrcKey> copyRange(
        fromRegion: USymbolicMemoryRegion<RegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): UTreeUpdates<Key, Reg, Sort> {
        val region = keyRangeToRegion(fromKey, toKey)
        val update = URangedUpdateNode(fromKey, toKey, fromRegion, concreteCmp, symbolicCmp, keyConverter, guard)
        val newUpdates = updates.write(
            region,
            update,
            keyFilter = { it.isIncludedByUpdateConcretely(update) }
        )

        return this.copy(updates = newUpdates)
    }

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): UTreeUpdates<Key, Reg, Sort> {
        // the suffix of the [updates], starting from the earliest update satisfying `predicate(update.value(key))`
        val updatesSuffix = mutableListOf<UUpdateNode<Key, Sort>?>()

        // reconstructed region tree, including all updates unsatisfying `predicate(update.value(key))` in the same order
        var splitUpdates = emptyRegionTree<UUpdateNode<Key, Sort>, Reg>()

        // add an update to result tree
        fun applyUpdate(update: UUpdateNode<Key, Sort>) {
            val region = when (update) {
                is UPinpointUpdateNode<Key, Sort> -> keyToRegion(update.key)
                is URangedUpdateNode<*, *, Key, Sort> -> keyRangeToRegion(update.fromKey, update.toKey)
            }
            splitUpdates = splitUpdates.write(region, update, keyFilter = { it.isIncludedByUpdateConcretely(update) })
        }

        // traverse all updates one by one from the oldest one
        for (update in this) {
            val satisfies = predicate(update.value(key))

            if (updatesSuffix.isNotEmpty()) {
                updatesSuffix += update
            } else if (satisfies) {
                // we found the first matched update, so we have to apply already visited updates
                // definitely unsatisfying `predicate(update.value(key))`
                for (prevUpdate in this) {
                    if (prevUpdate === update) {
                        break
                    }
                    applyUpdate(update)
                }
                updatesSuffix += update
            }
        }

        // no matching updates were found
        if (updatesSuffix.isEmpty()) {
            return this
        }

        // here we collect matchingWrites and update guardBuilder in the correct order (from the newest to the oldest)
        for (idx in updatesSuffix.indices.reversed()) {
            val update = requireNotNull(updatesSuffix[idx])
            updatesSuffix[idx] = update.split(key, predicate, matchingWrites, guardBuilder)
        }

        // here we apply the remaining updates
        for (update in updatesSuffix) {
            if (update != null) {
                applyUpdate(update)
            }
        }


        return this.copy(updates = splitUpdates)
    }


    override fun <Field, Type> map(
        keyMapper: KeyMapper<Key>,
        composer: UComposer<Field, Type>,
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

                is URangedUpdateNode<*, *, Key, Sort> -> {
                    mappedUpdateNode as URangedUpdateNode<*, *, Key, Sort>
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
            is UPinpointUpdateNode<Key, Sort> -> keyToRegion(update.key)
            is URangedUpdateNode<*, *, Key, Sort> -> keyRangeToRegion(update.fromKey, update.toKey)
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
    ): Result =
        UTreeMemoryUpdatesFolder(visitor, lookupCache).fold()

    private inner class UTreeMemoryUpdatesFolder<Result>(
        private val visitor: UMemoryUpdatesVisitor<Key, Sort, Result>,
        private val cache: MutableMap<Any?, Result>,
    ) {
        fun fold(): Result =
            cache.getOrPut(updates) {
                leftMostFold(updates)
            }

        private val emittedUpdates = hashSetOf<UUpdateNode<Key, Sort>>()

        private fun leftMostFold(updates: RegionTree<UUpdateNode<Key, Sort>, *>): Result {
            var result = cache[updates]

            if (result != null) {
                return result
            }

            val entryIterator = updates.entries.iterator()
            if (!entryIterator.hasNext()) {
                return visitor.visitInitialValue()
            }
            val (update, nextUpdates) = entryIterator.next().value
            result = leftMostFold(nextUpdates)
            result = visitor.visitUpdate(result, update)
            return notLeftMostFold(result, entryIterator)
        }

        private fun notLeftMostFold(
            accumulator: Result,
            iterator: Iterator<Map.Entry<Region<*>, Pair<UUpdateNode<Key, Sort>, RegionTree<UUpdateNode<Key, Sort>, *>>>>,
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