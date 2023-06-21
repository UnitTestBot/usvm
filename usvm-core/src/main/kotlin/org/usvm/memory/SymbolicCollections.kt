package org.usvm.memory

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.toPersistentMap
import org.usvm.*
import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.SetRegion
import org.usvm.util.emptyRegionTree

//region Implementation of symbolic collection

/**
 * A uniform unbounded slice of memory. Indexed by [Key], stores symbolic values.
 *
 * @property collectionId describes the source of the collection. Symbolic collections with the same [collectionId] represent the same
 * memory area, but in different states.
 *
 * @property defaultValue describes the initial values for the collections. If [defaultValue] equals `null` then this collection
 * is filled with symbolics.
 */
data class USymbolicCollection<out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>, Key, Sort : USort>(
    val collectionId: CollectionId,
    val updates: USymbolicCollectionUpdates<Key, Sort>,
) : UMemoryRegion<Key, Sort> {
    // to save memory usage
    val sort: Sort get() = collectionId.sort

    // If we replace it with get(), we have to check for nullability in read function.
    val defaultValue = collectionId.defaultValue

    private fun read(key: Key, updates: USymbolicCollectionUpdates<Key, Sort>): UExpr<Sort> {
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

    override fun write(key: Key, value: UExpr<Sort>, guard: UBoolExpr): USymbolicCollection<CollectionId, Key, Sort> {
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
        // TODO: either check in USymbolicCollection constructor that we do not construct a symbolic collection with
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
     * Maps the collection using [composer].
     * It is used in [UComposer] for composition operation.
     *
     * Note: after this operation a collection returned as a result might be in `broken` state:
     * it might have both symbolic and concrete values as keys in it.
     */
    fun <Field, Type> map(
        composer: UComposer<Field, Type>,
    ): USymbolicCollection<CollectionId, Key, Sort> {
        // Map the updates and the collectionId
        val mappedCollectionId = collectionId.map(composer)
        val mappedUpdates = updates.map(collectionId.keyMapper(composer), composer)

        if (mappedUpdates === updates && mappedCollectionId === collectionId) {
            return this
        }

        return USymbolicCollection(mappedCollectionId, mappedUpdates)
    }

    @Suppress("UNCHECKED_CAST")
    fun <Field, Type> applyTo(heap: USymbolicHeap<Field, Type>) {
        // Apply each update on the copy
        updates.forEach {
            when (it) {
                is UPinpointUpdateNode<Key, Sort> -> collectionId.write(heap, it.key, it.value, it.guard)
                is URangedUpdateNode<*, *, Key, Sort> -> {
                    it.sourceCollection.applyTo(heap)

                    val (srcFromRef, srcFromIdx) = it.keyConverter.srcSymbolicArrayIndex
                    val (dstFromRef, dstFromIdx) = it.keyConverter.dstFromSymbolicArrayIndex
                    val dstToIdx = it.keyConverter.dstToIndex

                    val collectionId = it.sourceCollection.collectionId
                    when (collectionId) {
                        is UTypedArrayId<*, *, *, *> -> {
                            val arrayType = collectionId.arrayType as Type
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
                            val descriptor = collectionId.descriptor
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

    private fun <CollectionId : USymbolicMapId<SrcKey, KeySort, Reg, Sort, CollectionId>,
            SrcKey, KeySort : USort, Reg : Region<Reg>> applyMergeNodeToHeap(
        mergeNode: UMergeUpdateNode<CollectionId, SrcKey, Key, KeySort, Reg, Sort>,
        heap: USymbolicHeap<*, *>
    ) {
        mergeNode.sourceCollection.applyTo(heap)

        val keyIncludesCheck = mergeNode.keyIncludesCheck
        keyIncludesCheck.collection.applyTo(heap)
        val keyContainsDescriptor = keyIncludesCheck.collection.collectionId.descriptor

        val collectionId = mergeNode.sourceCollection.collectionId
        val srcRef = mergeNode.keyConverter.srcRef
        val dstRef = mergeNode.keyConverter.dstRef

        heap.mergeSymbolicMap(
            collectionId.descriptor,
            keyContainsDescriptor.uncheckedCast(),
            srcRef,
            dstRef,
            mergeNode.guard
        )
    }

    /**
     * @return Symbolic collection which obtained from this one by overwriting the range of addresses [[fromKey] : [toKey]]
     * with values from collection [fromCollection] read from range
     * of addresses [[keyConverter].convert([fromKey]) : [keyConverter].convert([toKey])]
     */
    fun <OtherCollectionId : UArrayId<SrcKey, Sort, OtherCollectionId>, SrcKey> copyRange(
        fromCollection: USymbolicCollection<OtherCollectionId, SrcKey, Sort>,
        fromKey: Key,
        toKey: Key,
        keyConverter: UMemoryKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): USymbolicCollection<CollectionId, Key, Sort> {
        val updatesCopy = updates.copyRange(fromCollection, fromKey, toKey, keyConverter, guard)
        return this.copy(updates = updatesCopy)
    }

    fun <OtherCollectionId : USymbolicMapId<SrcKey, KeySort, Reg, Sort, OtherCollectionId>,
            SrcKey, KeySort : USort, Reg : Region<Reg>> mergeWithCollection(
        fromCollection: USymbolicCollection<OtherCollectionId, SrcKey, Sort>,
        keyIncludesCheck: UMergeKeyIncludesCheck<SrcKey, KeySort, *>,
        keyConverter: UMergeKeyConverter<SrcKey, Key>,
        guard: UBoolExpr
    ): USymbolicCollection<CollectionId, Key, Sort> {
        val updatesCopy = updates.mergeWithCollection(fromCollection, keyIncludesCheck, keyConverter, guard)
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

fun indexFullRangeRegion(): UArrayIndexRegion = SetRegion.universe()

fun refIndexRegion(idx: USymbolicArrayIndex): UArrayIndexRegion = indexRegion(idx.second)
fun refIndexRangeRegion(
    idx1: USymbolicArrayIndex,
    idx2: USymbolicArrayIndex,
): UArrayIndexRegion = indexRangeRegion(idx1.second, idx2.second)

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


typealias UInputFieldCollection<Field, Sort> = USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>
typealias UAllocatedArrayCollection<ArrayType, Sort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>
typealias UInputArrayCollection<ArrayType, Sort> = USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>
typealias UInputArrayLengthCollection<ArrayType> = USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>
typealias UAllocatedSymbolicMap<KeySort, Reg, Sort> = USymbolicCollection<UAllocatedSymbolicMapId<KeySort, Reg, Sort>, UExpr<KeySort>, Sort>
typealias UInputSymbolicMap<KeySort, Reg, Sort> = USymbolicCollection<UInputSymbolicMapId<KeySort, Reg, Sort>, USymbolicMapKey<KeySort>, Sort>
typealias UInputSymbolicMapLengthCollection = USymbolicCollection<UInputSymbolicMapLengthId, UHeapRef, USizeSort>

typealias KeyMapper<Key> = (Key) -> Key

val <Field, Sort : USort> UInputFieldCollection<Field, Sort>.field
    get() = collectionId.field

val <ArrayType, Sort : USort> UAllocatedArrayCollection<ArrayType, Sort>.allocatedArrayType
    get() = collectionId.arrayType
val <ArrayType, Sort : USort> UAllocatedArrayCollection<ArrayType, Sort>.allocatedAddress
    get() = collectionId.address

val <ArrayType, Sort : USort> UInputArrayCollection<ArrayType, Sort>.inputArrayType
    get() = collectionId.arrayType

val <ArrayType> UInputArrayLengthCollection<ArrayType>.inputLengthArrayType
    get() = collectionId.arrayType

fun <Field, Sort : USort> emptyInputFieldCollection(
    field: Field,
    sort: Sort,
): UInputFieldCollection<Field, Sort> =
    USymbolicCollection(
        UInputFieldId(field, sort, contextHeap = null),
        UFlatUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic)
    )

fun <ArrayType, Sort : USort> emptyAllocatedArrayCollection(
    arrayType: ArrayType,
    address: UConcreteHeapAddress,
    sort: Sort,
): UAllocatedArrayCollection<ArrayType, Sort> {
    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::indexRegion, ::indexRangeRegion, ::indexFullRangeRegion, ::indexEq, ::indexLeConcrete, ::indexLeSymbolic
    )
    return createAllocatedArrayCollection(arrayType, sort, address, updates)
}

fun <ArrayType, Sort : USort> initializedAllocatedArrayCollection(
    arrayType: ArrayType,
    address: UConcreteHeapAddress,
    sort: Sort,
    content: Map<USizeExpr, UExpr<Sort>>,
    guard: UBoolExpr
): UAllocatedArrayCollection<ArrayType, Sort> {
    val emptyRegionTree = emptyRegionTree<UUpdateNode<USizeExpr, Sort>, UArrayIndexRegion>()

    val entries = content.entries.associate { (key, value) ->
        val region = indexRegion(key)
        val update = UPinpointUpdateNode(key, value, ::indexEq, guard)
        region to (update to emptyRegionTree)
    }

    val updates = UTreeUpdates(
        updates = RegionTree(entries.toPersistentMap()),
        ::indexRegion, ::indexRangeRegion, ::indexFullRangeRegion, ::indexEq, ::indexLeConcrete, ::indexLeSymbolic
    )

    return createAllocatedArrayCollection(arrayType, sort, address, updates)
}

private fun <ArrayType, Sort : USort> createAllocatedArrayCollection(
    arrayType: ArrayType,
    sort: Sort,
    address: UConcreteHeapAddress,
    updates: UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>
): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
    // sampleUValue here is important
    val collectionId = UAllocatedArrayId(arrayType, sort, sort.sampleUValue(), address, contextHeap = null)
    return USymbolicCollection(collectionId, updates)
}

fun <ArrayType, Sort : USort> emptyInputArrayCollection(
    arrayType: ArrayType,
    sort: Sort,
): UInputArrayCollection<ArrayType, Sort> {
    val updates = UTreeUpdates<USymbolicArrayIndex, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::refIndexRegion, ::refIndexRangeRegion, ::indexFullRangeRegion, ::refIndexEq, ::refIndexCmpConcrete, ::refIndexCmpSymbolic
    )
    return USymbolicCollection(UInputArrayId(arrayType, sort, contextHeap = null), updates)
}

fun <ArrayType> emptyInputArrayLengthCollection(
    arrayType: ArrayType,
    sizeSort: USizeSort,
): UInputArrayLengthCollection<ArrayType> =
    USymbolicCollection(
        UInputArrayLengthId(arrayType, sizeSort, contextHeap = null),
        UFlatUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
    )

fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> emptyAllocatedSymbolicMap(
    descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
    address: UConcreteHeapAddress,
): UAllocatedSymbolicMap<KeySort, Reg, Sort> {
    val updates = UTreeUpdates<UExpr<KeySort>, Reg, Sort>(
        updates = emptyRegionTree(),
        keyToRegion = { descriptor.mkKeyRegion(it) },
        keyRangeToRegion = { k1, k2 -> descriptor.mkKeyRangeRegion(k1, k2) },
        fullRangeRegion = { descriptor.mkKeyFullRangeRegion() },
        symbolicEq = { k1, k2 -> descriptor.keyEqSymbolic(k1, k2) },
        concreteCmp = { k1, k2 -> descriptor.keyCmpConcrete(k1, k2) },
        symbolicCmp = { k1, k2 -> descriptor.keyCmpSymbolic(k1, k2) },
    )
    val collectionId = UAllocatedSymbolicMapId(descriptor, descriptor.defaultValue, address, contextHeap = null)
    return USymbolicCollection(collectionId, updates)
}

fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> emptyInputSymbolicMapCollection(
    descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
): UInputSymbolicMap<KeySort, Reg, Sort> {
    val updates = UTreeUpdates<USymbolicMapKey<KeySort>, Reg, Sort>(
        updates = emptyRegionTree(),
        keyToRegion = { symbolicMapRefKeyRegion(descriptor, it) },
        keyRangeToRegion = { k1, k2 -> symbolicMapRefKeyRangeRegion(descriptor, k1, k2) },
        fullRangeRegion = { descriptor.mkKeyFullRangeRegion() },
        symbolicEq = { k1, k2 -> symbolicMapRefKeyEq(descriptor, k1, k2) },
        concreteCmp = { k1, k2 -> symbolicMapRefKeyCmpConcrete(descriptor, k1, k2) },
        symbolicCmp = { k1, k2 -> symbolicMapRefKeyCmpSymbolic(descriptor, k1, k2) },
    )
    val collectionId = UInputSymbolicMapId(descriptor, contextHeap = null)
    return USymbolicCollection(collectionId, updates)
}

fun emptyInputSymbolicMapLengthCollection(
    descriptor: USymbolicMapDescriptor<*, *, *>,
    sizeSort: USizeSort,
): UInputSymbolicMapLengthCollection =
    USymbolicCollection(
        UInputSymbolicMapLengthId(descriptor, sizeSort, contextHeap = null),
        UFlatUpdates(::heapRefEq, ::heapRefCmpConcrete, ::heapRefCmpSymbolic),
    )

//endregion
