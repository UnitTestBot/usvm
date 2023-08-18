package org.usvm.memory

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.memory.collections.GuardBuilder
import org.usvm.memory.collections.KeyMapper
import org.usvm.memory.collections.UAllocatedArrayId
import org.usvm.memory.collections.USymbolicArrayId
import org.usvm.memory.collections.USymbolicArrayIndex
import org.usvm.memory.collections.USymbolicCollection
import org.usvm.memory.collections.USymbolicCollectionId
import org.usvm.memory.collections.USymbolicCollectionKeyInfo
import org.usvm.memory.collections.USymbolicMapId
import org.usvm.memory.collections.USymbolicSetId
import org.usvm.uctx
import org.usvm.util.Region
import java.util.*


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
    fun includesSymbolically(key: Key): UBoolExpr

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
    ): UUpdateNode<Key, Sort>?

    /**
     * @return Value which has been written into the address [key] during this memory write operation.
     */
    fun value(key: Key): UExpr<Sort>

    /**
     * Guard is a symbolic condition for this update. That is, this update is done only in states satisfying this guard.
     */
    val guard: UBoolExpr

    /**
     * Returns a mapped update node using [keyMapper] and [composer].
     * It is used in [UComposer] for composition.
     * For some key, [keyMapper] might return null. Then, this function returns null as well.
     */
    fun <Type, MappedKey> map(
        keyMapper: KeyMapper<Key, MappedKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedKey, *>
    ): UUpdateNode<MappedKey, Sort>?
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

    override fun includesSymbolically(key: Key): UBoolExpr =
        guard.ctx.mkAnd(keyInfo.eqSymbolic(this.key, key), guard)

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<Key, Sort>,
    ): Boolean =
        update.includesConcretely(key, guard)

    override fun value(key: Key): UExpr<Sort> = this.value

    override fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): UUpdateNode<Key, Sort>? {
        val ctx = value.ctx
        val nodeIncludesKey = includesSymbolically(key) // includes guard
        val nodeExcludesKey = ctx.mkNot(nodeIncludesKey)
        val guard = guardBuilder.guarded(nodeIncludesKey)

        val res = if (predicate(value)) {
            matchingWrites += value with guard
            null
        } else {
            this
        }

        guardBuilder += nodeExcludesKey

        return res
    }

    override fun <Type, MappedKey> map(
        keyMapper: KeyMapper<Key, MappedKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedKey, *>
    ): UPinpointUpdateNode<MappedKey, Sort>? {
        val mappedKey = keyMapper(key) ?: return null
        val mappedValue = composer.compose(value)
        val mappedGuard = composer.compose(guard)

        // If nothing changed, return this value
        if (mappedKey === key && mappedValue === value && mappedGuard === guard) {
            @Suppress("UNCHECKED_CAST")
            return this as UPinpointUpdateNode<MappedKey, Sort>
        }

        // Otherwise, construct a new one update node
        return UPinpointUpdateNode(mappedKey, mappedKeyInfo, mappedValue, mappedGuard)
    }

    override fun equals(other: Any?): Boolean =
        other is UPinpointUpdateNode<*, *> && this.key == other.key && this.guard == other.guard

    override fun hashCode(): Int = key.hashCode() * 31 + guard.hashCode() // Ignores value

    override fun toString(): String = "{$key <- $value}".takeIf { guard.isTrue } ?: "{$key <- $value | $guard}"
}

/**
 * Redirects reads from one collection into another. Used in [URangedUpdateNode].
 */
sealed interface USymbolicCollectionAdapter<SrcKey, DstKey> {
    /**
     * Converts destination memory key into source memory key
     */
    fun convert(key: DstKey): SrcKey

    /**
     * Key that defines adapted collection id (used to find  id after mapping).
     */
    val srcKey: SrcKey

    /**
     * Returns region covered by the adapted collection.
     */
    fun <Reg : Region<Reg>> region(): Reg

    fun includesConcretely(key: DstKey): Boolean

    fun includesSymbolically(key: DstKey): UBoolExpr

    fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ): Boolean

    /**
     * Maps this adapter by substituting all symbolic values using composer.
     * The type of adapter might change in both [SrcKey] and [DstKey].
     * Type of [SrcKey] changes if [collectionId] rebinds [srcKey].
     * Type of [DstKey] changes to [MappedDstKey] by [dstKeyMapper].
     * @return
     *  - Null if destination keys are filtered out by [dstKeyMapper]
     *  - Pair(adapter, targetId), where adapter is a mapped version of this one, targetId is a
     *    new collection id for the mapped source collection we adapt.
     */
    fun <Type, MappedDstKey, Sort : USort> map(
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        collectionId: USymbolicCollectionId<SrcKey, Sort, *>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
        val mappedSrcKey = collectionId.keyMapper(composer)(srcKey)
        val decomposedSrcKey = collectionId.rebindKey(mappedSrcKey)
        if (decomposedSrcKey != null) {
            val mappedAdapter =
                mapDstKeys(decomposedSrcKey.key, decomposedSrcKey.collectionId, dstKeyMapper, composer, mappedKeyInfo)
                    ?: return null
            return mappedAdapter to decomposedSrcKey.collectionId
        }

        val mappedAdapter = mapDstKeys(mappedSrcKey, collectionId, dstKeyMapper, composer, mappedKeyInfo) ?: return null
        return mappedAdapter to collectionId
    }

    /**
     * Returns new adapter with destination keys were successfully mapped by [dstKeyMapper].
     * If [dstKeyMapper] returns null for at least one key, returns null.
     */
    fun <Type, MappedSrcKey, MappedDstKey> mapDstKeys(
        mappedSrcKey: MappedSrcKey,
        srcCollectionId: USymbolicCollectionId<*, *, *>,
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>?

    fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr
    )

    fun toString(collection: USymbolicCollection<*, SrcKey, *>): String
}
//
//class USymbolicCollectionUpdate<
//        SrcKey, DstKey, Sort : USort,
//        Collection : USymbolicCollection<*, SrcKey, Sort>,
//        Adapter : USymbolicCollectionAdapter<SrcKey, DstKey, Adapter>
//        > : UUpdateNode<DstKey, Sort> {
//    val keyConverter: KeyConverter
//    val sourceCollection: Collection
//
//    override fun value(key: DstKey): UExpr<Sort> = sourceCollection.read(keyConverter.convert(key))
//
//    fun changeCollection(newCollection: Collection): UUpdateNode<DstKey, Sort> =
//
//}

/**
 * Represents a synchronous overwriting the range of addresses [[fromKey] : [toKey]]
 * with values from symbolic collection [sourceCollection] read from range
 * of addresses [[keyConverter].convert([fromKey]) : [keyConverter].convert([toKey])]
 */
