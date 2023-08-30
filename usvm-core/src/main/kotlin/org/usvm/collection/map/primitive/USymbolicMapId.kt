package org.usvm.collection.map.primitive

import io.ksmt.cache.hash
import io.ksmt.expr.KExpr
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.map.USymbolicMapKeyInfo
import org.usvm.collection.map.USymbolicMapKeyRegion
import org.usvm.collection.set.UAllocatedSetId
import org.usvm.collection.set.UInputSetId
import org.usvm.collection.set.USymbolicSetId
import org.usvm.compose
import org.usvm.memory.KeyTransformer
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.sampleUValue
import org.usvm.uctx
import org.usvm.util.Region
import org.usvm.util.emptyRegionTree

interface USymbolicMapId<
        MapType,
        Key,
        ValueSort : USort,
        out KeysSetId : USymbolicSetId<Key, *, KeysSetId>,
        out MapId : USymbolicMapId<MapType, Key, ValueSort, KeysSetId, MapId>,
        >
    : USymbolicCollectionId<Key, ValueSort, MapId> {
    val keysSetId: KeysSetId
    val mapType: MapType
}

class UAllocatedMapId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    val keySort: KeySort,
    val valueSort: ValueSort,
    override val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    val address: UConcreteHeapAddress,
) : USymbolicMapId<MapType, UExpr<KeySort>, ValueSort,
        UAllocatedSetId<UExpr<KeySort>, Reg>,
        UAllocatedMapId<MapType, KeySort, ValueSort, Reg>> {

    val defaultValue: UExpr<ValueSort> by lazy { valueSort.sampleUValue() }

    override val keysSetId: UAllocatedSetId<KExpr<KeySort>, Reg>
        get() = UAllocatedSetId(keyInfo)

    override val sort: ValueSort get() = valueSort

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
        key: UExpr<KeySort>,
        composer: UComposer<*>?
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return composer.compose(defaultValue)
        }

        if (composer == null) {
            return key.uctx.mkAllocatedMapReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, composer)
        return memory.read(UMapEntryLValue(keySort, sort, key.uctx.mkConcreteHeapRef(address), key, mapType, keyInfo))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr,
    ) {
        val lvalue = UMapEntryLValue(keySort, sort, key.uctx.mkConcreteHeapRef(address), key, mapType, keyInfo)
        memory.write(lvalue, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UExpr<KeySort>> = { transformer.apply(it) }

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
        if (valueSort != other.valueSort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int = hash(address, keySort, valueSort, mapType)
}

class UInputMapId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    val keySort: KeySort,
    val valueSort: ValueSort,
    override val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
) : USymbolicMapId<MapType, USymbolicMapKey<KeySort>, ValueSort,
        UInputSetId<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<Reg>>,
        UInputMapId<MapType, KeySort, ValueSort, Reg>> {
    override val keysSetId: UInputSetId<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<Reg>>
        get() = UInputSetId(keyInfo())

    override val sort: ValueSort get() = valueSort

    override fun instantiate(
        collection: USymbolicCollection<UInputMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
        key: USymbolicMapKey<KeySort>,
        composer: UComposer<*>?
    ): UExpr<ValueSort> {
        if (composer == null) {
            return sort.uctx.mkInputMapReading(collection, key.first, key.second)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, composer)
        return memory.read(UMapEntryLValue(keySort, sort, key.first, key.second, mapType, keyInfo))
    }

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicMapKey<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        val lvalue = UMapEntryLValue(keySort, sort, key.first, key.second, mapType, keyInfo)
        memory.write(lvalue, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<USymbolicMapKey<KeySort>> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

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
        if (valueSort != other.valueSort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int =
        hash(keySort, valueSort, mapType)
}
