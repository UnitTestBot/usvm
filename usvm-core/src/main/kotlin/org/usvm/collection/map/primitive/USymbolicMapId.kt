package org.usvm.collection.map.primitive

import io.ksmt.cache.hash
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.map.USymbolicMapKeyInfo
import org.usvm.collection.map.USymbolicMapKeyRegion
import org.usvm.collection.set.primitive.UAllocatedSetId
import org.usvm.collection.set.primitive.UInputSetId
import org.usvm.collection.set.primitive.USymbolicSetId
import org.usvm.compose
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.sampleUValue
import org.usvm.uctx
import org.usvm.regions.Region
import org.usvm.regions.emptyRegionTree

interface USymbolicMapId<
        MapType,
        Key,
        KeySort : USort,
        ValueSort : USort,
        Reg : Region<Reg>,
        out KeysSetId : USymbolicSetId<*, *, Key, *, *, KeysSetId>,
        out MapId : USymbolicMapId<MapType, Key, KeySort, ValueSort, Reg, KeysSetId, MapId>>
    : USymbolicCollectionId<Key, ValueSort, MapId> {
    val keysSetId: KeysSetId
    val mapType: MapType
    val keySort: KeySort
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>
}

class UAllocatedMapId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    override val keySort: KeySort,
    override val sort: ValueSort,
    override val mapType: MapType,
    override val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    val address: UConcreteHeapAddress,
) : USymbolicMapId<MapType, UExpr<KeySort>, KeySort, ValueSort, Reg,
        UAllocatedSetId<MapType, KeySort, Reg>,
        UAllocatedMapId<MapType, KeySort, ValueSort, Reg>> {

    val defaultValue: UExpr<ValueSort> by lazy { sort.sampleUValue() }

    override val keysSetId: UAllocatedSetId<MapType, KeySort, Reg>
        get() = UAllocatedSetId(address, keySort, mapType, keyInfo)

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
        key: UExpr<KeySort>,
        composer: UComposer<*, *>?
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return composer.compose(defaultValue)
        }

        if (composer == null) {
            return key.uctx.mkAllocatedMapReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UExpr<KeySort>) =
        UMapEntryLValue(keySort, sort, key.uctx.mkConcreteHeapRef(address), key, mapType, keyInfo)

    override fun keyInfo(): USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg> = keyInfo

    override fun emptyRegion(): USymbolicCollection<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort> {
        val updates = UTreeUpdates<UExpr<KeySort>, Reg, ValueSort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun toString(): String = "allocatedMap<$mapType>($address)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedMapId<*, *, *, *>

        if (address != other.address) return false
        if (keySort != other.keySort) return false
        if (sort != other.sort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int = hash(address, keySort, sort, mapType)
}

class UInputMapId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    override val keySort: KeySort,
    override val sort: ValueSort,
    override val mapType: MapType,
    override val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
) : USymbolicMapId<MapType, USymbolicMapKey<KeySort>, KeySort, ValueSort, Reg,
        UInputSetId<MapType, KeySort, Reg>,
        UInputMapId<MapType, KeySort, ValueSort, Reg>> {

    override val keysSetId: UInputSetId<MapType, KeySort, Reg>
        get() = UInputSetId(keySort, mapType, keyInfo)

    override fun instantiate(
        collection: USymbolicCollection<UInputMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
        key: USymbolicMapKey<KeySort>,
        composer: UComposer<*, *>?
    ): UExpr<ValueSort> {
        if (composer == null) {
            return sort.uctx.mkInputMapReading(collection, key.first, key.second)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicMapKey<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: USymbolicMapKey<KeySort>) =
        UMapEntryLValue(keySort, sort, key.first, key.second, mapType, keyInfo)

    override fun emptyRegion(): USymbolicCollection<UInputMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort> {
        val updates = UTreeUpdates<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<Reg>, ValueSort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun keyInfo(): USymbolicMapKeyInfo<KeySort, Reg> = USymbolicMapKeyInfo(keyInfo)

    override fun toString(): String = "inputMap<$mapType>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputMapId<*, *, *, *>

        if (keySort != other.keySort) return false
        if (sort != other.sort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int = hash(keySort, sort, mapType)
}
