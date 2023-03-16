package org.usvm

import org.ksmt.utils.asExpr
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree
import java.util.*

//region Memory region


/**
 * A typealias for a lambda that takes a key, a region and returns a reading from the region by the key.
 */
typealias UInstantiator<RegionId, Key, Sort> = (key: Key, UMemoryRegion<RegionId, Key, Sort>) -> UExpr<Sort>

/**
 * A uniform unbounded slice of memory. Indexed by [Key], stores symbolic values.
 *
 * @property regionId describes the source of the region. Memory regions with the same [regionId] represent the same
 * memory area, but in different states.
 */
data class UMemoryRegion<RegionId : URegionId<Key>, Key, Sort : USort>(
    val regionId: RegionId,
    val sort: Sort,
    private val updates: UMemoryUpdates<Key, Sort>,
    private val defaultValue: UExpr<Sort>?, // If defaultValue = null then this region is filled with symbolics
    private val instantiator: UInstantiator<RegionId, Key, Sort>
) {
    private fun read(key: Key, updates: UMemoryUpdates<Key, Sort>): UExpr<Sort> {
        val lastUpdatedElement = updates.lastUpdatedElementOrNull()

        if (lastUpdatedElement == null && defaultValue != null) {
            // Reading from untouched array filled with defaultValue
            return defaultValue
        }

        if (lastUpdatedElement != null) {
            if (lastUpdatedElement.includesConcretely(key)) {
                // The last write has overwritten the key
                return lastUpdatedElement.value(key)
            }
        }

        val localizedRegion = if (updates === this.updates) {
            this
        } else {
            UMemoryRegion(regionId, sort, updates, defaultValue, instantiator)
        }

        return instantiator(key, localizedRegion)
    }

    fun read(key: Key): UExpr<Sort> {
        if (sort == sort.uctx.addressSort) {
            // Here we split concrete heap addresses from symbolic ones to optimize further memory operations.
            return splittingRead(key) { it is UConcreteHeapRef }
        }

        val updates = updates.read(key)
        return read(key, updates)
    }

    /**
     * Reads key from this memory region, but 'bubbles up' entries satisfying predicates.
     * For example, imagine we read for example key z from array A with two updates: v written into x and w into y.
     * Usual [read] produces the expression
     *      A{x <- v}{y <- w}[z]
     * If v satisfies [predicate] and w does not, then [splittingRead] instead produces the expression
     *      ite(y <> z /\ x = z, v, A{y <- w}[z]).
     * These two expressions are semantically equivalent, but the second one 'splits' v out of the rest
     * memory updates.
     */
    private fun splittingRead(key: Key, predicate: (UExpr<Sort>) -> Boolean): UExpr<Sort> {
        val ctx = sort.ctx
        val guardBuilder = GuardBuilder(ctx.trueExpr, ctx.trueExpr)
        val matchingWrites = LinkedList<Pair<UBoolExpr, UExpr<Sort>>>()
        val splittingUpdates = split(key, predicate, matchingWrites, guardBuilder).updates

        if (matchingWrites.isEmpty()) {
            return instantiator(key, this)
        }

        val reading = read(key, splittingUpdates)
        var iteAcc = reading

        for (write in matchingWrites) {
            iteAcc = ctx.mkIte(write.first, write.second, iteAcc)
        }

        return iteAcc
    }

    fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryRegion<RegionId, Key, Sort> {
        assert(value.sort == sort)

        val newUpdates = updates.write(key, value, guard)
        return UMemoryRegion(regionId, sort, newUpdates, defaultValue, instantiator)
    }

    internal fun split(
        key: Key, predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<Sort>>>,
        guardBuilder: GuardBuilder
    ): UMemoryRegion<RegionId, Key, Sort> {
        // TODO: either check in UMemoryRegion constructor that we do not construct memory region with
        //       non-null reference as default value, or implement splitting by default value.
        assert(defaultValue == null || !predicate(defaultValue))

        val count = matchingWrites.size
        val splittingUpdates = updates.read(key).split(key, predicate, matchingWrites, guardBuilder)
        val sizeRemainedUnchanged = matchingWrites.size == count

        if (sizeRemainedUnchanged) {
            return this
        }

        return UMemoryRegion(regionId, sort, splittingUpdates, defaultValue, instantiator)
    }

    /**
     * Maps the region using [composer].
     * It is used in [UComposer] for composition operation.
     *
     * Note: after this operation a region returned as a result might be in `broken` state:
     * it might have both symbolic and concrete values as keys in it.
     */
    fun <Field, Type> map(
        composer: UComposer<Field, Type>,
        instantiator: UInstantiator<RegionId, Key, Sort> = this.instantiator
    ): UMemoryRegion<RegionId, Key, Sort> {
        // Map the updates and the default value
        val mappedUpdates = updates.map(regionId.keyMapper(composer), composer)
        val mappedDefaultValue = defaultValue?.let { composer.compose(it) }

        // If there is no changes after their composition, return unchecked region
        if (mappedUpdates === updates && mappedDefaultValue === defaultValue) {
            return this
        }

        // Otherwise, construct a new region with mapped values and a new instantiator.
        return UMemoryRegion(regionId, sort, mappedUpdates, mappedDefaultValue, instantiator)
    }

    @Suppress("UNCHECKED_CAST")
    fun <Field, Type> applyTo(heap: USymbolicHeap<Field, Type>) {
        // Apply each update on the copy
        updates.forEach {
            when (it) {
                is UPinpointUpdateNode<Key, Sort> -> regionId.write(it.key, sort, heap, it.value, it.guard)
                is URangedUpdateNode<*, *, *, Key, Sort> -> {
                    it.region.applyTo(heap)

                    val (srcFromRef, srcFromIdx) = it.keyConverter.srcSymbolicArrayIndex
                    val (dstFromRef, dstFromIdx) = it.keyConverter.dstFromSymbolicArrayIndex
                    val dstTo = it.keyConverter.dstToIndex
                    val arrayType = it.region.regionId.arrayType as Type

                    heap.memcpy(srcFromRef, dstFromRef, arrayType, sort, srcFromIdx, dstFromIdx, dstTo, it.guard)
                }
            }
        }
    }

    /**
     * @return Memory region which obtained from this one by overwriting the range of addresses [[fromKey] : [toKey]]
     * with values from memory region [fromRegion] read from range
     * of addresses [[keyConverter].convert([fromKey]) : [keyConverter].convert([toKey])]
     */
    fun <ArrayType, OtherRegionId : UArrayId<ArrayType, SrcKey>, SrcKey> memcpy(
        fromRegion: UMemoryRegion<OtherRegionId, SrcKey, Sort>,
        fromKey: Key, toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): UMemoryRegion<RegionId, Key, Sort> {
        val updatesCopy = updates.copy(fromRegion, fromKey, toKey, keyConverter, guard)
        return UMemoryRegion(regionId, sort, updatesCopy, defaultValue, instantiator)
    }
}

