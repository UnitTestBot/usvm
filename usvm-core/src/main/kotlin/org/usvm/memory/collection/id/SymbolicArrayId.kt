package org.usvm.memory.collection.id

import io.ksmt.cache.hash
import io.ksmt.utils.sampleValue
import kotlinx.collections.immutable.toPersistentMap
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.isTrue
import org.usvm.memory.collection.region.UArrayIndexRef
import org.usvm.memory.collection.region.UArrayLengthRef
import org.usvm.memory.ULValue
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.UFlatUpdates
import org.usvm.memory.collection.key.UHeapRefKeyInfo
import org.usvm.memory.collection.key.USizeExprKeyInfo
import org.usvm.memory.collection.key.USizeRegion
import org.usvm.memory.collection.key.USymbolicArrayIndex
import org.usvm.memory.collection.key.USymbolicArrayIndexKeyInfo
import org.usvm.memory.collection.key.USymbolicArrayIndexRegion
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.UTreeUpdates
import org.usvm.uctx
import org.usvm.util.RegionTree
import org.usvm.util.emptyRegionTree


interface USymbolicArrayId<ArrayType, Key, Sort : USort, out ArrayId : USymbolicArrayId<ArrayType, Key, Sort, ArrayId>> :
    USymbolicCollectionId<Key, Sort, ArrayId> {
    val arrayType: ArrayType
}

/**
 * A collection id for a collection storing arrays allocated during execution.
 * Each identifier contains information about its [arrayType] and [address].
 */
class UAllocatedArrayId<ArrayType, Sort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
    override val defaultValue: UExpr<Sort>,
    val address: UConcreteHeapAddress,
    contextMemory: UWritableMemory<*>?,
) : USymbolicCollectionIdWithContextMemory<USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>>(contextMemory),
    USymbolicArrayId<ArrayType, USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>> {

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): UExpr<Sort> = mkAllocatedArrayReading(collection, key)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): ULValue<*, Sort> = UArrayIndexRef(sort, mkConcreteHeapRef(address), key, arrayType)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USizeExpr,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) {
        val ref = key.uctx.mkConcreteHeapRef(address)
        memory.write(UArrayIndexRef(sort, ref, key, arrayType), value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<USizeExpr> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UAllocatedArrayId<ArrayType, Sort> {
        val composedDefaultValue = composer.compose(defaultValue)
        check(contextMemory == null) { "contextHeap is not null in composition" }
        return UAllocatedArrayId(arrayType, sort, composedDefaultValue, address, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = USizeExprKeyInfo

    override fun rebindKey(key: USizeExpr): DecomposedKey<*, Sort>? =
        null

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyArray(): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
        val updates = UTreeUpdates<USizeExpr, USizeRegion, Sort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    fun initializedArray(
        content: Map<USizeExpr, UExpr<Sort>>,
        guard: UBoolExpr
    ): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
        val emptyRegionTree = emptyRegionTree<USizeRegion, UUpdateNode<USizeExpr, Sort>>()

        val entries = content.entries.associate { (key, value) ->
            val region = USizeExprKeyInfo.keyToRegion(key)
            val update = UPinpointUpdateNode(key, keyInfo(), value, guard)
            region to (update to emptyRegionTree)
        }

        val updates = UTreeUpdates(
            updates = RegionTree(entries.toPersistentMap()),
            keyInfo()
        )

        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedArrayId<*, *>

        if (address != other.address) return false
        if (arrayType != other.arrayType) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = address

    override fun toString(): String = "allocatedArray<$arrayType>($address)"
}

/**
 * A collection id for a collection storing arrays retrieved as a symbolic value, contains only its [arrayType].
 */
class UInputArrayId<ArrayType, Sort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
    contextMemory: UWritableMemory<*>?,
) : USymbolicCollectionIdWithContextMemory<USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>>(contextMemory),
    USymbolicArrayId<ArrayType, USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>> {
    override val defaultValue: UExpr<Sort>? get() = null

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): UExpr<Sort> = mkInputArrayReading(collection, key.first, key.second)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): ULValue<*, Sort> = UArrayIndexRef(sort, key.first, key.second, arrayType)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicArrayIndex,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    ) = memory.write(UArrayIndexRef(sort, key.first, key.second, arrayType), value, guard)

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<USymbolicArrayIndex> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyRegion(): USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort> {
        val updates = UTreeUpdates<USymbolicArrayIndex, USymbolicArrayIndexRegion, Sort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun <Type> map(composer: UComposer<Type>): UInputArrayId<ArrayType, Sort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return UInputArrayId(arrayType, sort, composer.memory.toWritableMemory())
    }

    override fun keyInfo(): USymbolicArrayIndexKeyInfo =
        USymbolicArrayIndexKeyInfo

    override fun rebindKey(key: USymbolicArrayIndex): DecomposedKey<*, Sort>? {
        val heapRef = key.first
        return when (heapRef) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedArrayId(
                    arrayType,
                    sort,
                    sort.sampleValue(),
                    heapRef.address,
                    contextMemory
                ), key.second
            )

            else -> null
        }
    }

    override fun toString(): String = "inputArray<$arrayType>"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputArrayId<*, *>

        if (arrayType != other.arrayType) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = hash(arrayType, sort)
}

/**
 * A collection id for a collection storing array lengths for arrays of a specific [arrayType].
 */
class UInputArrayLengthId<ArrayType> internal constructor(
    val arrayType: ArrayType,
    override val sort: USizeSort,
    contextMemory: UWritableMemory<*>?,
) : USymbolicCollectionIdWithContextMemory<UHeapRef, USizeSort, UInputArrayLengthId<ArrayType>>(contextMemory) {
    override val defaultValue: UExpr<USizeSort>? get() = null

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = mkInputArrayLengthReading(collection, key)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): ULValue<*, USizeSort> = UArrayLengthRef(sort, key, arrayType)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        assert(guard.isTrue)
        memory.write(UArrayLengthRef(sort, key, arrayType), value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputArrayLengthId<ArrayType> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return UInputArrayLengthId(arrayType, sort, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? {
        TODO("Not yet implemented")
    }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyRegion(): USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))


    override fun toString(): String = "length<$arrayType>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputArrayLengthId<*>

        return arrayType == other.arrayType
    }

    override fun hashCode(): Int = arrayType.hashCode()
}
