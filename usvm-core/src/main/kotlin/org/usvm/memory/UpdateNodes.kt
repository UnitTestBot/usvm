package org.usvm.memory

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.compose
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.uctx

/**
 * Represents the result of memory write operation.
 *
 * Note that it should not be extended by external users. Otherwise, it would require
 * changes in processing of such nodes inside a core since we expect to have only two implementations:
 * [UPinpointUpdateNode] and [URangedUpdateNode].
 */
sealed interface UUpdateNode<Key, Sort : USort> {
    /**
     * @return will the [key] get overwritten by this write operation in *any* possible concrete state,
     * assuming [precondition] holds, or not.
     */
    fun includesConcretely(key: Key, precondition: UBoolExpr): Boolean

    /**
     * @return will this write get overwritten by [update] operation in *any* possible concrete state or not.
     */
    fun isIncludedByUpdateConcretely(update: UUpdateNode<Key, Sort>): Boolean

    /**
     * @return Symbolic condition expressing that the address [key] got overwritten by this memory write operation.
     * If [includesConcretely] returns true, then this method is obligated to return [UTrue].
     * Returned condition must imply [guard].
     */
    fun includesSymbolically(key: Key, composer: UComposer<*, *>?): UBoolExpr

    /**
     * Checks if the value in this [UUpdateNode] indexed by the [key] satisfies the [predicate]. If it does,
     * returns a new [UUpdateNode] without such writes or `null` if this [UUpdateNode] eliminates completely.
     *
     * In addition, if the value of this [UUpdateNode] satisfies the [predicate], it is added to the [matchingWrites]
     * with appropriate guard taken from the [guardBuilder]. In the end, the [guardBuilder] contains the condition that
     * [key] excluded from this [UUpdateNode].
     *
     * @return a new [UUpdateNode] without values satisfying [predicate] or `null` if this [UUpdateNode] eliminates
     * completely.
     */
    fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
        composer: UComposer<*, *>?,
    ): UUpdateNode<Key, Sort>?

    /**
     * @return Value which has been written into the address [key] during this memory write operation.
     */
    fun value(key: Key, composer: UComposer<*, *>?): UExpr<Sort>

    /**
     * @return This with [guard] = previous guard /\ condition.
     */
    fun addGuard(condition: UBoolExpr): UUpdateNode<Key, Sort>

    /**
     * Guard is a symbolic condition for this update. That is, this update is done only in states satisfying this guard.
     */
    val guard: UBoolExpr
}

/**
 * Represents a single write of [value] into a memory address [key]
 */
class UPinpointUpdateNode<Key, Sort : USort>(
    val key: Key,
    val keyInfo: USymbolicCollectionKeyInfo<Key, *>,
    internal val value: UExpr<Sort>,
    override val guard: UBoolExpr,
) : UUpdateNode<Key, Sort> {
    override fun includesConcretely(key: Key, precondition: UBoolExpr) =
        this.key == key && (guard == guard.ctx.trueExpr || guard == precondition)
    // in fact, we can check less strict formulae: `precondition -> guard`, but it is too complex to compute.

    override fun includesSymbolically(key: Key, composer: UComposer<*, *>?): UBoolExpr =
        guard.ctx.mkAnd(
            keyInfo.eqSymbolic(guard.uctx, keyInfo.mapKey(this.key, composer), key),
            composer.compose(guard)
        )

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<Key, Sort>,
    ): Boolean =
        update.includesConcretely(key, guard)

    override fun value(key: Key, composer: UComposer<*, *>?): UExpr<Sort> = composer.compose(value)

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
        composer: UComposer<*, *>?,
    ): UUpdateNode<Key, Sort>? {
        val ctx = value.ctx
        val nodeIncludesKey = includesSymbolically(key, composer) // includes guard
        val guard = guardBuilder.guarded(nodeIncludesKey)

        // We don't need to take nodes with unsatisfiable guard expression
        if (guard.isFalse) {
            return null
        }

        val transformedValue = composer.compose(value)
        val res = if (predicate(transformedValue)) {
            matchingWrites += transformedValue with guard
            null
        } else {
            this
        }

        val nodeExcludesKey = ctx.mkNot(nodeIncludesKey)
        guardBuilder += nodeExcludesKey

        return res
    }

    override fun addGuard(condition: UBoolExpr): UUpdateNode<Key, Sort> =
        UPinpointUpdateNode(key, keyInfo, value, condition.ctx.mkAnd(guard, condition))

    override fun toString(): String = "{$key <- $value}".takeIf { guard.isTrue } ?: "{$key <- $value | $guard}"
}