class URangedUpdateNode<CollectionId : USymbolicCollectionId<SrcKey, Sort, CollectionId>, SrcKey, DstKey, Sort : USort>(
//    val fromKey: DstKey,
//    val toKey: DstKey,
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


    override fun includesSymbolically(key: DstKey): UBoolExpr =
        guard.ctx.mkAnd(adapter.includesSymbolically(key), guard)

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, Sort>,
    ): Boolean =
        adapter.isIncludedByUpdateConcretely(update, guard)

    override fun value(key: DstKey): UExpr<Sort> = sourceCollection.read(adapter.convert(key))

    override fun split(
        key: DstKey,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): UUpdateNode<DstKey, Sort> {
        val ctx = guardBuilder.nonMatchingUpdatesGuard.ctx
        val nodeIncludesKey = includesSymbolically(key) // contains guard
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
         *                     | [this.keyConverter] = id
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
            sourceCollection.split(adapter.convert(key), predicate, matchingWrites, nextGuardBuilder) // ???

        val resultUpdateNode = if (splitCollection === sourceCollection) {
            this
        } else {
            changeCollection(splitCollection)
        }

        guardBuilder += nodeExcludesKey

        return resultUpdateNode
    }


    @Suppress("UNCHECKED_CAST")
    override fun <Type, MappedDstKey> map(
        keyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): URangedUpdateNode<*, *, MappedDstKey, Sort>? {
        val mappedCollectionId = sourceCollection.collectionId.map(composer)
        val (mappedAdapter, targetCollectionId) = adapter.map(keyMapper, composer, mappedCollectionId, mappedKeyInfo)
            ?: return null
        val mappedGuard = composer.compose(guard)

        val mappedCollection = sourceCollection.mapTo(composer, targetCollectionId)

        // If nothing changed, return this
        if (mappedCollection === sourceCollection
            && mappedAdapter === adapter
            && mappedGuard === guard
        ) {
            return this as URangedUpdateNode<*, *, MappedDstKey, Sort>
        }

        // Otherwise, construct a new one updated node
        return URangedUpdateNode(
            // Type variables in this cast are incorrect, but who cares...
            mappedCollection as USymbolicCollection<CollectionId, SrcKey, Sort>,
            mappedAdapter as USymbolicCollectionAdapter<SrcKey, MappedDstKey>,
            mappedGuard
        )
    }

    fun changeCollection(newCollection: USymbolicCollection<CollectionId, SrcKey, Sort>) =
        URangedUpdateNode(newCollection, adapter, guard)


    // Ignores update
    override fun equals(other: Any?): Boolean =
        other is URangedUpdateNode<*, *, *, *> &&
                this.adapter == other.adapter &&
                this.guard == other.guard

    // Ignores update
    override fun hashCode(): Int = adapter.hashCode() * 31 + guard.hashCode()

    fun applyTo(memory: UWritableMemory<*>, dstCollectionId: USymbolicCollectionId<DstKey, *, *>) {
        sourceCollection.applyTo(memory)
        adapter.applyTo(memory, sourceCollection.collectionId, dstCollectionId, guard)
    }

    override fun toString(): String =
        "{${adapter.toString(sourceCollection)}${if (guard.isTrue) "" else " | $guard"}}"
}

//class UMergeUpdateNode<
//        CollectionId : USymbolicMapId<SrcKey, KeySort, Reg, ValueSort, CollectionId>,
//        SrcKey,
//        DstKey,
//        KeySort : USort,
//        Reg : Region<Reg>,
//        ValueSort : USort>(
//    override val sourceCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>,
//    val keyIncludesCheck: UMergeKeyIncludesCheck<SrcKey, KeySort, *>,
//    override val keyConverter: UMergeKeyConverter<SrcKey, DstKey>,
//    override val guard: UBoolExpr
//) : USymbolicCollectionUpdate<SrcKey, DstKey, ValueSort,
//        USymbolicCollection<CollectionId, SrcKey, ValueSort>,
//        UMergeKeyConverter<SrcKey, DstKey>> {
//
//    override fun includesConcretely(key: DstKey, precondition: UBoolExpr): Boolean {
//        val srcKey = keyConverter.convert(key)
//        val keyIncludes = keyIncludesCheck.check(srcKey)
//        return (keyIncludes === keyIncludes.ctx.trueExpr) && (guard == guard.ctx.trueExpr || precondition == guard)
//    }
//
//    override fun isIncludedByUpdateConcretely(update: UUpdateNode<DstKey, ValueSort>): Boolean = false
//
//    override fun includesSymbolically(key: DstKey): UBoolExpr {
//        val srcKey = keyConverter.convert(key)
//        val keyIncludes = keyIncludesCheck.check(srcKey)
//        return keyIncludes.ctx.mkAnd(keyIncludes, guard)
//    }
//
//    override fun changeCollection(newCollection: USymbolicCollection<CollectionId, SrcKey, ValueSort>) =
//        UMergeUpdateNode(newCollection, keyIncludesCheck, keyConverter, guard)
//
//    override fun <Field, Type> map(
//        keyTransformer: KeyTransformer<DstKey>,
//        composer: UComposer<Field, Type>
//    ): UUpdateNode<DstKey, ValueSort> {
//        val mappedCollection = sourceCollection.map(composer)
//        val mappedKeyConverter = keyConverter.map(composer)
//        val mappedIncludesCheck = keyIncludesCheck.map(composer)
//        val mappedGuard = composer.compose(guard)
//
//        if (mappedCollection === sourceCollection
//            && mappedKeyConverter === keyConverter
//            && mappedIncludesCheck === keyIncludesCheck
//            && mappedGuard == guard
//        ) {
//            return this
//        }
//
//        return UMergeUpdateNode(mappedCollection, mappedIncludesCheck, mappedKeyConverter, mappedGuard)
//    }
//
//    override fun toString(): String = "(merge $sourceCollection)"
//}

