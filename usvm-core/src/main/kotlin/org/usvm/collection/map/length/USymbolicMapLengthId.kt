package org.usvm.collection.map.length

import io.ksmt.cache.hash
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.UTransformer
import org.usvm.memory.DecomposedKey
import org.usvm.memory.KeyTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionIdWithContextMemory
import org.usvm.memory.UWritableMemory
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.USingleKeyInfo
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.sampleUValue

interface USymbolicMapLengthId<Key, MapType, Id : USymbolicMapLengthId<Key, MapType, Id>> :
    USymbolicCollectionId<Key, USizeSort, Id> {
    val mapType: MapType
}

class UAllocatedMapLengthId<MapType> internal constructor(
    override val mapType: MapType,
    val address: UConcreteHeapAddress,
    override val sort: USizeSort,
    val idDefaultValue: UExpr<USizeSort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<Unit, USizeSort, UAllocatedMapLengthId<MapType>>(contextMemory),
    USymbolicMapLengthId<Unit, MapType, UAllocatedMapLengthId<MapType>> {

    val defaultValue: USizeExpr by lazy { idDefaultValue ?: sort.sampleUValue() }

    override fun rebindKey(key: Unit): DecomposedKey<*, USizeSort>? = null

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = USingleKeyInfo

    override fun toString(): String = "allocatedLength<$mapType>($address)"

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedMapLengthId<MapType>, Unit, USizeSort>,
        key: Unit
    ): UExpr<USizeSort> {
        check(collection.updates.isEmpty()) { "Can't instantiate length reading from non-empty collection" }
        return defaultValue
    }

    override fun UContext.mkLValue(
        key: Unit
    ): ULValue<*, USizeSort> = UMapLengthLValue(mkConcreteHeapRef(address), mapType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedMapLengthId<*>

        if (mapType != other.mapType) return false
        if (address != other.address) return false

        return true
    }

    override fun hashCode(): Int = hash(mapType, address)

    override fun emptyRegion(): USymbolicCollection<UAllocatedMapLengthId<MapType>, Unit, USizeSort> =
        error("This should not be called")

    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Unit> =
        error("This should not be called")

    override fun <Type> map(composer: UComposer<Type>): UAllocatedMapLengthId<MapType> =
        error("This should not be called")
}

class UInputMapLengthId<MapType> internal constructor(
    override val mapType: MapType,
    override val sort: USizeSort,
    private val defaultValue: UExpr<USizeSort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<UHeapRef, USizeSort, UInputMapLengthId<MapType>>(contextMemory),
    USymbolicMapLengthId<UHeapRef, MapType, UInputMapLengthId<MapType>> {
    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputMapLengthId<MapType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = mkInputMapLengthReading(collection, key)

    override fun UContext.mkLValue(
        key: UHeapRef
    ): ULValue<*, USizeSort> = UMapLengthLValue(key, mapType)

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputMapLengthId<MapType> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(sort.sampleUValue())
        return UInputMapLengthId(mapType, sort, composedDefaultValue, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? = when (key) {
        is UConcreteHeapRef -> DecomposedKey(
            UAllocatedMapLengthId(
                mapType,
                key.address,
                sort,
                defaultValue,
                contextMemory
            ),
            Unit
        )

        else -> null
    }

    override fun emptyRegion(): USymbolicCollection<UInputMapLengthId<MapType>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String = "length<$mapType>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputMapLengthId<*>

        return mapType == other.mapType
    }

    override fun hashCode(): Int = mapType.hashCode()
}
