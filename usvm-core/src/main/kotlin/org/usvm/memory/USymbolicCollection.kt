package org.usvm.memory

import io.ksmt.utils.asExpr
import kotlinx.collections.immutable.PersistentMap
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.compose
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.uctx

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
        updates: USymbolicCollectionUpdates<Key, Sort>,
        composer: UComposer<*>?
    ): UExpr<Sort> {
        val lastUpdatedElement = updates.lastUpdatedElementOrNull()

        if (lastUpdatedElement != null) {
            if (lastUpdatedElement.includesSymbolically(key, composer).isTrue) {
                // The last write has overwritten the key
                return lastUpdatedElement.value(key, composer)
            }
        }

        val localizedRegion = if (updates === this.updates) {
            this
        } else {
            this.copy(updates = updates)
        }

        return collectionId.instantiate(localizedRegion, key, composer)
    }

    fun read(key: Key, composer: UComposer<*>?): UExpr<Sort> {
        if (sort == sort.uctx.addressSort) {
            // Here we split concrete heap addresses from symbolic ones to optimize further memory operations.
            return splittingRead(key, composer) { it is UConcreteHeapRef }
        }

        val updates = updates.read(key, composer)
        return read(key, updates, composer)
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
        composer: UComposer<*>?,
        predicate: (UExpr<Sort>) -> Boolean
    ): UExpr<Sort> {
        val ctx = sort.ctx
        val guardBuilder = GuardBuilder(ctx.trueExpr)
        val matchingWrites = ArrayList<GuardedExpr<UExpr<Sort>>>() // works faster than linked list
        val splittingUpdates = split(key, predicate, matchingWrites, guardBuilder, composer).updates

        val reading = read(key, splittingUpdates, composer)

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
        composer: UComposer<*>?,
    ): USymbolicCollection<CollectionId, Key, Sort> {
        val splitUpdates = updates.read(key, composer).split(key, predicate, matchingWrites, guardBuilder, composer)

        return if (splitUpdates === updates) {
            this
        } else {
            this.copy(updates = splitUpdates)
        }
    }

    fun <Type> applyTo(memory: UWritableMemory<Type>, composer: UComposer<*>) {
        // Apply each update on the copy
        for (update in updates) {
            val guard = composer.compose(update.guard)

            if (guard.isFalse) {
                continue
            }

            when (update) {
                is UPinpointUpdateNode<Key, Sort> -> collectionId.write(
                    memory,
                    update.keyInfo.mapKey(update.key, composer),
                    composer.compose(update.value),
                    guard
                )

                is URangedUpdateNode<*, *, Key, Sort> -> update.applyTo(memory, collectionId, composer)
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

    override fun read(key: Key): UExpr<Sort> = read(key, composer = null)

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