class GuardBuilder(var matchingUpdatesGuard: UBoolExpr, var nonMatchingUpdatesGuard: UBoolExpr)

//endregion

//region Instantiations

typealias USymbolicArrayIndex = Pair<UHeapRef, USizeExpr>

fun heapRefEq(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    ref1.ctx.mkEq(ref1, ref2)  // TODO: use simplified equality!

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpSymbolic(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    error("Heap references should not be compared!")

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpConcrete(ref1: UHeapRef, ref2: UHeapRef): Boolean =
    error("Heap references should not be compared!")

fun indexEq(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkEq(idx1, idx2)  // TODO: use simplified equality!

fun indexLeSymbolic(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkBvSignedLessOrEqualExpr(idx1, idx2)  // TODO: use simplified comparison!

fun indexLeConcrete(idx1: USizeExpr, idx2: USizeExpr): Boolean =
    // TODO: to optimize things up, we could pass path constraints here and lookup the numeric bounds for idx1 and idx2
    idx1 == idx2 || (idx1 is UConcreteSize && idx2 is UConcreteSize && idx1.numberValue <= idx2.numberValue)

fun refIndexEq(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    // TODO: use simplified operations!
    return@with (idx1.first eq idx2.first) and indexEq(idx1.second, idx2.second)
}

fun refIndexCmpSymbolic(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    return@with (idx1.first eq idx2.first) and indexLeSymbolic(idx1.second, idx2.second)
}

fun refIndexCmpConcrete(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): Boolean =
    idx1.first == idx2.first && indexLeConcrete(idx1.second, idx2.second)

// TODO: change it to intervals region
typealias UArrayIndexRegion = SetRegion<UIndexType>

fun indexRegion(idx: USizeExpr): UArrayIndexRegion =
    when (idx) {
        is UConcreteSize -> SetRegion.singleton(idx.numberValue)
        else -> SetRegion.universe()
    }

fun indexRangeRegion(idx1: USizeExpr, idx2: USizeExpr): UArrayIndexRegion =
    when (idx1) {
        is UConcreteSize ->
            when (idx2) {
                is UConcreteSize -> SetRegion.ofSequence((idx1.numberValue..idx2.numberValue).asSequence())
                else -> SetRegion.universe()
            }

        else -> SetRegion.universe()
    }

fun refIndexRegion(idx: USymbolicArrayIndex): UArrayIndexRegion = indexRegion(idx.second)
fun refIndexRangeRegion(
    idx1: USymbolicArrayIndex,
    idx2: USymbolicArrayIndex
): UArrayIndexRegion = indexRangeRegion(idx1.second, idx2.second)

/**
 * An interface that represents any possible type of regions that can be used in the memory.
 */
interface URegionId<Key> {
    fun <Field, ArrayType, Sort : USort> read(
        key: Key,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>
    ): UExpr<Sort>

    fun <Field, ArrayType, Sort : USort> write(
        key: Key,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    )

    fun <Field, ArrayType, SrcKey, Sort : USort> copy(
        from: Key,
        to: Key,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        converter: UMemoryKeyConverter<Key, SrcKey>,
        guard: UBoolExpr
    )

    fun <Field, ArrayType> keyMapper(composer: UComposer<Field, ArrayType>): KeyMapper<Key>
}

/**
 * A region id for a region storing the specific [field].
 */
data class UInputFieldRegionId<Field> internal constructor(
    val field: Field
) : URegionId<UHeapRef> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: UHeapRef,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>
    ) = heap.readField(key, field as Field, sort).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: UHeapRef,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) = heap.writeField(key, field as Field, sort, value, guard)

    override fun <Field, ArrayType, SrcKey, Sort : USort> copy(
        from: UHeapRef,
        to: UHeapRef,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        converter: UMemoryKeyConverter<UHeapRef, SrcKey>,
        guard: UBoolExpr
    ) = error("Fields region copying should never happen")

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>
    ): KeyMapper<UHeapRef> = { composer.compose(it) }
}

