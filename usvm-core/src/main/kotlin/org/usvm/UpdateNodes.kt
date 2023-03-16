package org.usvm

import java.util.*

/**
 * Represents the result of memory write operation.
 *
 * Note that it should not be extended by external users. Otherwise, it would require
 * changes in processing of such nodes inside a core since we expect to have only two implementations:
 * [UPinpointUpdateNode] and [URangedUpdateNode].
 */
sealed interface UUpdateNode<Key, ValueSort : USort> {
    /**
     * @returns True if the address [key] got overwritten by this write operation in *any* possible concrete state.
     */
    fun includesConcretely(key: Key): Boolean

    /**
     * @return Symbolic condition expressing that the address [key] got overwritten by this memory write operation.
     * If [includesConcretely] returns true, then this method is obligated to return [UTrue].
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
     * Returns a mapped update node using [keyMapper] and [composer].
     * It is used in [UComposer] for composition.
     */
    fun <Field, Type> map(keyMapper: KeyMapper<Key>, composer: UComposer<Field, Type>): UUpdateNode<Key, ValueSort>
}

/**
 * Represents a single write of [value] into a memory address [key]
 */
class UPinpointUpdateNode<Key, ValueSort : USort>(
    val key: Key,
    internal val value: UExpr<ValueSort>,
    private val keyEqualityComparer: (Key, Key) -> UBoolExpr,
    val guard: UBoolExpr = value.ctx.trueExpr
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

        guardBuilder.nonMatchingUpdatesGuard = ctx.mkAnd(guardBuilder.nonMatchingUpdatesGuard, ctx.mkNot(keyEq))

        // TODO: @sergeypospelov: fix this split
        return this
    }

    override fun <Field, Type> map(
        keyMapper: KeyMapper<Key>,
        composer: UComposer<Field, Type>
    ): UPinpointUpdateNode<Key, ValueSort> {
        val mappedKey = keyMapper(key)
        val mappedValue = composer.compose(value)
        val mappedGuard = composer.compose(guard)

        // If nothing changed, return this value
        if (mappedKey === key && mappedValue === value && mappedGuard === guard) {
            return this
        }

        // Otherwise, construct a new one update node
        return UPinpointUpdateNode(mappedKey, mappedValue, keyEqualityComparer, mappedGuard)
    }

    override fun equals(other: Any?): Boolean =
        other is UPinpointUpdateNode<*, *> && this.key == other.key  // Ignores value

    override fun hashCode(): Int = key.hashCode()  // Ignores value

    override fun toString(): String = "{$key <- $value}"
}

/**
 * Composable converter of memory region keys. Helps to transparently copy content of various regions
 * each into other without eager address conversion.
 * For instance, when we copy array slice [i : i + len] to destination memory slice [j : j + len],
 * we emulate it by memorizing the source memory updates as-is, but read the destination memory by
 * 'redirecting' the index k to k + j - i of the source memory.
 * This conversion is done by [convert].
 * Do not be confused: it converts [DstKey] to [SrcKey] (not vice-versa), as we use it when we
 * read from destination buffer index to source memory.
 */
sealed class UMemoryKeyConverter<SrcKey, DstKey>(
    val srcSymbolicArrayIndex: USymbolicArrayIndex,
    val dstFromSymbolicArrayIndex: USymbolicArrayIndex,
    val dstToIndex: USizeExpr
) {
    /**
     * Converts source memory key into destination memory key
     */
    abstract fun convert(key: DstKey): SrcKey

    protected fun convertIndex(idx: USizeExpr): USizeExpr = with(srcSymbolicArrayIndex.first.ctx) {
        return mkBvSubExpr(mkBvAddExpr(idx, dstFromSymbolicArrayIndex.second), srcSymbolicArrayIndex.second)
    }

    abstract fun clone(
        srcSymbolicArrayIndex: USymbolicArrayIndex,
        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
        dstToIndex: USizeExpr
    ): UMemoryKeyConverter<SrcKey, DstKey>

    fun <Field, Type> map(composer: UComposer<Field, Type>): UMemoryKeyConverter<SrcKey, DstKey> {
        val (srcRef, srcIdx) = srcSymbolicArrayIndex
        val (dstRef, dstIdx) = dstFromSymbolicArrayIndex

        val newSrcHeapAddr = composer.compose(srcRef)
        val newSrcArrayIndex = composer.compose(srcIdx)
        val newDstHeapAddress = composer.compose(dstRef)
        val newDstFromIndex = composer.compose(dstIdx)
        val newDstToIndex = composer.compose(dstToIndex)

        if (newSrcHeapAddr === srcRef &&
            newSrcArrayIndex === srcIdx &&
            newDstHeapAddress === dstRef &&
            newDstFromIndex === dstIdx &&
            newDstToIndex === dstToIndex
        ) {
            return this
        }

        return clone(
            srcSymbolicArrayIndex = newSrcHeapAddr to newSrcArrayIndex,
            dstFromSymbolicArrayIndex = newDstHeapAddress to newDstFromIndex,
            dstToIndex = newDstToIndex
        )
    }
}

/**
 * Represents a synchronous overwriting the range of addresses [[fromKey] : [toKey]]
 * with values from memory region [region] read from range
 * of addresses [[keyConverter].convert([fromKey]) : [keyConverter].convert([toKey])]
 */
