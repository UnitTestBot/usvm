package org.usvm.collection.map.length

import io.ksmt.cache.hash
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.USizeSort
import org.usvm.UTransformer
import org.usvm.compose
import org.usvm.memory.KeyTransformer
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.USingleKeyInfo
import org.usvm.sampleUValue
import org.usvm.uctx

interface USymbolicMapLengthId<Key, MapType, Id : USymbolicMapLengthId<Key, MapType, Id>> :
    USymbolicCollectionId<Key, USizeSort, Id> {
    val mapType: MapType
}

class UAllocatedMapLengthId<MapType> internal constructor(
    override val mapType: MapType,
    val address: UConcreteHeapAddress,
    override val sort: USizeSort,
) : USymbolicMapLengthId<Unit, MapType, UAllocatedMapLengthId<MapType>> {
    val defaultValue: USizeExpr by lazy { sort.sampleUValue() }

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedMapLengthId<MapType>, Unit, USizeSort>,
        key: Unit,
        composer: UComposer<*>?
    ): UExpr<USizeSort> {
        check(collection.updates.isEmpty()) { "Can't instantiate length reading from non-empty collection" }
        return composer.compose(defaultValue)
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: Unit, value: UExpr<USizeSort>, guard: UBoolExpr) {
        val lvalue = UMapLengthLValue(value.uctx.mkConcreteHeapRef(address), mapType)
        memory.write(lvalue, value, guard)
    }

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = USingleKeyInfo

    override fun toString(): String = "allocatedLength<$mapType>($address)"

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
}

class UInputMapLengthId<MapType> internal constructor(
    override val mapType: MapType,
    override val sort: USizeSort,
) : USymbolicMapLengthId<UHeapRef, MapType, UInputMapLengthId<MapType>> {

    override fun instantiate(
        collection: USymbolicCollection<UInputMapLengthId<MapType>, UHeapRef, USizeSort>,
        key: UHeapRef,
        composer: UComposer<*>?
    ): UExpr<USizeSort> {
        if (composer == null) {
            return sort.uctx.mkInputMapLengthReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, composer)
        return memory.read(UMapLengthLValue(key, mapType))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<USizeSort>, guard: UBoolExpr) {
        val lvalue = UMapLengthLValue(key, mapType)
        memory.write(lvalue, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun keyInfo() = UHeapRefKeyInfo

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
