package org.usvm.collection.map.ref

import io.ksmt.cache.hash
import org.usvm.UAddressSort
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.collection.set.UAllocatedSymbolicSetId
import org.usvm.collection.set.UInputSymbolicSetId
import org.usvm.collection.set.USymbolicSetId
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.DecomposedKey
import org.usvm.memory.KeyTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionIdWithContextMemory
import org.usvm.memory.UWritableMemory
import org.usvm.memory.UTreeUpdates
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.UHeapRefRegion
import org.usvm.memory.key.USingleKeyInfo
import org.usvm.collection.map.USymbolicMapKey
import org.usvm.collection.map.USymbolicMapKeyInfo
import org.usvm.collection.map.USymbolicMapKeyRegion
import org.usvm.collection.map.primitive.USymbolicMapEntryRef
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.sampleUValue
import org.usvm.util.emptyRegionTree

interface USymbolicRefMapId<
        MapType,
        Key,
        ValueSort : USort,
        out KeysSetId : USymbolicSetId<Key, *, KeysSetId>,
        out MapId : USymbolicRefMapId<MapType, Key, ValueSort, KeysSetId, MapId>>
    : USymbolicCollectionId<Key, ValueSort, MapId> {
    val keysSetId: KeysSetId
    val mapType: MapType
}

class UAllocatedSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val mapAddress: UConcreteHeapAddress,
    val keyAddress: UConcreteHeapAddress,
    val idDefaultValue: UExpr<ValueSort>? = null,
    contextMemory: UWritableMemory<*>? = null
) : USymbolicCollectionIdWithContextMemory<
        Unit, ValueSort, UAllocatedSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>>(contextMemory),
    USymbolicRefMapId<MapType, Unit, ValueSort, Nothing, UAllocatedSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>> {

    val defaultValue: UExpr<ValueSort> by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun rebindKey(key: Unit): DecomposedKey<*, ValueSort>? = null

    override fun toString(): String = "allocatedMap<$mapType>($mapAddress)[$keyAddress]"

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = USingleKeyInfo

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, Unit, ValueSort>,
        key: Unit
    ): UExpr<ValueSort> {
        check(collection.updates.isEmpty()) { "Can't instantiate allocated map reading from non-empty collection" }
        return defaultValue
    }

    override fun UContext.mkLValue(
        key: Unit
    ): ULValue<*, ValueSort> = USymbolicMapEntryRef(
        addressSort, sort, mkConcreteHeapRef(mapAddress), mkConcreteHeapRef(keyAddress), mapType, UHeapRefKeyInfo
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedSymbolicRefMapWithAllocatedKeysId<*, *>

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
        composer: UComposer<Type>
    ) = error("This should not be called")
}

class UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val mapAddress: UConcreteHeapAddress,
    val idDefaultValue: UExpr<ValueSort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<
        UHeapRef, ValueSort, UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>>(contextMemory),
    USymbolicRefMapId<MapType, UHeapRef, ValueSort,
            UAllocatedSymbolicSetId<UHeapRef, UHeapRefRegion>,
            UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>> {

    val defaultValue: UExpr<ValueSort> by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, ValueSort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedSymbolicRefMapWithAllocatedKeysId(
                    sort,
                    mapType,
                    mapAddress,
                    key.address,
                    idDefaultValue,
                    contextMemory
                ),
                Unit
            )

            else -> null
        }

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return defaultValue
        }

        TODO("Not yet implemented")
    }

    override fun UContext.mkLValue(key: UHeapRef): ULValue<*, ValueSort> {
        TODO("Not yet implemented")
    }

    override val keysSetId: UAllocatedSymbolicSetId<UHeapRef, UHeapRefRegion>
        get() = UAllocatedSymbolicSetId(UHeapRefKeyInfo, contextMemory)

    override fun keyInfo(): USymbolicCollectionKeyInfo<UHeapRef, *> = UHeapRefKeyInfo

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(
        composer: UComposer<Type>
    ): UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(defaultValue)
        return UAllocatedSymbolicRefMapWithInputKeysId(
            sort, mapType, mapAddress, composedDefaultValue, composer.memory.toWritableMemory()
        )
    }

    override fun emptyRegion(): USymbolicCollection<UAllocatedSymbolicRefMapWithInputKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, ValueSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedSymbolicRefMapWithInputKeysId<*, *>

        if (sort != other.sort) return false
        if (mapType != other.mapType) return false
        if (mapAddress != other.mapAddress) return false

        return true
    }

    override fun hashCode(): Int = hash(mapAddress, mapType, sort)

    override fun toString(): String = "allocatedRefMap<$mapType>($mapAddress)"
}

class UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val keyAddress: UConcreteHeapAddress,
    val idDefaultValue: UExpr<ValueSort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<
        UHeapRef, ValueSort, UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>>(contextMemory),
    USymbolicRefMapId<MapType, UHeapRef, ValueSort,
            UAllocatedSymbolicSetId<UHeapRef, UHeapRefRegion>,
            UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>> {

    val defaultValue: UExpr<ValueSort> by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, ValueSort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedSymbolicRefMapWithAllocatedKeysId(
                    sort,
                    mapType,
                    key.address,
                    keyAddress,
                    idDefaultValue,
                    contextMemory
                ),
                Unit
            )

            else -> null
        }

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort>,
        key: UHeapRef
    ): UExpr<ValueSort> {
        if (collection.updates.isEmpty()) {
            return defaultValue
        }

        TODO("Not yet implemented")
    }

    override fun UContext.mkLValue(key: UHeapRef): ULValue<*, ValueSort> {
        TODO("Not yet implemented")
    }

    override val keysSetId: UAllocatedSymbolicSetId<UHeapRef, UHeapRefRegion>
        get() = UAllocatedSymbolicSetId(UHeapRefKeyInfo, contextMemory)

    override fun keyInfo(): USymbolicCollectionKeyInfo<UHeapRef, *> = UHeapRefKeyInfo

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(
        composer: UComposer<Type>
    ): UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(defaultValue)
        return UInputSymbolicRefMapWithAllocatedKeysId(
            sort, mapType, keyAddress, composedDefaultValue, composer.memory.toWritableMemory()
        )
    }

    override fun emptyRegion(): USymbolicCollection<UInputSymbolicRefMapWithAllocatedKeysId<MapType, ValueSort>, UHeapRef, ValueSort> {
        val updates = UTreeUpdates<UHeapRef, UHeapRefRegion, ValueSort>(
            updates = emptyRegionTree(),
            UHeapRefKeyInfo
        )
        return USymbolicCollection(this, updates)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicRefMapWithAllocatedKeysId<*, *>

        if (sort != other.sort) return false
        if (mapType != other.mapType) return false
        if (keyAddress != other.keyAddress) return false

        return true
    }

    override fun hashCode(): Int = hash(keyAddress, mapType, sort)

    override fun toString(): String = "inputRefMap<$mapType>()[$keyAddress]"
}

class UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort : USort>(
    override val sort: ValueSort,
    override val mapType: MapType,
    val defaultValue: UExpr<ValueSort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<
        USymbolicMapKey<UAddressSort>, ValueSort, UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>>(contextMemory),
    USymbolicRefMapId<MapType, USymbolicMapKey<UAddressSort>, ValueSort,
            UInputSymbolicSetId<USymbolicMapKey<UAddressSort>, *>,
            UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>> {

    override fun rebindKey(key: USymbolicMapKey<UAddressSort>): DecomposedKey<*, ValueSort>? {
        val mapRef = key.first
        val keyRef = key.second

        return when (mapRef) {
            is UConcreteHeapRef -> when (keyRef) {
                is UConcreteHeapRef -> DecomposedKey(
                    UAllocatedSymbolicRefMapWithAllocatedKeysId(
                        sort,
                        mapType,
                        mapRef.address,
                        keyRef.address,
                        defaultValue,
                        contextMemory
                    ),
                    Unit
                )

                else -> DecomposedKey(
                    UAllocatedSymbolicRefMapWithInputKeysId(
                        sort,
                        mapType,
                        mapRef.address,
                        defaultValue,
                        contextMemory
                    ),
                    keyRef
                )
            }

            else -> when (keyRef) {
                is UConcreteHeapRef -> DecomposedKey(
                    UInputSymbolicRefMapWithAllocatedKeysId(
                        sort,
                        mapType,
                        keyRef.address,
                        defaultValue,
                        contextMemory
                    ),
                    mapRef
                )

                else -> null
            }
        }
    }

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort>,
        key: USymbolicMapKey<UAddressSort>
    ): UExpr<ValueSort> {
        TODO("Not yet implemented")
    }

    override fun UContext.mkLValue(key: USymbolicMapKey<UAddressSort>): ULValue<*, ValueSort> {
        TODO("Not yet implemented")
    }

    override val keysSetId: UInputSymbolicSetId<USymbolicMapKey<UAddressSort>, *>
        get() = UInputSymbolicSetId(keyInfo(), contextMemory)

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
        composer: UComposer<Type>
    ): UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(sort.sampleUValue())
        return UInputSymbolicRefMapWithInputKeysId(
            sort, mapType, composedDefaultValue, composer.memory.toWritableMemory()
        )
    }

    override fun emptyRegion(): USymbolicCollection<UInputSymbolicRefMapWithInputKeysId<MapType, ValueSort>, USymbolicMapKey<UAddressSort>, ValueSort> {
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

        other as UInputSymbolicRefMapWithInputKeysId<*, *>

        if (sort != other.sort) return false
        if (mapType != other.mapType) return false

        return true
    }

    override fun hashCode(): Int = hash(mapType, sort)

    override fun toString(): String = "inputRefMap<$mapType>()"
}
