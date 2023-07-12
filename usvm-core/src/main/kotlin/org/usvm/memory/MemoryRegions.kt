package org.usvm.memory

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.toPersistentMap
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UConcreteSize
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIndexType
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.sampleUValue
import org.usvm.uctx
import org.usvm.util.ProductRegion
import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree

//region Memory region


interface UReadOnlyMemoryRegion<Key, Sort : USort> {
    fun read(key: Key): UExpr<Sort>
}


interface UMemoryRegion<Key, Sort : USort> : UReadOnlyMemoryRegion<Key, Sort> {
    fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): UMemoryRegion<Key, Sort>
}


/**
 * A uniform unbounded slice of memory. Indexed by [Key], stores symbolic values.
 *
 * @property regionId describes the source of the region. Memory regions with the same [regionId] represent the same
 * memory area, but in different states.
 *
 * @property defaultValue describes the initial values for the region. If [defaultValue] equals `null` then this region
 * is filled with symbolics.
 */
data class USymbolicMemoryRegion<out RegionId : URegionId<Key, Sort, RegionId>, Key, Sort : USort>(
    val regionId: RegionId,
    val updates: UMemoryUpdates<Key, Sort>,
) : UMemoryRegion<Key, Sort> {
    // to save memory usage
    val sort: Sort get() = regionId.sort

    // If we replace it with get(), we have to check for nullability in read function.
    val defaultValue = regionId.defaultValue

    private fun read(key: Key, updates: UMemoryUpdates<Key, Sort>): UExpr<Sort> {
        val lastUpdatedElement = updates.lastUpdatedElementOrNull()

        if (lastUpdatedElement == null && defaultValue != null) {
            // Reading from an untouched array filled with defaultValue
            return defaultValue
        }

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

        return regionId.instantiate(localizedRegion, key)
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
     * Reads key from this memory region, but 'bubbles up' entries satisfying predicates.
     * For example, imagine we read for example key z from array A with two updates: v written into x and w into y.
     * Usual [read] produces the expression
     *      A{x <- v}{y <- w}[z]
     * If v satisfies [predicate] and w does not, then [splittingRead] instead produces the expression
     *      ite(y != z /\ x = z, v, A{y <- w}[z]).
     * These two expressions are semantically equivalent, but the second one 'splits' v out of the rest
     * memory updates.
     */
    private fun splittingRead(key: Key, predicate: (UExpr<Sort>) -> Boolean): UExpr<Sort> {
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

    override fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): USymbolicMemoryRegion<RegionId, Key, Sort> {
        assert(value.sort == sort)

        val newUpdates = if (sort == sort.uctx.addressSort) {
            // we must split symbolic and concrete heap refs here,
            // because later in [splittingRead] we check value is UConcreteHeapRef
            var newUpdates = updates

            withHeapRef(
                value.asExpr(sort.uctx.addressSort),
                initialGuard = guard,
                blockOnConcrete = { (ref, guard) -> newUpdates = newUpdates.write(key, ref.asExpr(sort), guard) },
                blockOnSymbolic = { (ref, guard) -> newUpdates = newUpdates.write(key, ref.asExpr(sort), guard) }
            )

            newUpdates
        } else {
            updates.write(key, value, guard)
        }


        return this.copy(updates = newUpdates)
    }

    /**
     * Splits this [USymbolicMemoryRegion] on two parts:
     * * Values of [UUpdateNode]s satisfying [predicate] are added to the [matchingWrites].
     * * [UUpdateNode]s unsatisfying [predicate] remain in the result memory region.
     *
     * The [guardBuilder] is used to build guards for values added to [matchingWrites]. In the end, the [guardBuilder]
     * is updated and contains predicate indicating that the [key] can't be included in any of visited [UUpdateNode]s.
     *
     * @return new [USymbolicMemoryRegion] without writes satisfying [predicate] or this [USymbolicMemoryRegion] if no
     * matching writes were found.
     * @see [UMemoryUpdates.split], [splittingRead]
     */
    internal fun split(
        key: Key,
        predicate: (UExpr<Sort>) -> Boolean,
        matchingWrites: MutableList<GuardedExpr<UExpr<Sort>>>,
        guardBuilder: GuardBuilder,
    ): USymbolicMemoryRegion<RegionId, Key, Sort> {
        // TODO: either check in USymbolicMemoryRegion constructor that we do not construct memory region with
        //       non-null reference as default value, or implement splitting by default value.
        assert(defaultValue == null || !predicate(defaultValue))

        val splitUpdates = updates.read(key).split(key, predicate, matchingWrites, guardBuilder)

        return if (splitUpdates === updates) {
            this
        } else {
            this.copy(updates = splitUpdates)
        }
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
    ): USymbolicMemoryRegion<RegionId, Key, Sort> {
        // Map the updates and the regionId
        val mappedRegionId = regionId.map(composer)
        val mappedUpdates = updates.map(regionId.keyMapper(composer), composer)

        if (mappedUpdates === updates && mappedRegionId === regionId) {
            return this
        }

        return USymbolicMemoryRegion(mappedRegionId, mappedUpdates)
    }

    @Suppress("UNCHECKED_CAST")
    fun <Field, Type> applyTo(heap: USymbolicHeap<Field, Type>) {
        // Apply each update on the copy
        updates.forEach {
            when (it) {
                is UPinpointUpdateNode<Key, Sort> -> regionId.write(heap, it.key, it.value, it.guard)
                is URangedUpdateNode<*, *, Key, Sort> -> {
                    it.region.applyTo(heap)

                    val (srcFromRef, srcFromIdx) = it.keyConverter.srcSymbolicArrayIndex
                    val (dstFromRef, dstFromIdx) = it.keyConverter.dstFromSymbolicArrayIndex
                    val dstToIdx = it.keyConverter.dstToIndex

                    val regionId = it.region.regionId
                    when (regionId) {
                        is UTypedArrayId<*, *, *, *> -> {
                            val arrayType = regionId.arrayType as Type
                            heap.memcpy(
                                srcRef = srcFromRef,
                                dstRef = dstFromRef,
                                type = arrayType,
                                elementSort = sort,
                                fromSrcIdx = srcFromIdx,
                                fromDstIdx = dstFromIdx,
                                toDstIdx = dstToIdx,
                                guard = it.guard
                            )
                        }
                        is USymbolicMapId<*, *, *, *, *> -> {
                            val descriptor = regionId.descriptor
                            heap.copySymbolicMapIndexRange(
                                descriptor as USymbolicMapDescriptor<USizeSort, Sort, *>,
                                srcFromRef,
                                dstFromRef,
                                srcFromIdx,
                                dstFromIdx,
                                dstToIdx,
                                it.guard
                            )
                        }
                    }
                }
                is UMergeUpdateNode<*, *, Key, *, *, Sort> -> {
                    applyMergeNodeToHeap(it, heap)
                }
            }
        }
    }

    private fun <RegionId : USymbolicMapId<SrcKey, KeySort, Reg, Sort, RegionId>,
            SrcKey, KeySort : USort, Reg : Region<Reg>> applyMergeNodeToHeap(
        mergeNode: UMergeUpdateNode<RegionId, SrcKey, Key, KeySort, Reg, Sort>,
        heap: USymbolicHeap<*, *>
    ) {
        mergeNode.region.applyTo(heap)

        val keyIncludesCheck = mergeNode.keyIncludesCheck
        keyIncludesCheck.region.applyTo(heap)
        val keyContainsDescriptor = keyIncludesCheck.region.regionId.descriptor

        val regionId = mergeNode.region.regionId
        val srcRef = mergeNode.keyConverter.srcRef
        val dstRef = mergeNode.keyConverter.dstRef

        heap.mergeSymbolicMap(
            regionId.descriptor,
            keyContainsDescriptor.uncheckedCast(),
            srcRef,
            dstRef,
            mergeNode.guard
        )
    }

    /**
     * @return Memory region which obtained from this one by overwriting the range of addresses [[fromKey] : [toKey]]
     * with values from memory region [fromRegion] read from range
     * of addresses [[keyConverter].convert([fromKey]) : [keyConverter].convert([toKey])]
     */
    fun <OtherRegionId : UArrayId<SrcKey, Sort, OtherRegionId>, SrcKey> copyRange(
        fromRegion: USymbolicMemoryRegion<OtherRegionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): USymbolicMemoryRegion<RegionId, Key, Sort> {
        val updatesCopy = updates.copyRange(fromRegion, fromKey, toKey, keyConverter, guard)
        return this.copy(updates = updatesCopy)
    }

    fun <OtherRegionId : USymbolicMapId<SrcKey, KeySort, Reg, Sort, OtherRegionId>,
            SrcKey, KeySort : USort, Reg : Region<Reg>> mergeWithRegion(
        fromRegion: USymbolicMemoryRegion<OtherRegionId, SrcKey, Sort>,
        keyIncludesCheck: UMergeKeyIncludesCheck<SrcKey, KeySort, *, Reg>,
        keyConverter: UMergeKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): USymbolicMemoryRegion<RegionId, Key, Sort> {
        val updatesCopy = updates.mergeWithRegion(fromRegion, keyIncludesCheck, keyConverter, guard)
        return this.copy(updates = updatesCopy)
    }

    override fun toString(): String =
        buildString {
            append('<')
            append(defaultValue)
            updates.forEach {
                append(it.toString())
            }
            append('>')
            append('@')
            append(regionId)
        }
}

class GuardBuilder(nonMatchingUpdates: UBoolExpr) {
    var nonMatchingUpdatesGuard: UBoolExpr = nonMatchingUpdates
        private set

    operator fun plusAssign(guard: UBoolExpr) {
        nonMatchingUpdatesGuard = guarded(guard)
    }

    /**
     * @return [expr] guarded by this guard builder. Implementation uses no-flattening operations, because we accumulate
     * [nonMatchingUpdatesGuard] and otherwise it would take quadratic time.
     */
    fun guarded(expr: UBoolExpr): UBoolExpr = expr.ctx.mkAnd(nonMatchingUpdatesGuard, expr, flat = false)
}

//endregion

//region Instantiations

typealias USymbolicArrayIndex = Pair<UHeapRef, USizeExpr>
typealias USymbolicMapKey<KeySort> = Pair<UHeapRef, UExpr<KeySort>>

fun heapRefEq(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    ref1.uctx.mkHeapRefEq(ref1, ref2)

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpSymbolic(ref1: UHeapRef, ref2: UHeapRef): UBoolExpr =
    error("Heap references should not be compared!")

@Suppress("UNUSED_PARAMETER")
fun heapRefCmpConcrete(ref1: UHeapRef, ref2: UHeapRef): Boolean =
    error("Heap references should not be compared!")

fun indexEq(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkEq(idx1, idx2)

fun indexLeSymbolic(idx1: USizeExpr, idx2: USizeExpr): UBoolExpr =
    idx1.ctx.mkBvSignedLessOrEqualExpr(idx1, idx2)

fun indexLeConcrete(idx1: USizeExpr, idx2: USizeExpr): Boolean =
    // TODO: to optimize things up, we could pass path constraints here and lookup the numeric bounds for idx1 and idx2
    idx1 == idx2 || (idx1 is UConcreteSize && idx2 is UConcreteSize && idx1.numberValue <= idx2.numberValue)

fun refIndexEq(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    return@with (idx1.first eq idx2.first) and indexEq(idx1.second, idx2.second)
}

fun refIndexCmpSymbolic(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): UBoolExpr = with(idx1.first.ctx) {
    return@with (idx1.first eq idx2.first) and indexLeSymbolic(idx1.second, idx2.second)
}

fun refIndexCmpConcrete(idx1: USymbolicArrayIndex, idx2: USymbolicArrayIndex): Boolean =
    idx1.first == idx2.first && indexLeConcrete(idx1.second, idx2.second)

// TODO: change it to intervals region
typealias UArrayIndexRegion = SetRegion<UIndexType>
typealias UInputArrayIndexRegion = ProductRegion<SetRegion<UConcreteHeapRef>, SetRegion<UIndexType>>

fun indexRegion(idx: USizeExpr): UArrayIndexRegion =
    when (idx) {
        is UConcreteSize -> SetRegion.singleton(idx.numberValue)
        else -> SetRegion.universe()
    }

fun refRegion(address: UHeapRef): SetRegion<UConcreteHeapRef> =
    when (address) {
        is UConcreteHeapRef -> SetRegion.singleton(address)
        else -> SetRegion.universe()
    }

fun inputArrayRegion(address: UHeapRef, idx: USizeExpr): UInputArrayIndexRegion =
    ProductRegion(refRegion(address), indexRegion(idx))

fun indexRangeRegion(idx1: USizeExpr, idx2: USizeExpr): UArrayIndexRegion =
    when (idx1) {
        is UConcreteSize ->
            when (idx2) {
                is UConcreteSize -> SetRegion.ofSequence((idx1.numberValue..idx2.numberValue).asSequence())
                else -> SetRegion.universe()
            }

        else -> SetRegion.universe()
    }

fun inputArrayRangeRegion(
    ref1: UHeapRef,
    idx1: USizeExpr,
    ref2: UHeapRef,
    idx2: USizeExpr,
): ProductRegion<SetRegion<UConcreteHeapRef>, SetRegion<UIndexType>> {
    val refRegion1 = refRegion(ref1)
    val refRegion2 = refRegion(ref2)
    require(refRegion1 == refRegion2)
    return ProductRegion(refRegion1, indexRangeRegion(idx1, idx2))
}

fun refIndexRegion(idx: USymbolicArrayIndex): UInputArrayIndexRegion = inputArrayRegion(idx.first, idx.second)
fun indexUniverseRangeRegion(): UArrayIndexRegion = SetRegion.universe()
fun indexEmptyRangeRegion(): UArrayIndexRegion = SetRegion.empty()

fun refIndexRangeRegion(
    idx1: USymbolicArrayIndex,
    idx2: USymbolicArrayIndex,
): UInputArrayIndexRegion = inputArrayRangeRegion(idx1.first, idx1.second, idx2.first, idx2.second)

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyRegion(
    descriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key: USymbolicMapKey<KeySort>
): Reg = descriptor.mkKeyRegion(key.second)

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyRangeRegion(
    descriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): Reg = descriptor.mkKeyRangeRegion(key1.second, key2.second)

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyEq(
    descriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): UBoolExpr = with(key1.first.ctx) {
    (key1.first eq key2.first) and descriptor.keyEqSymbolic(key1.second, key2.second)
}

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyCmpSymbolic(
    keyDescriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): UBoolExpr = with(key1.first.ctx) {
    (key1.first eq key2.first) and keyDescriptor.keyCmpSymbolic(key1.second, key2.second)
}

fun <KeySort : USort, Reg : Region<Reg>> symbolicMapRefKeyCmpConcrete(
    keyDescriptor: USymbolicMapDescriptor<KeySort, *, Reg>,
    key1: USymbolicMapKey<KeySort>,
    key2: USymbolicMapKey<KeySort>
): Boolean = (key1.first == key2.first) && keyDescriptor.keyCmpConcrete(key1.second, key2.second)


typealias UInputFieldRegion<Field, Sort> = USymbolicMemoryRegion<UInputFieldId<Field, Sort>, UHeapRef, Sort>
typealias UAllocatedArrayRegion<ArrayType, Sort> = USymbolicMemoryRegion<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>
typealias UInputArrayRegion<ArrayType, Sort> = USymbolicMemoryRegion<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>
typealias UInputArrayLengthRegion<ArrayType> = USymbolicMemoryRegion<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>
typealias UAllocatedSymbolicMapRegion<KeySort, Reg, Sort> = USymbolicMemoryRegion<UAllocatedSymbolicMapId<KeySort, Reg, Sort>, UExpr<KeySort>, Sort>
typealias UInputSymbolicMapRegion<KeySort, Reg, Sort> = USymbolicMemoryRegion<UInputSymbolicMapId<KeySort, Reg, Sort>, USymbolicMapKey<KeySort>, Sort>
typealias UInputSymbolicMapLengthRegion = USymbolicMemoryRegion<UInputSymbolicMapLengthId, UHeapRef, USizeSort>

typealias KeyMapper<Key> = (Key) -> Key

val <Field, Sort : USort> UInputFieldRegion<Field, Sort>.field
    get() = regionId.field

val <ArrayType, Sort : USort> UAllocatedArrayRegion<ArrayType, Sort>.allocatedArrayType
    get() = regionId.arrayType
val <ArrayType, Sort : USort> UAllocatedArrayRegion<ArrayType, Sort>.allocatedAddress
    get() = regionId.address

val <ArrayType, Sort : USort> UInputArrayRegion<ArrayType, Sort>.inputArrayType
    get() = regionId.arrayType

val <ArrayType> UInputArrayLengthRegion<ArrayType>.inputLengthArrayType
    get() = regionId.arrayType

fun <Field, Sort : USort> emptyInputFieldRegion(
    field: Field,
    sort: Sort,
): UInputFieldRegion<Field, Sort> =
    USymbolicMemoryRegion(
        UInputFieldId(field, sort, contextHeap = null),
        UFlatUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic)
    )

fun <ArrayType, Sort : USort> emptyAllocatedArrayRegion(
    arrayType: ArrayType,
    address: UConcreteHeapAddress,
    sort: Sort,
): UAllocatedArrayRegion<ArrayType, Sort> {
    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::indexRegion, ::indexRangeRegion, ::indexEq, ::indexLeConcrete, ::indexLeSymbolic
    )
    return createAllocatedArrayRegion(arrayType, sort, address, updates)
}

fun <ArrayType, Sort : USort> initializedAllocatedArrayRegion(
    arrayType: ArrayType,
    address: UConcreteHeapAddress,
    sort: Sort,
    content: Map<USizeExpr, UExpr<Sort>>,
    guard: UBoolExpr
): UAllocatedArrayRegion<ArrayType, Sort> {
    val emptyRegionTree = emptyRegionTree<UArrayIndexRegion, UUpdateNode<USizeExpr, Sort>>()

    val entries = content.entries.associate { (key, value) ->
        val region = indexRegion(key)
        val update = UPinpointUpdateNode(key, value, ::indexEq, guard)
        region to (update to emptyRegionTree)
    }

    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(
        updates = RegionTree(entries.toPersistentMap()),
        ::indexRegion, ::indexRangeRegion, ::indexEq, ::indexLeConcrete, ::indexLeSymbolic
    )

    return createAllocatedArrayRegion(arrayType, sort, address, updates)
}

private fun <ArrayType, Sort : USort> createAllocatedArrayRegion(
    arrayType: ArrayType,
    sort: Sort,
    address: UConcreteHeapAddress,
    updates: UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>
): USymbolicMemoryRegion<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
    // sampleUValue here is important
    val regionId = UAllocatedArrayId(arrayType, sort, sort.sampleUValue(), address, contextHeap = null)
    return USymbolicMemoryRegion(regionId, updates)
}

