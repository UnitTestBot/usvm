package org.usvm.collection.map.length

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.UTransformer
import org.usvm.memory.KeyTransformer
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx

interface USymbolicMapLengthId<Key, MapType, Id : USymbolicMapLengthId<Key, MapType, Id>> :
    USymbolicCollectionId<Key, USizeSort, Id> {
    val mapType: MapType
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