class URangedUpdateNode<RegionId : UArrayId<ArrayType, SrcKey>, ArrayType, SrcKey, DstKey, ValueSort : USort>(
    val fromKey: DstKey,
    val toKey: DstKey,
    val region: UMemoryRegion<RegionId, SrcKey, ValueSort>,
    private val concreteComparer: (DstKey, DstKey) -> Boolean,
    private val symbolicComparer: (DstKey, DstKey) -> UBoolExpr,
    val keyConverter: UMemoryKeyConverter<SrcKey, DstKey>,
    val guard: UBoolExpr
) : UUpdateNode<DstKey, ValueSort> {
    override fun includesConcretely(key: DstKey): Boolean =
        concreteComparer(fromKey, key) && concreteComparer(key, toKey) && guard.isTrue

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val leftIsLefter = symbolicComparer(fromKey, key)
        val rightIsRighter = symbolicComparer(key, toKey)
        val ctx = leftIsLefter.ctx

        return ctx.mkAnd(leftIsLefter, rightIsRighter, guard)
    }

    override fun value(key: DstKey): UExpr<ValueSort> = region.read(keyConverter.convert(key))

    override fun <Field, Type> map(
        keyMapper: (DstKey) -> DstKey,
        composer: UComposer<Field, Type>
    ): URangedUpdateNode<RegionId, ArrayType, SrcKey, DstKey, ValueSort> {
        val mappedFromKey = keyMapper(fromKey)
        val mappedToKey = keyMapper(toKey)
        val mappedRegion = region.map(composer)
        val mappedKeyConverter = keyConverter.map(composer)
        val mappedGuard = composer.compose(guard)

        // If nothing changed, return this
        if (mappedFromKey === fromKey
            && mappedToKey === toKey
            && mappedRegion === region
            && mappedKeyConverter === keyConverter
            && mappedGuard === guard
        ) {
            return this
        }

        // Otherwise, construct a new one updated node
        return URangedUpdateNode(
            mappedFromKey,
            mappedToKey,
            mappedRegion,
            concreteComparer,
            symbolicComparer,
            mappedKeyConverter,
            mappedGuard
        )
    }

    override fun equals(other: Any?): Boolean =
        other is URangedUpdateNode<*, *, *, *, *> && this.fromKey == other.fromKey && this.toKey == other.toKey  // Ignores update

    override fun hashCode(): Int = 31 * fromKey.hashCode() + toKey.hashCode()  // Ignores update

    override fun split(
        key: DstKey, predicate: (UExpr<ValueSort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<ValueSort>>>,
        guardBuilder: GuardBuilder
    ): UUpdateNode<DstKey, ValueSort> {
        val splitRegion = region.split(keyConverter.convert(key), predicate, matchingWrites, guardBuilder)
        if (splitRegion === region) {
            return this
        }

        return URangedUpdateNode(
            fromKey,
            toKey,
            splitRegion,
            concreteComparer,
            symbolicComparer,
            keyConverter,
            guard
        )
    }
}

/**
 * Used when copying data from allocated array to another allocated array.
 */
class UAllocatedToAllocatedKeyConverter(
    srcSymbolicArrayIndex: USymbolicArrayIndex,
    dstFromSymbolicArrayIndex: USymbolicArrayIndex,
    dstToIndex: USizeExpr
) : UMemoryKeyConverter<USizeExpr, USizeExpr>(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex) {
    override fun convert(key: USizeExpr): USizeExpr = convertIndex(key)

    override fun clone(
        srcSymbolicArrayIndex: USymbolicArrayIndex,
        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
        dstToIndex: USizeExpr
    ) = UAllocatedToAllocatedKeyConverter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
}

/**
 * Used when copying data from allocated array to input one.
 */
class UAllocatedToInputKeyConverter(
    srcSymbolicArrayIndex: USymbolicArrayIndex,
    dstFromSymbolicArrayIndex: USymbolicArrayIndex,
    dstToIndex: USizeExpr
) : UMemoryKeyConverter<USizeExpr, USymbolicArrayIndex>(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex) {
    override fun convert(key: USymbolicArrayIndex): USizeExpr = convertIndex(key.second)

    override fun clone(
        srcSymbolicArrayIndex: USymbolicArrayIndex,
        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
        dstToIndex: USizeExpr
    ) = UAllocatedToInputKeyConverter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
}

/**
 * Used when copying data from allocated input to allocated one.
 */
class UInputToAllocatedKeyConverter(
    srcSymbolicArrayIndex: USymbolicArrayIndex,
    dstFromSymbolicArrayIndex: USymbolicArrayIndex,
    dstToIndex: USizeExpr
) : UMemoryKeyConverter<USymbolicArrayIndex, USizeExpr>(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex) {
    override fun convert(key: USizeExpr): USymbolicArrayIndex = srcSymbolicArrayIndex.first to convertIndex(key)

    override fun clone(
        srcSymbolicArrayIndex: USymbolicArrayIndex,
        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
        dstToIndex: USizeExpr
    ) = UInputToAllocatedKeyConverter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
}

/**
 * Used when copying data from input array to another input array.
 */
class UInputToInputKeyConverter(
    srcSymbolicArrayIndex: USymbolicArrayIndex,
    dstFromSymbolicArrayIndex: USymbolicArrayIndex,
    dstToIndex: USizeExpr
) : UMemoryKeyConverter<USymbolicArrayIndex, USymbolicArrayIndex>(
    srcSymbolicArrayIndex,
    dstFromSymbolicArrayIndex,
    dstToIndex
) {
    override fun convert(key: USymbolicArrayIndex): USymbolicArrayIndex =
        srcSymbolicArrayIndex.first to convertIndex(key.second)

    override fun clone(
        srcSymbolicArrayIndex: USymbolicArrayIndex,
        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
        dstToIndex: USizeExpr
    ) = UInputToInputKeyConverter(srcSymbolicArrayIndex, dstFromSymbolicArrayIndex, dstToIndex)
}
