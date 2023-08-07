package org.usvm.memory

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.toPersistentMap
import org.usvm.*
import org.usvm.util.Region
import org.usvm.util.RegionTree
import org.usvm.util.emptyRegionTree

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
) {
    // : UMemoryRegion<Key, Sort> {
    // to save memory usage
    val sort: Sort get() = collectionId.sort

    // If we replace it with get(), we have to check for nullability in read function.
    val defaultValue = collectionId.defaultValue

    private fun read(
        key: Key,
        updates: USymbolicCollectionUpdates<Key, Sort>
    ): UExpr<Sort> {
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

    fun read(key: Key): UExpr<Sort> {
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

    fun write(
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ): USymbolicCollection<CollectionId, Key, Sort> {
        assert(value.sort == sort)

        val newUpdates = if (sort == sort.uctx.addressSort) {
            // we must split symbolic and concrete heap refs here,
            // because later in [splittingRead] we check value is UConcreteHeapRef
            var newUpdates = updates

            withHeapRef(
                value.asExpr(sort.uctx.addressSort),
                initialGuard = guard,
                blockOnConcrete = { (ref, guard) ->
                    newUpdates = newUpdates.write(key, ref.asExpr(sort), guard)
                },
                blockOnSymbolic = { (ref, guard) ->
                    newUpdates = newUpdates.write(key, ref.asExpr(sort), guard)
                }
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
     * All updates which after mapping do not touch [targetCollectionId] will be thrown out.
     *
     * Note: after this operation a collection returned as a result might be in `broken` state:
     * it might [UIteExpr] with both symbolic and concrete references as keys in it.
     */
    fun <Field, Type, MappedKey> mapTo(
        composer: UComposer<Field, Type>,
        targetCollectionId: USymbolicCollectionId<MappedKey, Sort, *>
    ): USymbolicCollection<USymbolicCollectionId<MappedKey, Sort, *>, MappedKey, Sort> {
        // Map the updates and the collectionId
        val mapper = collectionId.keyFilterMapper(composer, targetCollectionId)
        val mappedUpdates = updates.filterMap(mapper, composer, targetCollectionId.keyInfo())

        if (mappedUpdates === updates && targetCollectionId === collectionId) {
            @Suppress("UNCHECKED_CAST")
            return this as USymbolicCollection<USymbolicCollectionId<MappedKey, Sort, *>, MappedKey, Sort>
        }

        return USymbolicCollection(targetCollectionId, mappedUpdates)
    }

    @Suppress("UNCHECKED_CAST")
    fun <Field, Type> applyTo(heap: UHeap<Field, Type>) {
        // Apply each update on the copy
        updates.forEach {
            when (it) {
                is UPinpointUpdateNode<Key, Sort> -> collectionId.write(heap, it.key, it.value, it.guard)
                is URangedUpdateNode<*, *, Key, Sort> -> {
                    it.sourceCollection.applyTo(heap)

                    val (srcFromRef, srcFromIdx) = it.adapter.srcSymbolicArrayIndex
                    val (dstFromRef, dstFromIdx) = it.adapter.dstFromSymbolicArrayIndex
                    val dstToIdx = it.adapter.dstToIndex

                    val collectionId = it.sourceCollection.collectionId
//                    when (collectionId) {
//                        is UTypedArrayId<*, *, *, *> -> {
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

//                        is USymbolicMapId<*, *, *, *, *> -> {
//                            val descriptor = collectionId.descriptor
//                            heap.copySymbolicMapIndexRange(
//                                descriptor as USymbolicMapDescriptor<USizeSort, Sort, *>,
//                                srcFromRef,
//                                dstFromRef,
//                                srcFromIdx,
//                                dstFromIdx,
//                                dstToIdx,
//                                it.guard
//                            )
//                        }
//                    }
                }

//                is UMergeUpdateNode<*, *, Key, *, *, Sort> -> {
//                    applyMergeNodeToHeap(it, heap)
//                }
//            }
        }
    }

//    private fun <CollectionId : USymbolicMapId<SrcKey, KeySort, Reg, Sort, CollectionId>,
//            SrcKey, KeySort : USort, Reg : Region<Reg>> applyMergeNodeToHeap(
//        mergeNode: UMergeUpdateNode<CollectionId, SrcKey, Key, KeySort, Reg, Sort>,
//        heap: UHeap<*, *>
//    ) {
//        mergeNode.sourceCollection.applyTo(heap)
//
//        val keyIncludesCheck = mergeNode.keyIncludesCheck
//        keyIncludesCheck.collection.applyTo(heap)
//        val keyContainsDescriptor = keyIncludesCheck.collection.collectionId.descriptor
//
//        val collectionId = mergeNode.sourceCollection.collectionId
//        val srcRef = mergeNode.keyConverter.srcRef
//        val dstRef = mergeNode.keyConverter.dstRef
//
//        heap.mergeSymbolicMap(
//            collectionId.descriptor,
//            keyContainsDescriptor.uncheckedCast(),
//            srcRef,
//            dstRef,
//            mergeNode.guard
//        )
//    }

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

typealias UInputFieldCollection<Field, Sort> = USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>
typealias UAllocatedArrayCollection<ArrayType, Sort> = USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>
typealias UInputArrayCollection<ArrayType, Sort> = USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>
typealias UInputArrayLengthCollection<ArrayType> = USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>
typealias UAllocatedSymbolicMap<KeySort, Sort> = USymbolicCollection<UAllocatedSymbolicMapId<KeySort, Sort>, UExpr<KeySort>, Sort>
typealias UInputSymbolicMap<KeySort, Reg, Sort> = USymbolicCollection<UInputSymbolicMapId<KeySort, Reg, Sort>, USymbolicMapKey<KeySort>, Sort>
typealias UInputSymbolicMapLengthCollection = USymbolicCollection<UInputSymbolicMapLengthId, UHeapRef, USizeSort>

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
        UFlatUpdates()
    )

fun <ArrayType, Sort : USort> emptyAllocatedArrayCollection(
    arrayType: ArrayType,
    address: UConcreteHeapAddress,
    sort: Sort,
): UAllocatedArrayCollection<ArrayType, Sort> {
    val updates = UTreeUpdates<USizeExpr, UArrayIndexRegion, Sort>(
        updates = emptyRegionTree(),
        ::indexRegion, ::indexRangeRegion, ::indexFullRangeRegion
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
        val update = UPinpointUpdateNode(key, value, guard)
        region to (update to emptyRegionTree)
    }

    val updates = UTreeUpdates(
        updates = RegionTree(entries.toPersistentMap()),
        ::indexRegion, ::indexRangeRegion, ::indexFullRangeRegion
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
        ::refIndexRegion,
        ::refIndexRangeRegion,
        ::indexFullRangeRegion,
    )
    return USymbolicCollection(UInputArrayId(arrayType, sort, contextHeap = null), updates)
}

fun <ArrayType> emptyInputArrayLengthCollection(
    arrayType: ArrayType,
    sizeSort: USizeSort,
): UInputArrayLengthCollection<ArrayType> =
    USymbolicCollection(
        UInputArrayLengthId(arrayType, sizeSort, contextHeap = null),
        UFlatUpdates(),
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
    )
    val collectionId = UAllocatedSymbolicMapId(descriptor, descriptor.defaultValue, address, contextHeap = null)
    return USymbolicCollection(collectionId, updates)
}

fun <KeySort : USort, Reg : Region<Reg>, Sort : USort> emptyInputSymbolicMapCollection(
    descriptor: USymbolicMapDescriptor<KeySort, Sort, Reg>,
): UInputSymbolicMap<KeySort, Reg, Sort> {
    val updates = UTreeUpdates<USymbolicMapKey<KeySort>, Reg, Sort>(
        updates = emptyRegionTree(),
//        keyToRegion = { symbolicMapRefKeyRegion(descriptor, it) },
//        keyRangeToRegion = { k1, k2 -> symbolicMapRefKeyRangeRegion(descriptor, k1, k2) },
//        fullRangeRegion = { descriptor.mkKeyFullRangeRegion() },
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
        UFlatUpdates(),
    )


class UFieldRef<Field, Sort : USort>(fieldSort: Sort, val ref: UHeapRef, val field: Field) :
    ULValue<Sort>(fieldSort) {
    override fun <Field, Type, Method> read(memory: UMemoryBase<Field, Type, Method>): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun <Field, Type, Method> write(memory: UMemoryBase<Field, Type, Method>, value: UExpr<Sort>) {
        TODO("Not yet implemented")
    }
}

class UArrayIndexRef<ArrayType, Sort : USort>(
    cellSort: Sort,
    val ref: UHeapRef,
    val index: USizeExpr,
    val arrayType: ArrayType,
) : ULValue<Sort>(cellSort) {

    override fun <Field, Type, Method> read(memory: UMemoryBase<Field, Type, Method>): UExpr<Sort> {
        TODO("Not yet implemented")
    }

    override fun <Field, Type, Method> write(memory: UMemoryBase<Field, Type, Method>, value: UExpr<Sort>) {
        TODO("Not yet implemented")
    }
}