interface UArrayId<ArrayType, Key> : URegionId<Key> {
    val arrayType: ArrayType
}

/**
 * A region id for a region storing arrays allocated during execution.
 * Each identifier contains information about its [arrayType] and [address].
 */
data class UAllocatedArrayId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
    val address: UConcreteHeapAddress,
) : UArrayId<ArrayType, USizeExpr> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: USizeExpr,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>
    ): UExpr<Sort> {
        val ref = key.uctx.mkConcreteHeapRef(address)
        return heap.readArrayIndex(ref, key, arrayType as ArrayType, sort).asExpr(sort)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: USizeExpr,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        heap.writeArrayIndex(ref, key, arrayType as ArrayType, sort, value, guard)
    }

    override fun <Field, ArrayType, SrcKey, Sort : USort> copy(
        from: USizeExpr,
        to: USizeExpr,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        converter: UMemoryKeyConverter<USizeExpr, SrcKey>,
        guard: UBoolExpr
    ) {
        TODO("Not yet implemented")
    }

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>
    ): KeyMapper<USizeExpr> = { composer.compose(it) }

    // we don't include arrayType into hashcode and equals, because [address] already defines unambiguously
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedArrayId<*>

        if (address != other.address) return false

        return true
    }

    override fun hashCode(): Int {
        return address
    }
}

/**
 * A region id for a region storing arrays retrieved as a symbolic value, contains only its [arrayType].
 */
data class UInputArrayId<ArrayType> internal constructor(
    override val arrayType: ArrayType
) : UArrayId<ArrayType, USymbolicArrayIndex> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: USymbolicArrayIndex,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>
    ): UExpr<Sort> = heap.readArrayIndex(key.first, key.second, arrayType as ArrayType, sort).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: USymbolicArrayIndex,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) = heap.writeArrayIndex(key.first, key.second, arrayType as ArrayType, sort, value, guard)

    override fun <Field, ArrayType, SrcKey, Sort : USort> copy(
        from: USymbolicArrayIndex,
        to: USymbolicArrayIndex,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        converter: UMemoryKeyConverter<USymbolicArrayIndex, SrcKey>,
        guard: UBoolExpr
    ) {
        TODO("Not yet implemented")
    }

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>
    ): KeyMapper<USymbolicArrayIndex> = {
        val ref = composer.compose(it.first)
        val idx = composer.compose(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }
}

/**
 * A region id for a region storing array lengths for arrays of a specific [arrayType].
 */
