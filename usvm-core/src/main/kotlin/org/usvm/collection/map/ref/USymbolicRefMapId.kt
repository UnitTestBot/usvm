package org.usvm.collection.map.ref

import io.ksmt.cache.hash
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.map.USymbolicMapKeyInfo
import org.usvm.collection.map.USymbolicMapKeyRegion
import org.usvm.collection.set.UAllocatedSetId
import org.usvm.collection.set.UInputSetId
import org.usvm.collection.set.USymbolicSetId
import org.usvm.memory.DecomposedKey
import org.usvm.memory.KeyTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionIdWithContextMemory
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.memory.key.USingleKeyInfo
import org.usvm.sampleUValue
import org.usvm.util.emptyRegionTree
import org.usvm.uctx

interface USymbolicRefMapId<
    MapType,
    Key,
    ValueSort : USort,
    out KeysSetId : USymbolicSetId<Key, *, KeysSetId>,
    out MapId : USymbolicRefMapId<MapType, Key, ValueSort, KeysSetId, MapId>,
    >
    : USymbolicCollectionId<Key, ValueSort, MapId> {
    val keysSetId: KeysSetId
    val mapType: MapType
}

class UAllocatedRefMapWithAllocatedKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val mapAddress: UConcreteHeapAddress,
    val keyAddress: UConcreteHeapAddress,
    val idDefaultValue: UExpr<ValueSort>? = null,
) : USymbolicRefMapId<MapType, Unit, ValueSort, Nothing, UAllocatedRefMapWithAllocatedKeysId<MapType, ValueSort>> {

    val defaultValue: UExpr<ValueSort> by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun rebindKey(key: Unit): DecomposedKey<*, ValueSort>? = null

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedRefMapWithAllocatedKeysId<MapType, ValueSort>, Unit, ValueSort>,
        key: Unit,
    ): UExpr<ValueSort> {
        check(collection.updates.isEmpty()) { "Can't instantiate allocated map reading from non-empty collection" }
        return defaultValue
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: Unit, value: UExpr<ValueSort>, guard: UBoolExpr) {
        val lvalue = URefMapEntryLValue(
            sort,
            value.uctx.mkConcreteHeapRef(mapAddress),
            value.uctx.mkConcreteHeapRef(keyAddress),
            mapType
        )
        memory.write(lvalue, value, guard)
    }

    override fun toString(): String = "allocatedMap<$mapType>($mapAddress)[$keyAddress]"

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = USingleKeyInfo

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedRefMapWithAllocatedKeysId<*, *>

        if (sort != other.sort) return false
        if (mapType != other.mapType) return false
        if (mapAddress != other.mapAddress) return false
        if (keyAddress != other.keyAddress) return false

        return true
    }

    override fun hashCode(): Int = hash(mapAddress, keyAddress, mapType, sort)

    override val keysSetId: Nothing
        get() = TODO()

    override fun emptyRegion() =
        error("This should not be called")

    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Unit> =
        error("This should not be called")

    override fun <Type> map(
        composer: UComposer<Type>,
    ) = error("This should not be called")
}

class UAllocatedRefMapWithInputKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val mapAddress: UConcreteHeapAddress,
    val idDefaultValue: UExpr<ValueSort>? = null,
) : USymbolicRefMapId<MapType, UHeapRef, ValueSort,
    UAllocatedSetId<UHeapRef, UHeapRefRegion>,
    UAllocatedRefMapWithInputKeysId<MapType, ValueSort>> {

    val defaultValue: UExpr<ValueSort> by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, ValueSort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedRefMapWithAllocatedKeysId(
                    sort,
                    mapType,
                    mapAddress,
                    key.address,
                    idDefaultValue
                ),
                Unit
            )

            else -> null
        }

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef,
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return defaultValue
        }

        return key.uctx.mkAllocatedRefMapWithInputKeysReading(collection, key)
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<ValueSort>, guard: UBoolExpr) {
        val lvalue = URefMapEntryLValue(sort, value.uctx.mkConcreteHeapRef(mapAddress), key, mapType)
        memory.write(lvalue, value, guard)
    }

    override val keysSetId: UAllocatedSetId<UHeapRef, UHeapRefRegion>
        get() = UAllocatedSetId(UHeapRefKeyInfo)

    override fun keyInfo(): USymbolicCollectionKeyInfo<UHeapRef, *> = UHeapRefKeyInfo

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(
        composer: UComposer<Type>,
    ): UAllocatedRefMapWithInputKeysId<MapType, ValueSort> {
        val composedDefaultValue = composer.compose(defaultValue)
        return UAllocatedRefMapWithInputKeysId(
            sort, mapType, mapAddress, composedDefaultValue
        )
    }

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
    val idDefaultValue: UExpr<ValueSort>? = null,
) : USymbolicRefMapId<MapType, UHeapRef, ValueSort,
        UAllocatedSetId<UHeapRef, UHeapRefRegion>,
        UInputRefMapWithAllocatedKeysId<MapType, ValueSort>> {

    val defaultValue: UExpr<ValueSort> by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, ValueSort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedRefMapWithAllocatedKeysId(
                    sort,
                    mapType,
                    key.address,
                    keyAddress,
                    idDefaultValue
                ),
                Unit
            )

            else -> null
        }

    override fun instantiate(
        collection: USymbolicCollection<UInputRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef,
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return defaultValue
        }

        return key.uctx.mkInputRefMapWithAllocatedKeysReading(collection, key)
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<ValueSort>, guard: UBoolExpr) {
        val lvalue = URefMapEntryLValue(sort, key, key.uctx.mkConcreteHeapRef(keyAddress), mapType)
        memory.write(lvalue, value, guard)
    }

    override val keysSetId: UAllocatedSetId<UHeapRef, UHeapRefRegion>
        get() = UAllocatedSetId(UHeapRefKeyInfo)

    override fun keyInfo(): USymbolicCollectionKeyInfo<UHeapRef, *> = UHeapRefKeyInfo

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(
        composer: UComposer<Type>,
    ): UInputRefMapWithAllocatedKeysId<MapType, ValueSort> {
        val composedDefaultValue = composer.compose(defaultValue)
        return UInputRefMapWithAllocatedKeysId(
            sort, mapType, keyAddress, composedDefaultValue
        )
    }

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
    val defaultValue: UExpr<ValueSort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<
    USymbolicMapKey<UAddressSort>, ValueSort, UInputRefMapWithInputKeysId<MapType, ValueSort>>(contextMemory),
    USymbolicRefMapId<MapType, USymbolicMapKey<UAddressSort>, ValueSort,
        UInputSetId<USymbolicMapKey<UAddressSort>, *>,
        UInputRefMapWithInputKeysId<MapType, ValueSort>> {

    override fun rebindKey(key: USymbolicMapKey<UAddressSort>): DecomposedKey<*, ValueSort>? {
        val mapRef = key.first
        val keyRef = key.second

        return when (mapRef) {
            is UConcreteHeapRef -> when (keyRef) {
                is UConcreteHeapRef -> DecomposedKey(
                    UAllocatedRefMapWithAllocatedKeysId(
                        sort,
                        mapType,
                        mapRef.address,
                        keyRef.address,
                        defaultValue
                    ),
                    Unit
                )

                else -> DecomposedKey(
                    UAllocatedRefMapWithInputKeysId(
                        sort,
                        mapType,
                        mapRef.address,
                        defaultValue
                    ),
                    keyRef
                )
            }

            else -> when (keyRef) {
                is UConcreteHeapRef -> DecomposedKey(
                    UInputRefMapWithAllocatedKeysId(
                        sort,
                        mapType,
                        keyRef.address,
                        defaultValue
                    ),
                    mapRef
                )

                else -> null
            }
        }
    }

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>,
        key: USymbolicMapKey<UAddressSort>,
    ): UExpr<ValueSort> {
        return mkInputRefMapWithInputKeysReading(collection, key.first, key.second)
    }

    override fun UContext.mkLValue(key: USymbolicMapKey<UAddressSort>): ULValue<*, ValueSort> =
        URefMapEntryLValue(sort, key.first, key.second, mapType)

    override val keysSetId: UInputSetId<USymbolicMapKey<UAddressSort>, *>
        get() = UInputSetId(keyInfo(), contextMemory)

    override fun keyInfo(): USymbolicCollectionKeyInfo<USymbolicMapKey<UAddressSort>, *> =
        USymbolicMapKeyInfo(UHeapRefKeyInfo)

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<USymbolicMapKey<UAddressSort>> = {
        val ref = transformer.apply(it.first)
        val idx = transformer.apply(it.second)
        if (ref === it.first && idx === it.second) it else ref to idx
    }

    override fun <Type> map(
        composer: UComposer<Type>,
    ): UInputRefMapWithInputKeysId<MapType, ValueSort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(sort.sampleUValue())
        return UInputRefMapWithInputKeysId(
            sort, mapType, composedDefaultValue, composer.memory.toWritableMemory()
        )
    }

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
