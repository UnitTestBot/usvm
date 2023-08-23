package org.usvm.memory

import io.ksmt.utils.asExpr
import kotlinx.collections.immutable.PersistentMap
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.uctx
import kotlin.collections.ArrayList

/**
 * A uniform unbounded slice of memory. Indexed by [Key], stores symbolic values.
 *
 * @property collectionId describes the source of the collection. Symbolic collections with the same [collectionId] represent the same
 * memory area, but in different states.
 */
data class USymbolicCollection<out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort>(
    val collectionId: CollectionId,
    val updates: USymbolicCollectionUpdates<Key, Sort>,
) : UMemoryRegion<Key, Sort> {
    // to save memory usage
    val sort: Sort get() = collectionId.sort

    private fun read(
        key: Key,
        updates: USymbolicCollectionUpdates<Key, Sort>
    ): UExpr<Sort> {
        val lastUpdatedElement = updates.lastUpdatedElementOrNull()

        if (lastUpdatedElement != null) {
            if (lastUpdatedElement.includesConcretely(key, precondition = sort.ctx.trueExpr)) {
                // The last write has overwritten the key
                return lastUpdatedElement.value(key)
            }
        }

        val localizedRegion = if (updates === this.updates) {
            this
        } else {
            this.copy(updates = updates)
        }

        return collectionId.instantiate(localizedRegion, key)
    }

    override fun read(key: Key): UExpr<Sort> {
        if (sort == sort.uctx.addressSort) {
            // Here we split concrete heap addresses from symbolic ones to optimize further memory operations.
            return splittingRead(key) { it is UConcreteHeapRef }
        }

        val updates = updates.read(key)
        return read(key, updates)
    }

    /**
     * Reads key from this symbolic collection, but 'bubbles up' entries satisfying predicates.
     * For example, imagine we read for example key z from array A with two updates: v written into x and w into y.
     * Usual [read] produces the expression
     *      A{x <- v}{y <- w}[z]
     * If v satisfies [predicate] and w does not, then [splittingRead] instead produces the expression
     *      ite(y != z /\ x = z, v, A{y <- w}[z]).
     * These two expressions are semantically equivalent, but the second one 'splits' v out of the rest
     * memory updates.
     */
    private fun splittingRead(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean
    ): UExpr<Sort> {
        val ctx = sort.ctx
        val guardBuilder = GuardBuilder(ctx.trueExpr)
        val matchingWrites = ArrayList<GuardedExpr<UExpr<Sort>>>() // works faster than linked list
        val splittingUpdates = split(key, predicate, matchingWrites, guardBuilder).updates

        val reading = read(key, splittingUpdates)

        // TODO: maybe introduce special expression for such operations?
        val readingWithBubbledWrites = matchingWrites.foldRight(reading) { (expr, guard), acc ->
            //                         foldRight here ^^^^^^^^^ is important
            ctx.mkIte(guard, expr, acc)
        }


        return readingWithBubbledWrites
    }

    override fun write(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): USymbolicCollection<CollectionId, Key, Sort> {
        assert(value.sort == sort)

        val newUpdates = if (sort == sort.uctx.addressSort) {
            // we must split symbolic and concrete heap refs here,
            // because later in [splittingRead] we check value is UConcreteHeapRef
            foldHeapRef(
                ref = value.asExpr(value.uctx.addressSort),
                initial = updates,
                initialGuard = guard,
                ignoreNullRefs = false,
                blockOnConcrete = { newUpdates, (valueRef, valueGuard) ->
                    newUpdates.write(key, valueRef.asExpr(sort), valueGuard)
                },
                blockOnSymbolic = { newUpdates, (valueRef, valueGuard) ->
                    newUpdates.write(key, valueRef.asExpr(sort), valueGuard)
                }
            )
        } else {
            updates.write(key, value, guard)
        }


        return this.copy(updates = newUpdates)
    }

    /**
     * Splits this [USymbolicCollection] on two parts:
     * * Values of [UUpdateNode]s satisfying [predicate] are added to the [matchingWrites].
     * * [UUpdateNode]s unsatisfying [predicate] remain in the result sumbolic collection.
     *
     * The [guardBuilder] is used to build guards for values added to [matchingWrites]. In the end, the [guardBuilder]
     * is updated and contains predicate indicating that the [key] can't be included in any of visited [UUpdateNode]s.
     *
     * @return new [USymbolicCollection] without writes satisfying [predicate] or this [USymbolicCollection] if no
     * matching writes were found.
     * @see [USymbolicCollectionUpdates.split], [splittingRead]
     */
    internal fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): USymbolicCollection<CollectionId, Key, Sort> {
        val splitUpdates = updates.read(key).split(key, predicate, matchingWrites, guardBuilder)

        return if (splitUpdates === updates) {
            this
        } else {
            this.copy(updates = splitUpdates)
        }
    }

    /**
     * Maps the collection using [composer].
     * It is used in [UComposer] for composition operation.
     * All updates which after mapping do not touch [targetCollectionId] will be thrown out.
     *
     * Note: after this operation a collection returned as a result might be in `broken` state:
     * it might [UIteExpr] with both symbolic and concrete references as keys in it.
     */
    fun <Type, MappedKey> mapTo(
        composer: UComposer<Type>,
        targetCollectionId: USymbolicCollectionId<MappedKey, Sort, *>
    ): USymbolicCollection<USymbolicCollectionId<MappedKey, Sort, *>, MappedKey, Sort> {
        val mapper = collectionId.keyFilterMapper(composer, targetCollectionId)
        // Map the updates and the collectionId
        val mappedUpdates = updates.filterMap(mapper, composer, targetCollectionId.keyInfo())

        if (mappedUpdates === updates && targetCollectionId === collectionId) {
            @Suppress("UNCHECKED_CAST")
            return this as USymbolicCollection<USymbolicCollectionId<MappedKey, Sort, *>, MappedKey, Sort>
        }

        return USymbolicCollection(targetCollectionId, mappedUpdates)
    }

    fun <Type> applyTo(memory: UWritableMemory<Type>) {
        // Apply each update on the copy
        updates.forEach {
            when (it) {
                is UPinpointUpdateNode<Key, Sort> -> collectionId.write(memory, it.key, it.value, it.guard)
                is URangedUpdateNode<*, *, Key, Sort> -> it.applyTo(memory, collectionId)
            }
        }
    }

    /**
     * @return Symbolic collection which obtained from this one by overwriting the range of addresses [[fromKey] : [toKey]]
     * with values from collection [fromCollection] read from range
     * of addresses [[keyConverter].convert([fromKey]) : [keyConverter].convert([toKey])]
     */
    fun <OtherCollectionId : USymbolicCollectionId<SrcKey, Sort, OtherCollectionId>, SrcKey> copyRange(
        fromCollection: USymbolicCollection<OtherCollectionId, SrcKey, Sort>,
        adapter: USymbolicCollectionAdapter<SrcKey, Key>,
        guard: UBoolExpr
    ): USymbolicCollection<CollectionId, Key, Sort> {
        val updatesCopy = updates.copyRange(fromCollection, adapter, guard)
        return this.copy(updates = updatesCopy)
    }

    override fun toString(): String =
        buildString {
            append('<')
            updates.forEach {
                append(it.toString())
            }
            append('>')
            append('@')
            append(collectionId)
        }
}

class GuardBuilder(nonMatchingUpdates: UBoolExpr) {
    var nonMatchingUpdatesGuard: UBoolExpr = nonMatchingUpdates
        private set

    operator fun plusAssign(guard: UBoolExpr) {
        nonMatchingUpdatesGuard = guarded(guard)
    }

    /**
     * @return [expr] guarded by this guard builder. Implementation uses [UContext.mkAnd] without flattening, because we accumulate
     * [nonMatchingUpdatesGuard] and otherwise it would take quadratic time.
     */
    fun guarded(expr: UBoolExpr): UBoolExpr = expr.ctx.mkAnd(nonMatchingUpdatesGuard, expr, flat = false)
}

inline fun <K, VSort : USort> PersistentMap<K, UExpr<VSort>>.guardedWrite(
    key: K,
    value: UExpr<VSort>,
    guard: UBoolExpr,
    defaultValue: () -> UExpr<VSort>
): PersistentMap<K, UExpr<VSort>> {
    val guardedValue = guard.uctx.mkIte(
        guard,
        { value },
        { get(key) ?: defaultValue() }
    )
    return put(key, guardedValue)
}