data class UInputArrayLengthId<ArrayType> internal constructor(
    val arrayType: ArrayType
) : URegionId<UHeapRef> {
    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> read(
        key: UHeapRef,
        sort: Sort,
        heap: UReadOnlySymbolicHeap<Field, ArrayType>
    ): UExpr<Sort> = heap.readArrayLength(key, arrayType as ArrayType).asExpr(sort)

    @Suppress("UNCHECKED_CAST")
    override fun <Field, ArrayType, Sort : USort> write(
        key: UHeapRef,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        assert(guard.isTrue)
        heap.writeArrayLength(key, value.asExpr(key.uctx.sizeSort), arrayType as ArrayType)
    }

    override fun <Field, ArrayType, SrcKey, Sort : USort> copy(
        from: UHeapRef,
        to: UHeapRef,
        sort: Sort,
        heap: USymbolicHeap<Field, ArrayType>,
        converter: UMemoryKeyConverter<UHeapRef, SrcKey>,
        guard: UBoolExpr
    ) = error("Lengths region copying should never happen")

    override fun <Field, ArrayType> keyMapper(
        composer: UComposer<Field, ArrayType>
    ): KeyMapper<UHeapRef> = { composer.compose(it) }
}

typealias UInputFieldMemoryRegion<Field, Sort> = UMemoryRegion<UInputFieldRegionId<Field>, UHeapRef, Sort>
typealias UAllocatedArrayMemoryRegion<ArrayType, Sort> = UMemoryRegion<UAllocatedArrayId<ArrayType>, USizeExpr, Sort>
typealias UInputArrayMemoryRegion<ArrayType, Sort> = UMemoryRegion<UInputArrayId<ArrayType>, USymbolicArrayIndex, Sort>
typealias UInputArrayLengthMemoryRegion<ArrayType> = UMemoryRegion<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>

typealias KeyMapper<Key> = (Key) -> Key

val <Field, Sort : USort> UInputFieldMemoryRegion<Field, Sort>.field
    get() = regionId.field

val <ArrayType, Sort : USort> UAllocatedArrayMemoryRegion<ArrayType, Sort>.allocatedArrayType
    get() = regionId.arrayType
val <ArrayType, Sort : USort> UAllocatedArrayMemoryRegion<ArrayType, Sort>.allocatedAddress
    get() = regionId.address

val <ArrayType, Sort : USort> UInputArrayMemoryRegion<ArrayType, Sort>.inputArrayType
    get() = regionId.arrayType

val <ArrayType> UInputArrayLengthMemoryRegion<ArrayType>.inputLengthArrayType
    get() = regionId.arrayType

fun <Field, Sort : USort> emptyInputFieldRegion(
    field: Field,
    sort: Sort,
    instantiator: UInstantiator<UInputFieldRegionId<Field>, UHeapRef, Sort>
): UInputFieldMemoryRegion<Field, Sort> = UMemoryRegion(
    UInputFieldRegionId(field),
    sort,
    UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
    defaultValue = null,
    instantiator
)

fun <ArrayType, Sort : USort> emptyAllocatedArrayRegion(
    arrayType: ArrayType,
    address: UConcreteHeapAddress,
    sort: Sort,
    instantiator: UInstantiator<UAllocatedArrayId<ArrayType>, USizeExpr, Sort>
): UAllocatedArrayMemoryRegion<ArrayType, Sort> {
    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::indexRegion, ::indexRangeRegion, ::indexEq, ::indexLeConcrete, ::indexLeSymbolic
    )
    val regionId = UAllocatedArrayId(arrayType, address)
    return UMemoryRegion(regionId, sort, updates, sort.defaultValue(), instantiator)
}

fun <ArrayType, Sort : USort> emptyInputArrayRegion(
    arrayType: ArrayType,
    sort: Sort,
    instantiator: UInstantiator<UInputArrayId<ArrayType>, USymbolicArrayIndex, Sort>
): UInputArrayMemoryRegion<ArrayType, Sort> {
    val updates = UTreeUpdates<USymbolicArrayIndex, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::refIndexRegion, ::refIndexRangeRegion, ::refIndexEq, ::refIndexCmpConcrete, ::refIndexCmpSymbolic
    )
    return UMemoryRegion(UInputArrayId(arrayType), sort, updates, defaultValue = null, instantiator)
}

fun <ArrayType> emptyArrayLengthRegion(
    arrayType: ArrayType,
    ctx: UContext,
    instantiator: UInstantiator<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
): UInputArrayLengthMemoryRegion<ArrayType> =
    UMemoryRegion(
        UInputArrayLengthId(arrayType),
        ctx.sizeSort,
        UEmptyUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
        defaultValue = null,
        instantiator
    )

//endregion