/**
 * Composable converter of symbolic collection keys. Helps to transparently copy content of various collections
 * each into other without eager address conversion.
 * For instance, when we copy array slice [i : i + len] to destination memory slice [j : j + len],
 * we emulate it by memorizing the source memory updates as-is, but read the destination memory by
 * 'redirecting' the index k to k + j - i of the source memory.
 * This conversion is done by [convert].
 * Do not be confused: it converts [DstKey] to [SrcKey] (not vice-versa), as we use it when we
 * read from destination buffer index to source memory.
 */
class USymbolicArrayAdapter<SrcKey, DstKey>(
    private val srcFrom: SrcKey,
    val dstFrom: DstKey,
    val dstTo: DstKey,
    private val keyInfo: USymbolicCollectionKeyInfo<DstKey, *>
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    override val srcKey = srcFrom

    override fun <Reg : Region<Reg>> region(): Reg =
        keyInfo.keyRangeRegion(dstFrom, dstTo).uncheckedCast()

    @Suppress("UNCHECKED_CAST")
    private fun <Key> extractArrayIndex(value: Key): USizeExpr = when (value) {
        is Pair<*, *> -> value.second as USizeExpr  // we deal with symbolic array index here
        is UExpr<*> -> value as USizeExpr           // we deal with size expression here
        else -> error("Unexpected value passed as array index")
    }

    /**
     * Converts source memory key into destination memory key
     */
    @Suppress("UNCHECKED_CAST")
    override fun convert(key: DstKey): SrcKey =
        when (srcFrom) {
            is Pair<*, *> -> (srcFrom.first to convertIndex(srcFrom.second as USizeExpr)) as SrcKey
            is UExpr<*> -> convertIndex(srcFrom as USizeExpr) as SrcKey
            else -> error("Unexpected value passed as array index")
        }

    protected fun convertIndex(idx: USizeExpr): USizeExpr = with(idx.ctx) {
        mkBvSubExpr(mkBvAddExpr(idx, extractArrayIndex(dstFrom)), extractArrayIndex(srcFrom))
    }

//    abstract fun clone(
//        srcSymbolicArrayIndex: USymbolicArrayIndex,
//        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
//        dstToIndex: USizeExpr
//    ): USymbolicArrayAdapter<SrcKey, DstKey>

    override fun includesConcretely(key: DstKey): Boolean =
        keyInfo.cmpConcrete(dstFrom, key) && keyInfo.cmpConcrete(key, dstTo)

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val leftIsLefter = keyInfo.cmpSymbolic(dstFrom, key)
        val rightIsRighter = keyInfo.cmpSymbolic(key, dstTo)
        val ctx = leftIsLefter.ctx

        return ctx.mkAnd(leftIsLefter, rightIsRighter)
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ): Boolean =
        update.includesConcretely(dstFrom, guard) && update.includesConcretely(dstTo, guard)

    override fun <Type, MappedSrcKey, MappedDstKey> mapDstKeys(
        mappedSrcKey: MappedSrcKey,
        srcCollectionId: USymbolicCollectionId<*, *, *>,
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>? {
        val mappedDstFrom = dstKeyMapper(dstFrom) ?: return null
        val mappedDstTo = dstKeyMapper(dstTo) ?: return null

        if (srcKey === mappedSrcKey && dstFrom === mappedDstFrom && dstTo === mappedDstTo) {
            @Suppress("UNCHECKED_CAST")
            // In this case [MappedSrcKey] == [SrcKey] and [MappedDstKey] == [DstKey],
            // but type system cannot type check that.
            return this as USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>
        }

        return USymbolicArrayAdapter(mappedSrcKey, mappedDstFrom, mappedDstTo, mappedKeyInfo)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr
    ) {
        require(dstCollectionId is USymbolicArrayId<*, *, *, *>)
        val (srcRef: UHeapRef, srcIdx: USizeExpr) =
            when (srcFrom) {
                is Pair<*, *> -> srcFrom as USymbolicArrayIndex
                is UExpr<*> -> srcFrom.uctx.mkConcreteHeapRef((srcCollectionId as UAllocatedArrayId<*, *>).address) to (srcFrom as USizeExpr)
                else -> error("Unexpected value passed as array index")
            }
        val (dstRef: UHeapRef, dstFromIdx: USizeExpr, dstToIdx: USizeExpr) =
            when (dstFrom) {
                is Pair<*, *> -> Triple(dstFrom.first as UHeapRef, dstFrom.second as USizeExpr, (dstTo as Pair<*, *>).second as USizeExpr)
                is UExpr<*> -> Triple(dstFrom.uctx.mkConcreteHeapRef((dstCollectionId as UAllocatedArrayId<*, *>).address), dstFrom as USizeExpr, dstTo as USizeExpr)
                else -> error("Unexpected value passed as array index")
            }
        memory.memcpy(srcRef, dstRef, dstCollectionId.arrayType, dstCollectionId.sort, srcIdx, dstFromIdx, dstToIdx, guard)
    }

    //    override fun <Field, Type> map(composer: UComposer<Field, Type>): USymbolicArrayAdapter<SrcKey, DstKey> {
//        val (srcRef, srcIdx) = srcSymbolicArrayIndex
//        val (dstRef, dstIdx) = dstFromSymbolicArrayIndex
//
//        val newSrcHeapAddr = composer.compose(srcRef)
//        val newSrcArrayIndex = composer.compose(srcIdx)
//        val newDstHeapAddress = composer.compose(dstRef)
//        val newDstFromIndex = composer.compose(dstIdx)
//        val newDstToIndex = composer.compose(dstToIndex)
//
//        if (newSrcHeapAddr === srcRef &&
//            newSrcArrayIndex === srcIdx &&
//            newDstHeapAddress === dstRef &&
//            newDstFromIndex === dstIdx &&
//            newDstToIndex === dstToIndex
//        ) {
//            return this
//        }
//
//        return clone(
//            srcSymbolicArrayIndex = newSrcHeapAddr to newSrcArrayIndex,
//            dstFromSymbolicArrayIndex = newDstHeapAddress to newDstFromIndex,
//            dstToIndex = newDstToIndex
//        )
//    }

    private fun <Key> keyToString(key: Key) = when (key) {
        is Pair<*, *> -> "${key.first}.${key.second}"
        else -> key.toString()
    }

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String {
        return "[${keyToString(dstFrom)}..${keyToString(dstTo)}] <- $collection[${convert(dstFrom)}..${convert(dstTo)}]"
    }
}

