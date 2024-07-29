package org.usvm.collection.array.length

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.withSizeSort
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.uctx

interface USymbolicArrayLengthId<Key, ArrayType, Id, USizeSort : USort> : USymbolicCollectionId<Key, USizeSort, Id>
        where Id : USymbolicArrayLengthId<Key, ArrayType, Id, USizeSort> {
    val arrayType: ArrayType
}

/**
 * A collection id for a collection storing array lengths for arrays of a specific [arrayType].
 */
class UInputArrayLengthId<ArrayType, USizeSort : USort> internal constructor(
    override val arrayType: ArrayType,
    override val sort: USizeSort,
) : USymbolicArrayLengthId<UHeapRef, ArrayType, UInputArrayLengthId<ArrayType, USizeSort>, USizeSort> {

    override fun instantiate(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType, USizeSort>, UHeapRef, USizeSort>,
        key: UHeapRef,
        composer: UComposer<*, *>?
    ): UExpr<USizeSort> {
        if (composer == null) {
            return key.uctx.withSizeSort<USizeSort>().mkInputArrayLengthReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory(composer.ownership)
        collection.applyTo(memory, key, composer)
        return memory.read(mkLValue(key))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<USizeSort>, guard: UBoolExpr) {
        memory.write(mkLValue(key), value, guard)
    }

    private fun mkLValue(key: UHeapRef) = UArrayLengthLValue(key, arrayType, sort)

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): USymbolicCollection<UInputArrayLengthId<ArrayType, USizeSort>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String = "length<$arrayType>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputArrayLengthId<*, *>

        return arrayType == other.arrayType
    }

    override fun hashCode(): Int = arrayType.hashCode()
}
