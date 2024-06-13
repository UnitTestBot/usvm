package org.usvm.collection.set.length

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.UAnySetRegionId
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx
import org.usvm.withSizeSort

interface USymbolicSetLengthId<Key, SetType, Id : USymbolicSetLengthId<Key, SetType, Id, USizeSort>, USizeSort : USort> :
    USymbolicCollectionId<Key, USizeSort, Id> {
    val setId: UAnySetRegionId<SetType, *>
}

class UInputSetLengthId<SetType, USizeSort : USort> internal constructor(
    override val setId: UAnySetRegionId<SetType, *>,
    override val sort: USizeSort,
) : USymbolicSetLengthId<UHeapRef, SetType, UInputSetLengthId<SetType, USizeSort>, USizeSort> {

    override fun instantiate(
        collection: USymbolicCollection<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<USizeSort> {
        if (composer == null) {
            return sort.uctx.withSizeSort<USizeSort>().mkInputSetLengthReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<USizeSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) = USetLengthLValue(key, setId, sort)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): USymbolicCollection<UInputSetLengthId<SetType, USizeSort>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String = "length<$setId>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputSetLengthId<*, *>

        return setId == other.setId
    }

    override fun hashCode(): Int = setId.hashCode()
}
