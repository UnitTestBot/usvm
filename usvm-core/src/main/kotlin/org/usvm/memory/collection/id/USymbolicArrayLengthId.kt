package org.usvm.memory.collection.id

import io.ksmt.utils.sampleValue
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.UTransformer
import org.usvm.isTrue
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.UFlatUpdates
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.key.UHeapRefKeyInfo
import org.usvm.memory.collection.key.UNoKeyInfo
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.memory.collection.region.UArrayLengthRef

interface USymbolicArrayLengthId<Key, ArrayType, Id : USymbolicArrayLengthId<Key, ArrayType, Id>> :
    USymbolicCollectionId<Key, USizeSort, Id> {
    val arrayType: ArrayType
}

/**
 * An id for a collection storing the concretely allocated array length at heap address [address].
 */
data class UAllocatedArrayLengthId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
    val address: UConcreteHeapAddress,
    override val sort: USizeSort
) : USymbolicArrayLengthId<Unit, ArrayType, UAllocatedArrayLengthId<ArrayType>> {
    override val defaultValue: UExpr<USizeSort> = sort.sampleValue()

    override fun rebindKey(key: Unit): DecomposedKey<*, USizeSort>? = null

    override fun toString(): String = "allocatedLength<$arrayType>($address)"

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: Unit,
        value: UExpr<USizeSort>,
        guard: UBoolExpr
    ) {
        error("This should not be called")
    }

    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Unit> =
        error("This should not be called")

    override fun <Type> map(composer: UComposer<Type>): UAllocatedArrayLengthId<ArrayType> =
        error("This should not be called")

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = UNoKeyInfo

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedArrayLengthId<ArrayType>, Unit, USizeSort>,
        key: Unit
    ): UExpr<USizeSort> = error("This should not be called")

    override fun emptyRegion(): USymbolicCollection<UAllocatedArrayLengthId<ArrayType>, Unit, USizeSort> =
        error("This should not be called")
}

/**
 * A collection id for a collection storing array lengths for arrays of a specific [arrayType].
 */
class UInputArrayLengthId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
    override val sort: USizeSort,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<UHeapRef, USizeSort, UInputArrayLengthId<ArrayType>>(contextMemory),
    USymbolicArrayLengthId<UHeapRef, ArrayType, UInputArrayLengthId<ArrayType>> {
    override val defaultValue: UExpr<USizeSort>? get() = null

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = mkInputArrayLengthReading(collection, key)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): ULValue<*, USizeSort> = UArrayLengthRef(sort, key, arrayType)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UHeapRef,
        value: UExpr<USizeSort>,
        guard: UBoolExpr,
    ) {
        assert(guard.isTrue)
        memory.write(UArrayLengthRef(sort, key, arrayType), value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputArrayLengthId<ArrayType> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        return UInputArrayLengthId(arrayType, sort, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? = when (key) {
        is UConcreteHeapRef -> DecomposedKey(UAllocatedArrayLengthId(arrayType, key.address, sort), Unit)
        else -> null
    }

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