fun <ArrayType, Sort : USort> emptyInputArrayRegion(
    arrayType: ArrayType,
    sort: Sort,
): UInputArrayRegion<ArrayType, Sort> {
    val updates = UTreeUpdates<USymbolicArrayIndex, UInputArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::refIndexRegion, ::refIndexRangeRegion, ::refIndexEq, ::refIndexCmpConcrete, ::refIndexCmpSymbolic
    )
    return USymbolicMemoryRegion(UInputArrayId(arrayType, sort, contextHeap = null), updates)
}

fun <ArrayType> emptyInputArrayLengthRegion(
    arrayType: ArrayType,
    sizeSort: USizeSort,
): UInputArrayLengthRegion<ArrayType> =
    USymbolicMemoryRegion(
        UInputArrayLengthId(arrayType, sizeSort, contextHeap = null),
        UFlatUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
    )

fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> emptyAllocatedSymbolicMapRegion(
    descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
    address: UConcreteHeapAddress,
): UAllocatedSymbolicMapRegion<KeySort, Reg, Sort> {
    val updates = UTreeUpdates<UExpr<KeySort>, Reg, Sort>(
        updates = emptyRegionTree(),
        keyToRegion = { descriptor.mkKeyRegion(it) },
        keyRangeToRegion = { k1, k2 -> descriptor.mkKeyRangeRegion(k1, k2) },
        symbolicEq = { k1, k2 -> descriptor.keyEqSymbolic(k1, k2) },
        concreteCmp = { k1, k2 -> descriptor.keyCmpConcrete(k1, k2) },
        symbolicCmp = { k1, k2 -> descriptor.keyCmpSymbolic(k1, k2) },
    )
    val regionId = UAllocatedSymbolicMapId(descriptor, descriptor.defaultValue, address, contextHeap = null)
    return USymbolicMemoryRegion(regionId, updates)
}

fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> emptyInputSymbolicMapRegion(
    descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
): UInputSymbolicMapRegion<KeySort, Reg, Sort> {
    val updates = UTreeUpdates<USymbolicMapKey<KeySort>, Reg, Sort>(
        updates = emptyRegionTree(),
        keyToRegion = { symbolicMapRefKeyRegion(descriptor, it) },
        keyRangeToRegion = { k1, k2 -> symbolicMapRefKeyRangeRegion(descriptor, k1, k2) },
        symbolicEq = { k1, k2 -> symbolicMapRefKeyEq(descriptor, k1, k2) },
        concreteCmp = { k1, k2 -> symbolicMapRefKeyCmpConcrete(descriptor, k1, k2) },
        symbolicCmp = { k1, k2 -> symbolicMapRefKeyCmpSymbolic(descriptor, k1, k2) },
    )
    val regionId = UInputSymbolicMapId(descriptor, contextHeap = null)
    return USymbolicMemoryRegion(regionId, updates)
}

fun emptyInputSymbolicMapLengthRegion(
    descriptor: USymbolicMapDescriptor<*, *, *>,
    sizeSort: USizeSort,
): UInputSymbolicMapLengthRegion =
    USymbolicMemoryRegion(
        UInputSymbolicMapLengthId(descriptor, sizeSort, contextHeap = null),
        UFlatUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
    )

//endregion
