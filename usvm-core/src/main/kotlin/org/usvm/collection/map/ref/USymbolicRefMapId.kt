package org.usvm.collection.map.ref

import io.ksmt.cache.hash
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.map.USymbolicMapKeyInfo
import org.usvm.collection.map.USymbolicMapKeyRegion
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsId
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsId
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsId
import org.usvm.collection.set.ref.USymbolicRefSetId
import org.usvm.compose
import org.usvm.memory.ULValue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.sampleUValue
import org.usvm.uctx
import org.usvm.regions.emptyRegionTree

interface USymbolicRefMapId<
        MapType,
        Key,
        ValueSort : USort,
        out KeysSetId : USymbolicRefSetId<MapType, Key, *, KeysSetId>,
        out MapId : USymbolicRefMapId<MapType, Key, ValueSort, KeysSetId, MapId>>
    : USymbolicCollectionId<Key, ValueSort, MapId> {
    val keysSetId: KeysSetId
    val mapType: MapType
}

class UAllocatedRefMapWithInputKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val mapAddress: UConcreteHeapAddress,
) : USymbolicRefMapId<MapType, UHeapRef, ValueSort,
        UAllocatedRefSetWithInputElementsId<MapType>,
        UAllocatedRefMapWithInputKeysId<MapType, ValueSort>> {
    val defaultValue: UExpr<ValueSort> by lazy { sort.sampleUValue() }

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return composer.compose(defaultValue)
        }

        if (composer == null) {
            return key.uctx.mkAllocatedRefMapWithInputKeysReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<ValueSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef): ULValue<*, ValueSort> =
        URefMapEntryLValue(sort, key.uctx.mkConcreteHeapRef(mapAddress), key, mapType)

    override val keysSetId: UAllocatedRefSetWithInputElementsId<MapType>
        get() = UAllocatedRefSetWithInputElementsId(mapAddress, mapType, sort.uctx.boolSort)

    override fun keyInfo(): USymbolicCollectionKeyInfo<UHeapRef, *> = UHeapRefKeyInfo

    override fun emptyRegion(): USymbolicCollection<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, ValueSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedRefMapWithInputKeysId<*, *>

        if (sort != other.sort) return false
        if (mapType != other.mapType) return false
        if (mapAddress != other.mapAddress) return false

        return true
    }

    override fun hashCode(): Int = hash(mapAddress, mapType, sort)

    override fun toString(): String = "allocatedRefMap<$mapType>($mapAddress)"
}

class UInputRefMapWithAllocatedKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val keyAddress: UConcreteHeapAddress,
) : USymbolicRefMapId<MapType, UHeapRef, ValueSort,
        UInputRefSetWithAllocatedElementsId<MapType>,
        UInputRefMapWithAllocatedKeysId<MapType, ValueSort>> {
    val defaultValue: UExpr<ValueSort> by lazy { sort.sampleUValue() }

    override fun instantiate(
        collection: USymbolicCollection<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return composer.compose(defaultValue)
        }

        if (composer == null) {
            return key.uctx.mkInputRefMapWithAllocatedKeysReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<ValueSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef): ULValue<*, ValueSort> =
        URefMapEntryLValue(sort, key, key.uctx.mkConcreteHeapRef(keyAddress), mapType)

    override val keysSetId: UInputRefSetWithAllocatedElementsId<MapType>
        get() = UInputRefSetWithAllocatedElementsId(keyAddress, mapType, sort.uctx.boolSort)

    override fun keyInfo(): USymbolicCollectionKeyInfo<UHeapRef, *> = UHeapRefKeyInfo

    override fun emptyRegion(): USymbolicCollection<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, ValueSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputRefMapWithAllocatedKeysId<*, *>

        if (sort != other.sort) return false
        if (mapType != other.mapType) return false
        if (keyAddress != other.keyAddress) return false

        return true
    }

    override fun hashCode(): Int = hash(keyAddress, mapType, sort)

    override fun toString(): String = "inputRefMap<$mapType>()[$keyAddress]"
}

class UInputRefMapWithInputKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
) : USymbolicRefMapId<MapType, USymbolicMapKey<UAddressSort>, ValueSort,
        UInputRefSetWithInputElementsId<MapType>,
        UInputRefMapWithInputKeysId<MapType, ValueSort>> {

    override fun instantiate(
        collection: USymbolicCollection<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>,
        key: USymbolicMapKey<UAddressSort>,
        composer: UComposer<*, *>?
    ): UExpr<ValueSort> {
        if (composer == null) {
            return sort.uctx.mkInputRefMapWithInputKeysReading(collection, key.first, key.second)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicMapKey<UAddressSort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: USymbolicMapKey<UAddressSort>): ULValue<*, ValueSort> =
        URefMapEntryLValue(sort, key.first, key.second, mapType)

    override val keysSetId: UInputRefSetWithInputElementsId<MapType>
        get() = UInputRefSetWithInputElementsId(mapType, sort.uctx.boolSort)

    override fun keyInfo(): USymbolicCollectionKeyInfo<USymbolicMapKey<UAddressSort>, *> =
        USymbolicMapKeyInfo(UHeapRefKeyInfo)

    override fun emptyRegion(): USymbolicCollection<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort> {
        val updates =
            UTreeUpdates<USymbolicMapKey<UAddressSort>, USymbolicMapKeyRegion<UHeapRefRegion>, ValueSort>(
                updates = emptyRegionTree(),
                USymbolicMapKeyInfo(UHeapRefKeyInfo)
            )
        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputRefMapWithInputKeysId<*, *>

        if (sort != other.sort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int = hash(mapType, sort)

    override fun toString(): String = "inputRefMap<$mapType>()"
}