/**
 * Represents a synchronous overwriting the range of addresses [[fromKey] : [toKey]]
 * with values from symbolic collection [sourceCollection] read from range
 * of addresses [[adapter].convert([fromKey]) : [adapter].convert([toKey])]
 */
class URangedUpdateNode<CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey, DstKey, Sort : USort>(
    val sourceCollection: USymbolicCollection<CollectionId, SrcKey, Sort>,
    val adapter: USymbolicCollectionAdapter<SrcKey, DstKey>,
    override val guard: UBoolExpr,
) : UUpdateNode<DstKey, Sort> {

    override fun includesConcretely(
        key: DstKey,
        precondition: UBoolExpr,
    ): Boolean =
        adapter.includesConcretely(key) &&
            (guard == guard.ctx.trueExpr || precondition == guard) // TODO: some optimizations here?
    // in fact, we can check less strict formulae: precondition _implies_ guard, but this is too complex to compute.


    override fun includesSymbolically(key: DstKey, composer: UComposer<*, *>?): UBoolExpr =
        guard.ctx.mkAnd(adapter.includesSymbolically(key, composer), composer.compose(guard))

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, Sort>,
    ): Boolean = adapter.isIncludedByUpdateConcretely(update, guard)

    override fun value(key: DstKey, composer: UComposer<*, *>?): UExpr<Sort> =
        sourceCollection.read(adapter.convert(key, composer), composer)

    override fun split(
        key: DstKey,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
        composer: UComposer<*, *>?,
    ): UUpdateNode<DstKey, Sort>? {
        val ctx = guardBuilder.nonMatchingUpdatesGuard.ctx
        val nodeIncludesKey = includesSymbolically(key, composer) // contains guard
        val nextGuard = guardBuilder.guarded(nodeIncludesKey)

        // We don't need to take nodes with unsatisfiable guard expression
        if (nextGuard.isFalse) {
            return null
        }

        val nextGuardBuilder = GuardBuilder(nextGuard)

        /**
         * Here's the explanation of the [split] function. Consider these symbolic collections and updates:
         *
         * ```
         *                  [this]   [UPinpointUpdateNode]
         *                     |            |
         *                     |            |
         *                    \/           \/
         * reg0:           { 1..5 } -> { k1, 0x1 } -> mkArrayConst(nullRef)
         *                     |
         *                     | copy from [this.region]{ 1..5 }
         *                     | [this.adapter.convert] = id
         *                     |
         * [this.region]: { k2, 0x3 } -> mkArrayConst(nullRef)
         * ```
         *
         * The [nextGuardBuilder] must imply that the [key] lies in { 1..5 } and it's satisfied by [nodeIncludesKey].
         * The result [matchingWrites] will contain { 0x3 } with the guard [key] == k2 && k in { 1..5 }.
         * Also, the result [matchingWrites] must contain { 0x1 }, but this must be guarded: [key] !in { 1..5 } which
         * is implied from [nodeExcludesKey].
         *
         * Due to the [GuardBuilder] mutability, we have to create a new guard to pass into the [sourceCollection.split] function,
         * it's a [nextGuardBuilder].
         */
        val splitCollection =
            sourceCollection.split(adapter.convert(key, composer), predicate, matchingWrites, nextGuardBuilder, composer) // ???

        val resultUpdateNode = if (splitCollection === sourceCollection) {
            this
        } else {
            URangedUpdateNode(splitCollection, adapter, composer.compose(guard))
        }

        val nodeExcludesKey = ctx.mkNot(nodeIncludesKey)
        guardBuilder += nodeExcludesKey

        return resultUpdateNode
    }

    /**
     * Applies this update node to the [memory] with applying composition via [composer].
     */
    fun applyTo(
        memory: UWritableMemory<*>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        key: DstKey,
        composer: UComposer<*, *>,
    ) {
        val convertedKey = adapter.convert(key, composer)
        sourceCollection.applyTo(memory, convertedKey, composer)
        adapter.applyTo(
            memory,
            sourceCollection.collectionId,
            dstCollectionId,
            composer.compose(guard),
            convertedKey,
            composer
        )
    }

    override fun addGuard(condition: UBoolExpr): UUpdateNode<DstKey, Sort> =
        URangedUpdateNode(sourceCollection, adapter, condition.ctx.mkAnd(guard, condition))

    override fun toString(): String =
        "{${adapter.toString(sourceCollection)}${if (guard.isTrue) "" else " | $guard"}}"
}