///**
// * Used when copying data from allocated array to another allocated array.
// */
//class UAllocatedToAllocatedArrayAdapter(
//    private val srcFromIndex: USizeExpr,
//    private val dstFromIndex: USizeExpr,
//    private val dstToIndex: USizeExpr
//) : USymbolicArrayAdapter<USizeExpr, USizeExpr>(/*srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex*/) {
//    override fun convert(key: USizeExpr): USizeExpr = convertIndex(key, srcFromIndex, dstFromIndex)
//    override val fromKey: USizeExpr = dstFromIndex
//    override val toKey: USizeExpr = dstToIndex
//    override val srcKey: USizeExpr = srcFromIndex
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UAllocatedToAllocatedArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort : USort> map(
//        dstKeyMapper: KeyMapper<USizeExpr, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USizeExpr, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromIndex = dstKeyMapper(dstFromIndex) ?: return null
//        val mappedDstToIndex = dstKeyMapper(dstToIndex) ?: return null
//        val mappedSrcFromIndex = composer.compose(srcFromIndex)
//        // collectionId is already an allocated one, so no need to rebind it...
//
//        if (srcFromIndex == mappedSrcFromIndex && dstFromIndex == mappedDstFromIndex && dstToIndex == mappedDstToIndex) {
//            return (this as USymbolicCollectionAdapter<USizeExpr, MappedDstKey>) to collectionId
//        }
//
//        return UAllocatedToAllocatedArrayAdapter(
//            mappedSrcFromIndex,
//            mappedDstFromIndex as USizeExpr,
//            mappedDstToIndex as USizeExpr
//        ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//    }
//}
//
///**
// * Used when copying data from allocated array to input one.
// */
//class UAllocatedToInputArrayAdapter(
//    private val srcFromIndex: USizeExpr,
//    private val dstFromSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstToSymbolicArrayIndex: USymbolicArrayIndex
//) : USymbolicArrayAdapter<USizeExpr, USymbolicArrayIndex>(
////    srcSymbolicArrayIndex,
////    dstFromSymbolicArrayIndex,
////    dstToIndex
//) {
//    init {
//        require(dstFromSymbolicArrayIndex.first == dstToSymbolicArrayIndex.first)
//    }
//
//    override fun convert(key: USymbolicArrayIndex): USizeExpr =
//        convertIndex(key.second, srcFromIndex, dstFromSymbolicArrayIndex.second)
//
//    override val fromKey: USymbolicArrayIndex = dstFromSymbolicArrayIndex
//    override val toKey: USymbolicArrayIndex = dstToSymbolicArrayIndex
//    override val srcKey: USizeExpr = srcFromIndex
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UAllocatedToInputArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort: USort> map(
//        dstKeyMapper: KeyMapper<USymbolicArrayIndex, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USizeExpr, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromSymbolicArrayIndex = dstKeyMapper(dstFromSymbolicArrayIndex) ?: return null
//        val mappedDstToSymbolicArrayIndex = dstKeyMapper(dstToSymbolicArrayIndex) ?: return null
//        val mappedSrcFromIndex = composer.compose(srcFromIndex)
//        // collectionId is already an allocated one, so no need to rebind it...
//
//        if (srcFromIndex == mappedSrcFromIndex &&
//            dstFromSymbolicArrayIndex === mappedDstFromSymbolicArrayIndex &&
//            dstToSymbolicArrayIndex === mappedDstToSymbolicArrayIndex
//        ) {
//            return this as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//        }
//
//        val mappedAdapter = when (mappedDstFromSymbolicArrayIndex) {
//            is Pair<*, *> ->
//                UAllocatedToInputArrayAdapter(
//                    mappedSrcFromIndex,
//                    mappedDstFromSymbolicArrayIndex as USymbolicArrayIndex,
//                    mappedDstToSymbolicArrayIndex as USymbolicArrayIndex
//                ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey>
//
//            else ->
//                UAllocatedToAllocatedArrayAdapter(
//                    mappedSrcFromIndex,
//                    mappedDstFromSymbolicArrayIndex as USizeExpr,
//                    mappedDstToSymbolicArrayIndex as USizeExpr
//                ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey>
//        }
//
//        return mappedAdapter to collectionId
//    }
//}
//
///**
// * Used when copying data from input array to allocated one.
// */
//class UInputToAllocatedArrayAdapter(
//    private val srcSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstFromIndex: USizeExpr,
//    private val dstToIndex: USizeExpr
//) : USymbolicArrayAdapter<USymbolicArrayIndex, USizeExpr>(
////    srcSymbolicArrayIndex,
////    dstFromSymbolicArrayIndex,
////    dstToIndex
//) {
//    override fun convert(key: USizeExpr): USymbolicArrayIndex =
//        srcSymbolicArrayIndex.first to convertIndex(key, srcSymbolicArrayIndex.second, dstFromIndex)
//
//    override val fromKey: USizeExpr = dstFromIndex
//    override val toKey: USizeExpr = dstToIndex
//    override val srcKey: USymbolicArrayIndex = srcSymbolicArrayIndex
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort : USort> map(
//        dstKeyMapper: KeyMapper<USizeExpr, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USymbolicArrayIndex, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromIndex = dstKeyMapper(dstFromIndex) ?: return null
//        val mappedDstToIndex = dstKeyMapper(dstToIndex) ?: return null
//        val mappedSrcSymbolicArrayIndex = collectionId.keyMapper(composer)(srcSymbolicArrayIndex)
//
//        val decomposedSrcKey = collectionId.rebindKey(mappedSrcSymbolicArrayIndex)
//        if (decomposedSrcKey != null) {
//            // In this case, source collection id has been changed. Heuristically, it can change
//            // only to allocated array id, validating it explicitly...
//            require(collectionId is UAllocatedArrayId<*, Sort>)
//            return (UAllocatedToAllocatedArrayAdapter(
//                decomposedSrcKey.key as USizeExpr,
//                mappedDstFromIndex as USizeExpr,
//                mappedDstToIndex as USizeExpr
//            ) as USymbolicCollectionAdapter<*, MappedDstKey>) to decomposedSrcKey.collectionId
//        }
//
//        if (srcSymbolicArrayIndex == mappedSrcSymbolicArrayIndex && dstFromIndex == mappedDstFromIndex && dstToIndex == mappedDstToIndex) {
//            return this as USymbolicCollectionAdapter<USymbolicArrayIndex, MappedDstKey> to collectionId
//        }
//
//        return UInputToAllocatedArrayAdapter(
//            mappedSrcSymbolicArrayIndex,
//            mappedDstFromIndex as USizeExpr,
//            mappedDstToIndex as USizeExpr
//        ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//    }
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UInputToAllocatedArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//}
//
///**
// * Used when copying data from input array to another input array.
// */
//class UInputToInputArrayAdapter(
//    private val srcSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstFromSymbolicArrayIndex: USymbolicArrayIndex,
//    private val dstToSymbolicArrayIndex: USymbolicArrayIndex
//) : USymbolicArrayAdapter<USymbolicArrayIndex, USymbolicArrayIndex>(
////    srcFromSymbolicArrayIndex,
////    dstFromSymbolicArrayIndex,
////    dstToIndex
//) {
//    override fun convert(key: USymbolicArrayIndex): USymbolicArrayIndex =
//        srcSymbolicArrayIndex.first to convertIndex(key.second, srcSymbolicArrayIndex.second, dstFromSymbolicArrayIndex.second)
//
//    override val fromKey: USymbolicArrayIndex = dstFromSymbolicArrayIndex
//    override val toKey: USymbolicArrayIndex = dstToSymbolicArrayIndex
//    override val srcKey: USymbolicArrayIndex = srcSymbolicArrayIndex
//
////    override fun clone(
////        srcSymbolicArrayIndex: USymbolicArrayIndex,
////        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
////        dstToIndex: USizeExpr
////    ) = UInputToInputArrayAdapter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
//
//    @Suppress("UNCHECKED_CAST")
//    override fun <Field, Type, MappedDstKey, Sort : USort> map(
//        dstKeyMapper: KeyMapper<USymbolicArrayIndex, MappedDstKey>,
//        composer: UComposer<Field, Type>,
//        collectionId: USymbolicCollectionId<USymbolicArrayIndex, Sort, *>
//    ): Pair<USymbolicCollectionAdapter<*, MappedDstKey>, USymbolicCollectionId<*, Sort, *>>? {
//        val mappedDstFromSymbolicArrayIndex = dstKeyMapper(dstFromSymbolicArrayIndex) ?: return null
//        val mappedDstToSymbolicArrayIndex = dstKeyMapper(dstToSymbolicArrayIndex) ?: return null
//        val mappedSrcSymbolicArrayIndex = collectionId.keyMapper(composer)(srcSymbolicArrayIndex)
//
//        val decomposedSrcKey = collectionId.rebindKey(mappedSrcSymbolicArrayIndex)
//        if (decomposedSrcKey != null) {
//            // In this case, source collection id has been changed. Heuristically, it can change
//            // only to allocated array id, validating it explicitly...
//            require(collectionId is UAllocatedArrayId<*, Sort>)
//            return (UAllocatedToAllocatedArrayAdapter(
//                decomposedSrcKey.key as USizeExpr,
//                mappedDstFromSymbolicArrayIndex as USizeExpr,
//                mappedDstToIndex as USizeExpr
//            ) as USymbolicCollectionAdapter<*, MappedDstKey>) to decomposedSrcKey.collectionId
//        }
//
//        if (srcSymbolicArrayIndex == mappedSrcSymbolicArrayIndex && dstFromIndex == mappedDstFromIndex && dstToIndex == mappedDstToIndex) {
//            return this as USymbolicCollectionAdapter<USymbolicArrayIndex, MappedDstKey> to collectionId
//        }
//
//        return UInputToAllocatedArrayAdapter(
//            mappedSrcSymbolicArrayIndex,
//            mappedDstFromIndex as USizeExpr,
//            mappedDstToIndex as USizeExpr
//        ) as USymbolicCollectionAdapter<USizeExpr, MappedDstKey> to collectionId
//    }
//}

class USymbolicMapMergeAdapter<SrcKey, DstKey>(
    val dstKey: DstKey,
    override val srcKey: SrcKey,
    val setOfKeys: USymbolicCollection<USymbolicSetId<SrcKey, *>, SrcKey, UBoolSort>,
) : USymbolicCollectionAdapter<SrcKey, DstKey> {

    @Suppress("UNCHECKED_CAST")
    override fun convert(key: DstKey): SrcKey =
        when (srcKey) {
            is UExpr<*> ->
                when (dstKey) {
                    is UExpr<*> -> key as SrcKey
                    is Pair<*, *> -> (key as Pair<*, *>).second as SrcKey
                    else -> error("Unexpected symbolic map key $dstKey")
                }

            is Pair<*, *> ->
                when (dstKey) {
                    is UExpr<*> -> (srcKey.first to key) as SrcKey
                    is Pair<*, *> -> (srcKey.first to (key as Pair<*, *>).second) as SrcKey
                    else -> error("Unexpected symbolic map key $dstKey")
                }

            else -> error("Unexpected symbolic map key $srcKey")
        }

    override fun includesConcretely(key: DstKey) =
        includesSymbolically(key).isTrue

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val srcKey = convert(key)
        return setOfKeys.read(srcKey) // ???
    }

    override fun isIncludedByUpdateConcretely(
        update: UUpdateNode<DstKey, *>,
        guard: UBoolExpr,
    ) =
        false

    @Suppress("UNCHECKED_CAST")
    override fun <Type, MappedSrcKey, MappedDstKey> mapDstKeys(
        mappedSrcKey: MappedSrcKey,
        srcCollectionId: USymbolicCollectionId<*, *, *>,
        dstKeyMapper: KeyMapper<DstKey, MappedDstKey>,
        composer: UComposer<Type>,
        mappedKeyInfo: USymbolicCollectionKeyInfo<MappedDstKey, *>
    ): USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>? {
        val mappedDstKey = dstKeyMapper(dstKey) ?: return null

        @Suppress("NAME_SHADOWING")
        val srcCollectionId = srcCollectionId as USymbolicMapId<MappedSrcKey, *, USymbolicSetId<MappedSrcKey, *>, *>
        val mappedKeys = setOfKeys.mapTo(composer, srcCollectionId.keysSetId)
        if (mappedSrcKey === srcKey && mappedDstKey == dstKey) {
            return this as USymbolicCollectionAdapter<MappedSrcKey, MappedDstKey>
        }
        return USymbolicMapMergeAdapter(mappedDstKey, mappedSrcKey, mappedKeys.uncheckedCast())
    }

    override fun toString(collection: USymbolicCollection<*, SrcKey, *>): String =
        "(merge $collection)"

    override fun <Type> applyTo(
        memory: UWritableMemory<Type>,
        srcCollectionId: USymbolicCollectionId<SrcKey, *, *>,
        dstCollectionId: USymbolicCollectionId<DstKey, *, *>,
        guard: UBoolExpr
    ) {

        TODO("Not yet implemented")
    }

    override fun <Reg : Region<Reg>> region(): Reg =
        convertRegion(setOfKeys.collectionId.region(setOfKeys.updates))

    private fun <Reg : Region<Reg>> convertRegion(srcReg: Reg): Reg =
        srcReg // TODO: implement valid region conversion logic
}
