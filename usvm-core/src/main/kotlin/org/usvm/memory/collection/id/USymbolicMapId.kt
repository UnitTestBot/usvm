package org.usvm.memory.collection.id

import io.ksmt.cache.hash
import io.ksmt.expr.KExpr
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.UTreeUpdates
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.memory.collection.key.USymbolicMapKey
import org.usvm.memory.collection.key.USymbolicMapKeyInfo
import org.usvm.memory.collection.key.USymbolicMapKeyRegion
import org.usvm.memory.collection.region.USymbolicMapEntryRef
import org.usvm.uctx
import org.usvm.util.Region
import org.usvm.util.emptyRegionTree

interface USymbolicMapId<
        MapType,
        Key,
        ValueSort : USort,
        out KeysSetId : USymbolicSetId<Key, *, KeysSetId>,
        out MapId : USymbolicMapId<MapType, Key, ValueSort, KeysSetId, MapId>>
    : USymbolicCollectionId<Key, ValueSort, MapId> {
    val keysSetId: KeysSetId
    val mapType: MapType
}

class UAllocatedSymbolicMapId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    override val defaultValue: UExpr<ValueSort>,
    val keySort: KeySort,
    val valueSort: ValueSort,
    override val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    val address: UConcreteHeapAddress,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<UExpr<KeySort>, ValueSort, UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>>(
    contextMemory
),
    USymbolicMapId<MapType, UExpr<KeySort>, ValueSort, UAllocatedSymbolicSetId<UExpr<KeySort>, Reg>, UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>> {

    override val keysSetId: UAllocatedSymbolicSetId<KExpr<KeySort>, Reg>
        get() = UAllocatedSymbolicSetId(keyInfo, contextMemory)

    override val sort: ValueSort get() = valueSort

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
        key: UExpr<KeySort>
    ): UExpr<ValueSort> = mkAllocatedSymbolicMapReading(collection, key)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort>,
        key: UExpr<KeySort>
    ): ULValue<*, ValueSort> = USymbolicMapEntryRef(keySort, sort, mkConcreteHeapRef(address), key, mapType, keyInfo)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UExpr<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        val lValue = USymbolicMapEntryRef(keySort, sort, key.uctx.mkConcreteHeapRef(address), key, mapType, keyInfo)
        memory.write(lValue, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UExpr<KeySort>> = { transformer.apply(it) }

    override fun <Type> map(
        composer: UComposer<Type>
    ): UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(defaultValue)
        return UAllocatedSymbolicMapId(
            composedDefaultValue, keySort, valueSort, mapType, keyInfo, address, composer.memory.toWritableMemory()
        )
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg> = keyInfo

    override fun rebindKey(key: UExpr<KeySort>): DecomposedKey<*, ValueSort>? {
        TODO("Not yet implemented")
    }

    override fun emptyRegion(): USymbolicCollection<UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>, UExpr<KeySort>, ValueSort> {
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

        other as UAllocatedSymbolicMapId<*, *, *, *>

        if (address != other.address) return false
        if (keySort != other.keySort) return false
        if (valueSort != other.valueSort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int = hash(address, keySort, valueSort, mapType)
}

class UInputSymbolicMapId<MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> internal constructor(
    val keySort: KeySort,
    val valueSort: ValueSort,
    override val mapType: MapType,
    val keyInfo: USymbolicCollectionKeyInfo<UExpr<KeySort>, Reg>,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<USymbolicMapKey<KeySort>, ValueSort, UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>>(
    contextMemory
),
    USymbolicMapId<MapType, USymbolicMapKey<KeySort>, ValueSort, UInputSymbolicSetId<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<Reg>>, UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>> {
    override val keysSetId: UInputSymbolicSetId<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<Reg>>
        get() = UInputSymbolicSetId(keyInfo(), contextMemory)

    override val sort: ValueSort get() = valueSort
    override val defaultValue: UExpr<ValueSort>? get() = null

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
        key: USymbolicMapKey<KeySort>
    ): UExpr<ValueSort> = mkInputSymbolicMapReading(collection, key.first, key.second)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort>,
        key: USymbolicMapKey<KeySort>
    ): ULValue<*, ValueSort> = USymbolicMapEntryRef(keySort, sort, key.first, key.second, mapType, keyInfo)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: USymbolicMapKey<KeySort>,
        value: UExpr<ValueSort>,
        guard: UBoolExpr
    ) {
        val lValue = USymbolicMapEntryRef(keySort, sort, key.first, key.second, mapType, keyInfo)
        memory.write(lValue, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<USymbolicMapKey<KeySort>> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun emptyRegion(): USymbolicCollection<UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>, USymbolicMapKey<KeySort>, ValueSort> {
        val updates = UTreeUpdates<USymbolicMapKey<KeySort>, USymbolicMapKeyRegion<Reg>, ValueSort>(
            updates = emptyRegionTree(),
            keyInfo()
        )
        return USymbolicCollection(this, updates)
    }

    override fun <Type> map(
        composer: UComposer<Type>
    ): UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return UInputSymbolicMapId(keySort, valueSort, mapType, keyInfo, composer.memory.toWritableMemory())
    }

    override fun keyInfo(): USymbolicMapKeyInfo<KeySort, Reg> = USymbolicMapKeyInfo(keyInfo)

    override fun rebindKey(key: USymbolicMapKey<KeySort>): DecomposedKey<*, ValueSort>? {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "inputMap<$mapType>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicMapId<*, *, *, *>

        if (keySort != other.keySort) return false
        if (valueSort != other.valueSort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int =
        hash(keySort, valueSort, mapType)
}
