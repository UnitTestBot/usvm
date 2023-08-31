package org.usvm.collection.array.length

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

interface USymbolicArrayLengthId<Key, ArrayType, Id : USymbolicArrayLengthId<Key, ArrayType, Id>> :
    USymbolicCollectionId<Key, USizeSort, Id> {
    val arrayType: ArrayType
}

/**
 * A collection id for a collection storing array lengths for arrays of a specific [arrayType].
 */
class UInputArrayLengthId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
    override val sort: USizeSort,
) : USymbolicArrayLengthId<UHeapRef, ArrayType, UInputArrayLengthId<ArrayType>> {

    override fun instantiate(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef,
        composer: UComposer<*>?
    ): UExpr<USizeSort> {
        if (composer == null) {
            return key.uctx.mkInputArrayLengthReading(collection, key)
        }

        val memory = composer.memory.toWritableMemory()
        collection.applyTo(memory, key, composer)
        return memory.read(UArrayLengthLValue(key, arrayType))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<USizeSort>, guard: UBoolExpr) {
        val lvalue = UArrayLengthLValue(key, arrayType)
        memory.write(lvalue, value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun emptyRegion(): USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun toString(): String = "length<$arrayType>()"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputArrayLengthId<*>

        return arrayType == other.arrayType
    }

    override fun hashCode(): Int = arrayType.hashCode()
}
