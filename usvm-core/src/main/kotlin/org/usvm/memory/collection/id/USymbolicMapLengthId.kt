package org.usvm.memory.collection.id

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.UTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.UFlatUpdates
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.key.UHeapRefKeyInfo
import org.usvm.memory.collection.region.USymbolicMapLengthRef

class UInputSymbolicMapLengthId<MapType> internal constructor(
    val mapType: MapType,
    override val sort: USizeSort,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<UHeapRef, USizeSort, UInputSymbolicMapLengthId<MapType>>(contextMemory) {
    override val defaultValue: UExpr<USizeSort>? get() = null

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = mkInputSymbolicMapLengthReading(collection, key)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): ULValue<*, USizeSort> = USymbolicMapLengthRef(sort, key, mapType)

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<USizeSort>, guard: UBoolExpr) {
        val lValue = USymbolicMapLengthRef(sort, key, mapType)
        memory.write(lValue, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputSymbolicMapLengthId<MapType> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return UInputSymbolicMapLengthId(mapType, sort, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? {
        TODO("Not yet implemented")
    }

    override fun emptyRegion(): USymbolicCollection<UInputSymbolicMapLengthId<MapType>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String = "length<$mapType>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSymbolicMapLengthId<*>

        return mapType == other.mapType
    }

    override fun hashCode(): Int = mapType.hashCode()
}