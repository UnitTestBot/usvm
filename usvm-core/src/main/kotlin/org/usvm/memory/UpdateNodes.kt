package org.usvm.memory

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.compose
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
    fun includesSymbolically(key: Key, composer: UComposer<*>?): UBoolExpr

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
        composer: UComposer<*>?,
    ): UUpdateNode<Key, Sort>?

    /**
     * @return Value which has been written into the address [key] during this memory write operation.
     */
    fun value(key: Key, composer: UComposer<*>?): UExpr<Sort>

    /**
     * Guard is a symbolic condition for this update. That is, this update is done only in states satisfying this guard.
     */
    val guard: UBoolExpr

    /**
     * Returns a mapped update node using [keyMapper] and [composer].
     * It is used in [UComposer] for composition.
     * For some key, [keyMapper] might return null. Then, this function returns null as well.
     */
//    fun <Type, MappedKey> map(
//        keyMapper: KeyMapper<Key, MappedKey>,
//        composer: UComposer<Type>,
//        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedKey, *>
//    ): UUpdateNode<MappedKey, Sort>?
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

    override fun includesSymbolically(key: Key, composer: UComposer<*>?): UBoolExpr =
        guard.ctx.mkAnd(
            keyInfo.eqSymbolic(guard.uctx, keyInfo.mapKey(key, composer), key),
            composer.compose(guard)
        )

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<Key, Sort>,
    ): Boolean =
        update.includesConcretely(key, guard)

    override fun value(key: Key, composer: UComposer<*>?): UExpr<Sort> = composer.compose(value)

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
        composer: UComposer<*>?,
    ): UUpdateNode<Key, Sort>? {
        val ctx = value.ctx
        val nodeIncludesKey = includesSymbolically(key, composer) // includes guard
        val nodeExcludesKey = ctx.mkNot(nodeIncludesKey)
        val guard = guardBuilder.guarded(nodeIncludesKey)

        val transformedValue = composer.compose(value)
        val res = if (predicate(transformedValue)) {
            matchingWrites += transformedValue with guard
            null
        } else {
            this
        }

        guardBuilder += nodeExcludesKey

        return res
    }

//    override fun <Type, MappedKey> map(
//        keyMapper: KeyMapper<Key, MappedKey>,
//        composer: UComposer<Type>,
//        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedKey, *>
//    ): UPinpointUpdateNode<MappedKey, Sort>? {
//        val mappedKey = keyMapper(key) ?: return null
//        val mappedValue = composer.compose(value)
//        val mappedGuard = composer.compose(guard)
//
//        // If nothing changed, return this value
//        if (mappedKey === key && mappedValue === value && mappedGuard === guard) {
//            @Suppress("UNCHECKED_CAST")
//            return this as UPinpointUpdateNode<MappedKey, Sort>
//        }
//
//        // Otherwise, construct a new one update node
//        return UPinpointUpdateNode(mappedKey, mappedKeyInfo, mappedValue, mappedGuard)
//    }

    override fun equals(other: Any?): Boolean =
        other is UPinpointUpdateNode<*, *> && this.key == other.key && this.guard == other.guard

    override fun hashCode(): Int = key.hashCode() * 31 + guard.hashCode() // Ignores value

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


    override fun includesSymbolically(key: DstKey, composer: UComposer<*>?): UBoolExpr =
        guard.ctx.mkAnd(adapter.includesSymbolically(key, composer), composer.compose(guard))

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, Sort>,
    ): Boolean = adapter.isIncludedByUpdateConcretely(update, guard)

    override fun value(key: DstKey, composer: UComposer<*>?): UExpr<Sort> =
        sourceCollection.read(adapter.convert(key, composer), composer)

    override fun split(
        key: DstKey,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
        composer: UComposer<*>?,
    ): UUpdateNode<DstKey, Sort> {
        val ctx = guardBuilder.nonMatchingUpdatesGuard.ctx
        val nodeIncludesKey = includesSymbolically(key, composer) // contains guard
        val nodeExcludesKey = ctx.mkNot(nodeIncludesKey)
        val nextGuard = guardBuilder.guarded(nodeIncludesKey)
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

        guardBuilder += nodeExcludesKey

        return resultUpdateNode
    }


//    @Suppress("UNCHECKED_CAST")
//    override fun <Type, MappedDstKey> map(
//        keyMapper: KeyMapper<DstKey, MappedDstKey>,
//        composer: UComposer<Type>,
//        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
//    ): URangedUpdateNode<*, *, MappedDstKey, Sort>? {
//        val mappedCollectionId = sourceCollection.collectionId.map(composer)
//        val (mappedAdapter, targetCollectionId) = adapter.map(keyMapper, composer, mappedCollectionId, mappedKeyInfo)
//            ?: return null
//        val mappedGuard = composer.compose(guard)
//
//        val mappedCollection = sourceCollection.mapTo(composer, targetCollectionId)
//
//        // If nothing changed, return this
//        if (mappedCollection === sourceCollection
//            && mappedAdapter === adapter
//            && mappedGuard === guard
//        ) {
//            return this as URangedUpdateNode<*, *, MappedDstKey, Sort>
//        }
//
//        // Otherwise, construct a new one updated node
//        return URangedUpdateNode(
//            // Type variables in this cast are incorrect, but who cares...
//            mappedCollection as USymbolicCollection<CollectionId, SrcKey, Sort>,
//            mappedAdapter as USymbolicCollectionAdapter<SrcKey, MappedDstKey>,
//            mappedGuard
//        )
//    }

    // Ignores update
    override fun equals(other: Any?): Boolean =
        other is URangedUpdateNode<*, *, *, *> &&
            this.adapter == other.adapter &&
            this.guard == other.guard

    // Ignores update
    override fun hashCode(): Int = adapter.hashCode() * 31 + guard.hashCode()

    fun applyTo(
        memory: UWritableMemory<*>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        composer: UComposer<*>,
    ) {
        sourceCollection.applyTo(memory, composer)
        adapter.applyTo(memory, sourceCollection.collectionId, dstCollectionId, composer.compose(guard), composer)
    }

    override fun toString(): String =
        "{${adapter.toString(sourceCollection)}${if (guard.isTrue) "" else " | $guard"}}"
}
