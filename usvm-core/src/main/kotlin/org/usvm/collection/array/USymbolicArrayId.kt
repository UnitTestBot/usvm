package org.usvm.collection.array

import io.ksmt.cache.hash
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.adapters.ImmutableListAdapter
import kotlinx.collections.immutable.adapters.ImmutableSetAdapter
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.compose
import org.usvm.memory.UPinpointUpdateNode
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UUpdateNode
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.USizeRegion
import org.usvm.regions.RegionTree
import org.usvm.regions.emptyRegionTree
import org.usvm.sampleUValue
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.mkSizeExpr
import org.usvm.uctx
import org.usvm.withSizeSort


interface USymbolicArrayId<ArrayType, Key, Sort : USort, out ArrayId : USymbolicArrayId<ArrayType, Key, Sort, ArrayId>> :
    USymbolicCollectionId<Key, Sort, ArrayId> {
    val arrayType: ArrayType
}

/**
 * A collection id for a collection storing arrays allocated during execution.
 * Each identifier contains information about its [arrayType] and [address].
 */
class UAllocatedArrayId<ArrayType, Sort : USort, USizeSort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
    val address: UConcreteHeapAddress,
) : USymbolicArrayId<ArrayType, UExpr<USizeSort>, Sort, UAllocatedArrayId<ArrayType, Sort, USizeSort>> {
    val defaultValue: UExpr<Sort> by lazy { sort.sampleUValue() }

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort>,
        key: UExpr<USizeSort>,
        composer: UComposer<*, *>?
    ): UExpr<Sort> {
        if (collection.updates.isEmpty()) {
            return composer.compose(defaultValue)
        }

        if (composer == null) {
            return key.uctx.withSizeSort<USizeSort>().mkAllocatedArrayReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UExpr<USizeSort>, value: UExpr<Sort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UExpr<USizeSort>) =
        UArrayIndexLValue(sort, key.uctx.mkConcreteHeapRef(address), key, arrayType)

    override fun keyInfo(): USizeExprKeyInfo<USizeSort> = USizeExprKeyInfo()

    override fun emptyRegion(): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort> {
        val updates = UTreeUpdates<UExpr<USizeSort>, USizeRegion, Sort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    fun initializedArray(
        content: List<UExpr<Sort>>,
        guard: UBoolExpr
    ): USymbolicCollection<UAllocatedArrayId<ArrayType, Sort, USizeSort>, UExpr<USizeSort>, Sort> {
        val ctx = guard.uctx.withSizeSort<USizeSort>()
        val entries = content.mapIndexed { idx, value ->
            UPinpointUpdateNode(ctx.mkSizeExpr(idx), keyInfo(), value, guard)
        }

        val updates = UTreeUpdates(
            updates = RegionTree(UInitializedArrayRegionEntries(entries)),
            keyInfo()
        )

        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedArrayId<*, *, *>

        if (address != other.address) return false
        if (arrayType != other.arrayType) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = address

    override fun toString(): String = "allocatedArray<$arrayType>($address)"
}

private typealias UInitializedArrayRegionValue<USizeSort, Sort> = Pair<UUpdateNode<UExpr<USizeSort>, Sort>, RegionTree<USizeRegion, UUpdateNode<UExpr<USizeSort>, Sort>>>

private class UInitializedArrayRegionEntries<USizeSort : USort, Sort : USort>(
    val data: List<UUpdateNode<UExpr<USizeSort>, Sort>>
) : PersistentMap<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> {
    override val size: Int get() = data.size
    override fun isEmpty(): Boolean = data.isEmpty()

    override val keys: ImmutableSet<USizeRegion> by lazy {
        data.indices.mapTo(hashSetOf()) { USizeRegion.point(it) }
            .let { ImmutableSetAdapter(it) }
    }

    private fun USizeRegion.getPointOrNull(): Int? {
        val points = iterator()
        if (!points.hasNext()) return null
        val lhs = points.next()
        if (!points.hasNext()) return null
        val rhs = points.next()
        if (points.hasNext()) return null
        if (rhs != lhs + 1) return null
        return lhs
    }

    override fun get(key: USizeRegion): UInitializedArrayRegionValue<USizeSort, Sort>? {
        val point = key.getPointOrNull() ?: return null
        val value = data.getOrNull(point) ?: return null
        return value to emptyRegionTree()
    }

    override fun containsKey(key: USizeRegion): Boolean {
        val point = key.getPointOrNull() ?: return false
        return point in data.indices
    }

    override fun containsValue(value: UInitializedArrayRegionValue<USizeSort, Sort>): Boolean {
        if (!value.second.isEmpty) return false
        return data.contains(value.first)
    }

    private data class Entry<USizeSort : USort, Sort : USort>(
        override val key: USizeRegion,
        override val value: UInitializedArrayRegionValue<USizeSort, Sort>
    ) : Map.Entry<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>>

    override val entries: ImmutableSet<Map.Entry<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>>> by lazy {
        data.mapIndexedTo(hashSetOf()) { idx, update ->
            Entry(USizeRegion.point(idx), update to emptyRegionTree())
        }.let { ImmutableSetAdapter(it) }
    }

    override val values: ImmutableCollection<UInitializedArrayRegionValue<USizeSort, Sort>> by lazy {
        ImmutableListAdapter(data.map { it to emptyRegionTree() })
    }

    override fun clear(): PersistentMap<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> = persistentMapOf()

    private val persistentMap: PersistentMap<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> by lazy {
        val map = persistentMapOf<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>>().builder()

        data.forEachIndexed { idx, update ->
            map[USizeRegion.point(idx)] = update to emptyRegionTree()
        }

        map.build()
    }

    override fun builder(): PersistentMap.Builder<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> =
        persistentMap.builder()

    override fun remove(
        key: USizeRegion,
        value: UInitializedArrayRegionValue<USizeSort, Sort>
    ): PersistentMap<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> =
        persistentMap.remove(key, value)

    override fun remove(key: USizeRegion): PersistentMap<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> =
        persistentMap.remove(key)

    override fun putAll(
        m: Map<out USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>>
    ): PersistentMap<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> =
        persistentMap.putAll(m)

    override fun put(
        key: USizeRegion,
        value: UInitializedArrayRegionValue<USizeSort, Sort>
    ): PersistentMap<USizeRegion, UInitializedArrayRegionValue<USizeSort, Sort>> =
        persistentMap.put(key, value)
}

/**
 * A collection id for a collection storing arrays retrieved as a symbolic value, contains only its [arrayType].
 */
class UInputArrayId<ArrayType, Sort : USort, USizeSort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: Sort,
) : USymbolicArrayId<ArrayType, USymbolicArrayIndex<USizeSort>, Sort, UInputArrayId<ArrayType, Sort, USizeSort>> {

    override fun instantiate(
        collection: USymbolicCollection<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort>,
        key: USymbolicArrayIndex<USizeSort>,
        composer: UComposer<*, *>?
    ): UExpr<Sort> {
        if (composer == null) {
            return sort.uctx.withSizeSort<USizeSort>().mkInputArrayReading(collection, key.first, key.second)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicArrayIndex<USizeSort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: USymbolicArrayIndex<USizeSort>) =
        UArrayIndexLValue(sort, key.first, key.second, arrayType)

    override fun emptyRegion(): USymbolicCollection<UInputArrayId<ArrayType, Sort, USizeSort>, USymbolicArrayIndex<USizeSort>, Sort> {
        val updates = UTreeUpdates<USymbolicArrayIndex<USizeSort>, USymbolicArrayIndexRegion, Sort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun keyInfo(): USymbolicArrayIndexKeyInfo<USizeSort> = USymbolicArrayIndexKeyInfo()

    override fun toString(): String = "inputArray<$arrayType>"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputArrayId<*, *, *>

        if (arrayType != other.arrayType) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = hash(arrayType, sort)
}
