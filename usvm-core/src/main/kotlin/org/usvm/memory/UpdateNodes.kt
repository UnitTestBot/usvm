package org.usvm.memory

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.isFalse
import org.usvm.isTrue
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
     */
    fun <Field, Type> map(
        keyMapper: KeyMapper<Key>,
        composer: UComposer<Field, Type>
    ): UUpdateNode<Key, Sort>
}

/**
 * Represents a single write of [value] into a memory address [key]
 */
class UPinpointUpdateNode<Key, Sort : USort>(
    val key: Key,
    internal val value: UExpr<Sort>,
    private val keyEqualityComparer: (Key, Key) -> UBoolExpr,
    override val guard: UBoolExpr,
) : UUpdateNode<Key, Sort> {
    override fun includesConcretely(key: Key, precondition: UBoolExpr) =
        this.key == key && (guard == guard.ctx.trueExpr || guard == precondition)
    // in fact, we can check less strict formulae: `precondition -> guard`, but it is too complex to compute.

    override fun includesSymbolically(key: Key): UBoolExpr =
        guard.ctx.mkAnd(keyEqualityComparer(this.key, key), guard)

    override fun isIncludedByUpdateConcretely(update: UUpdateNode<Key, Sort>): Boolean =
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

    override fun <Field, Type> map(
        keyMapper: KeyMapper<Key>,
        composer: UComposer<Field, Type>
    ): UPinpointUpdateNode<Key, Sort> {
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
        other is UPinpointUpdateNode<*, *> && this.key == other.key  && this.guard == other.guard

    override fun hashCode(): Int = key.hashCode() * 31 + guard.hashCode() // Ignores value

    override fun toString(): String = "{$key <- $value}".takeIf { guard.isTrue } ?: "{$key <- $value | $guard}"
}

sealed interface UMemoryKeyConverterBase<SrcKey, DstKey, T : UMemoryKeyConverterBase<SrcKey, DstKey, T>> {
    /**
     * Converts destination memory key into source memory key
     */
    fun convert(key: DstKey): SrcKey

    fun <Field, Type> map(composer: UComposer<Field, Type>): T
}

sealed interface UMemoryRegionUpdate<
        SrcKey, DstKey, Sort : USort,
        Region : USymbolicMemoryRegion<*, SrcKey, Sort>,
        KeyConverter : UMemoryKeyConverterBase<SrcKey, DstKey, KeyConverter>
        > : UUpdateNode<DstKey, Sort> {
    val keyConverter: KeyConverter
    val region: Region

    override fun value(key: DstKey): UExpr<Sort> = region.read(keyConverter.convert(key))

    fun changeRegion(newRegion: Region): UUpdateNode<DstKey, Sort>

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
         * Here's the explanation of the [split] function. Consider these memory regions and memory updates:
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
         * Due to the [GuardBuilder] mutability, we have to create a new guard to pass into the [region.split] function,
         * it's a [nextGuardBuilder].
         */
        val splitRegion = region.split(keyConverter.convert(key), predicate, matchingWrites, nextGuardBuilder)

        val resultUpdateNode = if (splitRegion === region) {
            this
        } else {
            @Suppress("UNCHECKED_CAST")
            changeRegion(splitRegion as Region)
        }

        guardBuilder += nodeExcludesKey

        return resultUpdateNode
    }
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
): UMemoryKeyConverterBase<SrcKey, DstKey, UMemoryKeyConverter<SrcKey, DstKey>> {
    /**
     * Converts source memory key into destination memory key
     */
    abstract override fun convert(key: DstKey): SrcKey

    protected fun convertIndex(idx: USizeExpr): USizeExpr = with(srcSymbolicArrayIndex.first.ctx) {
        mkBvSubExpr(mkBvAddExpr(idx, dstFromSymbolicArrayIndex.second), srcSymbolicArrayIndex.second)
    }

    abstract fun clone(
        srcSymbolicArrayIndex: USymbolicArrayIndex,
        dstFromSymbolicArrayIndex: USymbolicArrayIndex,
        dstToIndex: USizeExpr
    ): UMemoryKeyConverter<SrcKey, DstKey>

    override fun <Field, Type> map(composer: UComposer<Field, Type>): UMemoryKeyConverter<SrcKey, DstKey> {
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
class URangedUpdateNode<RegionId : UArrayId<SrcKey, Sort, RegionId>, SrcKey, DstKey, Sort : USort>(
    val fromKey: DstKey,
    val toKey: DstKey,
    override val region: USymbolicMemoryRegion<RegionId, SrcKey, Sort>,
    private val concreteComparer: (DstKey, DstKey) -> Boolean,
    private val symbolicComparer: (DstKey, DstKey) -> UBoolExpr,
    override val keyConverter: UMemoryKeyConverter<SrcKey, DstKey>,
    override val guard: UBoolExpr
) : UMemoryRegionUpdate<SrcKey, DstKey, Sort,
        USymbolicMemoryRegion<RegionId, SrcKey, Sort>,
        UMemoryKeyConverter<SrcKey, DstKey>> {

    override fun includesConcretely(key: DstKey, precondition: UBoolExpr): Boolean =
        concreteComparer(fromKey, key) && concreteComparer(key, toKey) &&
            (guard == guard.ctx.trueExpr || precondition == guard) // TODO: some optimizations here?
    // in fact, we can check less strict formulae: precondition _implies_ guard, but this is too complex to compute.

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val leftIsLefter = symbolicComparer(fromKey, key)
        val rightIsRighter = symbolicComparer(key, toKey)
        val ctx = leftIsLefter.ctx

        return ctx.mkAnd(leftIsLefter, rightIsRighter, guard)
    }

    override fun isIncludedByUpdateConcretely(update: UUpdateNode<DstKey, Sort>): Boolean =
        update.includesConcretely(fromKey, guard) && update.includesConcretely(toKey, guard)

    override fun <Field, Type> map(
        keyMapper: KeyMapper<DstKey>,
        composer: UComposer<Field, Type>
    ): URangedUpdateNode<RegionId, SrcKey, DstKey, Sort> {
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

    override fun changeRegion(newRegion: USymbolicMemoryRegion<RegionId, SrcKey, Sort>) =
        URangedUpdateNode(fromKey, toKey, newRegion, concreteComparer, symbolicComparer, keyConverter, guard)

    // Ignores update
    override fun equals(other: Any?): Boolean =
        other is URangedUpdateNode<*, *, *, *> &&
                this.fromKey == other.fromKey &&
                this.toKey == other.toKey &&
                this.guard == other.guard

    // Ignores update
    override fun hashCode(): Int = (17 * fromKey.hashCode() + toKey.hashCode()) * 31 + guard.hashCode()

    override fun toString(): String {
        return "{[$fromKey..$toKey] <- $region[keyConv($fromKey)..keyConv($toKey)]" +
            ("}".takeIf { guard.isTrue } ?: " | $guard}")
    }
}

class UMergeKeyConverter<SrcKey, DstKey>(
    val srcRef: UHeapRef,
    val dstRef: UHeapRef,
    val converter: UMergeKeyConverter<SrcKey, DstKey>.(DstKey) -> SrcKey
) : UMemoryKeyConverterBase<SrcKey, DstKey, UMergeKeyConverter<SrcKey, DstKey>> {
    override fun convert(key: DstKey): SrcKey = converter(key)

    override fun <Field, Type> map(composer: UComposer<Field, Type>): UMergeKeyConverter<SrcKey, DstKey> {
        val mappedSrc = composer.compose(srcRef)
        val mappedDst = composer.compose(dstRef)
        if (mappedSrc === srcRef && mappedDst == dstRef) {
            return this
        }
        return UMergeKeyConverter(mappedSrc, mappedDst, converter)
    }
}

abstract class UMergeKeyIncludesCheck<
        SrcKey,
        KeySort : USort,
        RegionId : USymbolicMapId<SrcKey, KeySort, *, UBoolSort, RegionId>,
        Reg : Region<Reg>>(
    val region: USymbolicMemoryRegion<RegionId, SrcKey, UBoolSort>,
    val mkKeyRegion: (SrcKey) -> Reg,
    val mkEmptyRegion: () -> Reg,
    val mkUniverseRegion: () -> Reg
) {
    fun check(key: SrcKey): UBoolExpr = region.read(key)

    private val keyRegionBuilder = KeyRegionBuilder()
    private val keyRegionCache = IdentityHashMap<Any?, Reg>()

    abstract fun initialKeyRegion(): Reg

    private inner class KeyRegionBuilder: UMemoryUpdatesVisitor<SrcKey, UBoolSort, Reg>{
        override fun visitSelect(result: Reg, key: SrcKey): UExpr<UBoolSort> {
            error("Unexpected reading")
        }

        override fun visitInitialValue(): Reg = initialKeyRegion()

        override fun visitUpdate(previous: Reg, update: UUpdateNode<SrcKey, UBoolSort>): Reg = when (update) {
            is UMergeUpdateNode<*, *, *, *, *, UBoolSort> -> {
                val updatedKeys: Reg = update.keyIncludesCheck.keyRegion().uncheckedCast()
                previous.union(updatedKeys)
            }

            is UPinpointUpdateNode -> {
                // todo: removed keys
                val keyReg = mkKeyRegion(update.key)
                previous.union(keyReg)
            }

            is URangedUpdateNode<*, *, *, *> -> error("Unexpected range update")
        }
    }

    fun keyRegion(): Reg =
        region.updates.accept(keyRegionBuilder, keyRegionCache)

    abstract fun <Field, Type> map(composer: UComposer<Field, Type>): UMergeKeyIncludesCheck<SrcKey, KeySort, RegionId, Reg>
}

class UMergeKeyIncludesCheckAllocated<
        SrcKey,
        KeySort : USort,
        RegionId : USymbolicMapId<SrcKey, KeySort, *, UBoolSort, RegionId>,
        Reg : Region<Reg>>(
    region: USymbolicMemoryRegion<RegionId, SrcKey, UBoolSort>,
    mkKeyRegion: (SrcKey) -> Reg,
    mkEmptyRegion: () -> Reg,
    mkUniverseRegion: () -> Reg
) : UMergeKeyIncludesCheck<SrcKey, KeySort, RegionId, Reg>(region, mkKeyRegion, mkEmptyRegion, mkUniverseRegion) {

    override fun initialKeyRegion(): Reg = mkEmptyRegion()

    override fun <Field, Type> map(composer: UComposer<Field, Type>): UMergeKeyIncludesCheck<SrcKey, KeySort, RegionId, Reg> {
        val mappedRegion = region.map(composer)
        if (mappedRegion === region) return this
        return UMergeKeyIncludesCheckAllocated(mappedRegion, mkKeyRegion, mkEmptyRegion, mkUniverseRegion)
    }
}

class UMergeKeyIncludesCheckInput<
        KeySort : USort,
        RegionId : USymbolicMapId<USymbolicMapKey<KeySort>, KeySort, *, UBoolSort, RegionId>,
        Reg : Region<Reg>>(
    val ref: UHeapRef,
    region: USymbolicMemoryRegion<RegionId, USymbolicMapKey<KeySort>, UBoolSort>,
    mkKeyRegion: (USymbolicMapKey<KeySort>) -> Reg,
    mkEmptyRegion: () -> Reg,
    mkUniverseRegion: () -> Reg
) : UMergeKeyIncludesCheck<USymbolicMapKey<KeySort>, KeySort, RegionId, Reg>(
    region, mkKeyRegion, mkEmptyRegion, mkUniverseRegion
) {

    override fun initialKeyRegion(): Reg {
        // allocated region
        if (region.defaultValue != null) {
            return mkEmptyRegion()
        }

        val regionId: USymbolicMapId<USymbolicMapKey<KeySort>, KeySort, *, UBoolSort, RegionId> = region.regionId

        // unexpected region
        if (regionId !is UInputSymbolicMapId<KeySort, *, UBoolSort>) {
            return mkUniverseRegion()
        }

        return regionId.visitInputMapUpdates(object : UInputSymbolicMapUpdatesVisitor<KeySort, UBoolSort, Reg> {
            override fun visitInputValue(): Reg = mkUniverseRegion()

            override fun visitUpdate(previous: Reg, key: USymbolicMapKey<KeySort>, value: UExpr<UBoolSort>): Reg {
                val refEquals = ref.uctx.mkHeapRefEq(ref, key.first)
                if (refEquals.isFalse) return mkEmptyRegion()
                if (!refEquals.isTrue) return mkUniverseRegion()

                val keyReg = mkKeyRegion(key)
                // todo: removed keys
                return previous.union(keyReg)
            }

            override fun visitBaseValue(value: UExpr<UBoolSort>): Reg = if (value.isFalse) {
                mkEmptyRegion()
            } else {
                mkUniverseRegion()
            }
        })
    }

    override fun <Field, Type> map(
        composer: UComposer<Field, Type>
    ): UMergeKeyIncludesCheck<USymbolicMapKey<KeySort>, KeySort, RegionId, Reg> {
        val mappedRef = composer.compose(ref)
        val mappedRegion = region.map(composer)
        if (mappedRef === ref && mappedRegion === region) return this
        return UMergeKeyIncludesCheckInput(mappedRef, mappedRegion, mkKeyRegion, mkEmptyRegion, mkUniverseRegion)
    }
}

class UMergeUpdateNode<
        RegionId : USymbolicMapId<SrcKey, KeySort, Reg, ValueSort, RegionId>,
        SrcKey,
        DstKey,
        KeySort : USort,
        Reg : Region<Reg>,
        ValueSort : USort>(
    override val region: USymbolicMemoryRegion<RegionId, SrcKey, ValueSort>,
    val keyIncludesCheck: UMergeKeyIncludesCheck<SrcKey, KeySort, *, Reg>,
    override val keyConverter: UMergeKeyConverter<SrcKey, DstKey>,
    override val guard: UBoolExpr
) : UMemoryRegionUpdate<SrcKey, DstKey, ValueSort,
        USymbolicMemoryRegion<RegionId, SrcKey, ValueSort>,
        UMergeKeyConverter<SrcKey, DstKey>> {

    override fun includesConcretely(key: DstKey, precondition: UBoolExpr): Boolean {
        val srcKey = keyConverter.convert(key)
        val keyIncludes = keyIncludesCheck.check(srcKey)
        return (keyIncludes === keyIncludes.ctx.trueExpr) && (guard == guard.ctx.trueExpr || precondition == guard)
    }

    override fun isIncludedByUpdateConcretely(update: UUpdateNode<DstKey, ValueSort>): Boolean = false

    override fun includesSymbolically(key: DstKey): UBoolExpr {
        val srcKey = keyConverter.convert(key)
        val keyIncludes = keyIncludesCheck.check(srcKey)
        return keyIncludes.ctx.mkAnd(keyIncludes, guard)
    }

    override fun changeRegion(newRegion: USymbolicMemoryRegion<RegionId, SrcKey, ValueSort>) =
        UMergeUpdateNode(newRegion, keyIncludesCheck, keyConverter, guard)

    override fun <Field, Type> map(
        keyMapper: KeyMapper<DstKey>,
        composer: UComposer<Field, Type>
    ): UUpdateNode<DstKey, ValueSort> {
        val mappedRegion = region.map(composer)
        val mappedKeyConverter = keyConverter.map(composer)
        val mappedIncludesCheck = keyIncludesCheck.map(composer)
        val mappedGuard = composer.compose(guard)

        if (mappedRegion === region
            && mappedKeyConverter === keyConverter
            && mappedIncludesCheck === keyIncludesCheck
            && mappedGuard == guard
        ) {
            return this
        }

        return UMergeUpdateNode(mappedRegion, mappedIncludesCheck, mappedKeyConverter, mappedGuard)
    }

    override fun toString(): String = "(merge $region)"
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
 * Used when copying data from input array to allocated one.
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
    srcFromSymbolicArrayIndex: USymbolicArrayIndex,
    dstFromSymbolicArrayIndex: USymbolicArrayIndex,
    dstToIndex: USizeExpr
) : UMemoryKeyConverter<USymbolicArrayIndex, USymbolicArrayIndex>(
    srcFromSymbolicArrayIndex,
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
