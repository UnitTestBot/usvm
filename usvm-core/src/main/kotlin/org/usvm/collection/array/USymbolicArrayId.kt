package org.usvm.collection.array

import io.ksmt.cache.hash
import kotlinx.collections.immutable.toPersistentMap
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USizeExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.key.USizeRegion
import org.usvm.memory.DecomposedKey
import org.usvm.memory.KeyTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionIdWithContextMemory
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.sampleUValue
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
    val address: UConcreteHeapAddress,
    val idDefaultValue: UExpr<Sort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>>(contextMemory),
    USymbolicArrayId<ArrayType, USizeExpr, Sort, UAllocatedArrayId<ArrayType, Sort>> {

    val defaultValue: UExpr<Sort> by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort>,
        key: USizeExpr
    ): UExpr<Sort> {
        if (collection.updates.isEmpty()) {
            return defaultValue
        }

        return mkAllocatedArrayReading(collection, key)
    }

    override fun UContext.mkLValue(
        key: USizeExpr
    ): ULValue<*, Sort> = UArrayIndexRef(sort, mkConcreteHeapRef(address), key, arrayType)

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<USizeExpr> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UAllocatedArrayId<ArrayType, Sort> {
        val composedDefaultValue = composer.compose(defaultValue)
        check(contextMemory == null) { "contextHeap is not null in composition" }
        return UAllocatedArrayId(arrayType, sort, address, composedDefaultValue, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = USizeExprKeyInfo

    override fun rebindKey(key: USizeExpr): DecomposedKey<*, Sort>? =
        null

    override fun emptyRegion(): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort>, USizeExpr, Sort> {
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
    private val defaultValue: UExpr<Sort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>>(contextMemory),
    USymbolicArrayId<ArrayType, USymbolicArrayIndex, Sort, UInputArrayId<ArrayType, Sort>> {

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort>,
        key: USymbolicArrayIndex
    ): UExpr<Sort> = mkInputArrayReading(collection, key.first, key.second)

    override fun UContext.mkLValue(
        key: USymbolicArrayIndex
    ): ULValue<*, Sort> = UArrayIndexRef(sort, key.first, key.second, arrayType)

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<USymbolicArrayIndex> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun emptyRegion(): USymbolicCollection<UInputArrayId<ArrayType, Sort>, USymbolicArrayIndex, Sort> {
        val updates = UTreeUpdates<USymbolicArrayIndex, USymbolicArrayIndexRegion, Sort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun <Type> map(composer: UComposer<Type>): UInputArrayId<ArrayType, Sort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefault = composer.compose(sort.sampleUValue())
        return UInputArrayId(arrayType, sort, composedDefault, composer.memory.toWritableMemory())
    }

    override fun keyInfo(): USymbolicArrayIndexKeyInfo =
        USymbolicArrayIndexKeyInfo

    override fun rebindKey(key: USymbolicArrayIndex): DecomposedKey<*, Sort>? =
        when (val heapRef = key.first) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedArrayId(
                    arrayType,
                    sort,
                    heapRef.address,
                    defaultValue,
                    contextMemory
                ), key.second
            )

            else -> null
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
