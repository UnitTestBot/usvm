package org.usvm.collection.array.length

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
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.key.UHeapRefKeyInfo
import org.usvm.memory.key.USingleKeyInfo
import org.usvm.memory.DecomposedKey
import org.usvm.memory.KeyTransformer
import org.usvm.memory.UFlatUpdates
import org.usvm.memory.ULValue
import org.usvm.memory.USymbolicCollectionId
import org.usvm.memory.USymbolicCollectionIdWithContextMemory
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.memory.UWritableMemory
import org.usvm.sampleUValue

interface USymbolicArrayLengthId<Key, ArrayType, Id : USymbolicArrayLengthId<Key, ArrayType, Id>> :
    USymbolicCollectionId<Key, USizeSort, Id> {
    val arrayType: ArrayType
}

/**
 * An id for a collection storing the concretely allocated array length at heap address [address].
 */
class UAllocatedArrayLengthId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
    val address: UConcreteHeapAddress,
    override val sort: USizeSort,
    val idDefaultValue: UExpr<USizeSort>? = null,
    contextMemory: UWritableMemory<*>? = null
) : USymbolicCollectionIdWithContextMemory<Unit, USizeSort, UAllocatedArrayLengthId<ArrayType>>(contextMemory),
    USymbolicArrayLengthId<Unit, ArrayType, UAllocatedArrayLengthId<ArrayType>> {

    val defaultValue: USizeExpr by lazy {
        idDefaultValue ?: sort.sampleUValue()
    }

    override fun rebindKey(key: Unit): DecomposedKey<*, USizeSort>? = null

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = USingleKeyInfo

    override fun toString(): String = "allocatedLength<$arrayType>($address)"

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedArrayLengthId<ArrayType>, Unit, USizeSort>,
        key: Unit
    ): UExpr<USizeSort> {
        check(collection.updates.isEmpty()) { "Can't instantiate length reading from non-empty collection" }
        return defaultValue
    }

    override fun UContext.mkLValue(
        key: Unit
    ): ULValue<*, USizeSort> = UArrayLengthRef(mkConcreteHeapRef(address), arrayType)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedArrayLengthId<*>

        if (arrayType != other.arrayType) return false
        if (address != other.address) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = hash(address, arrayType, sort)

    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Unit> =
        error("This should not be called")

    override fun <Type> map(composer: UComposer<Type>): UAllocatedArrayLengthId<ArrayType> =
        error("This should not be called")

    override fun emptyRegion(): USymbolicCollection<UAllocatedArrayLengthId<ArrayType>, Unit, USizeSort> =
        error("This should not be called")
}

/**
 * A collection id for a collection storing array lengths for arrays of a specific [arrayType].
 */
class UInputArrayLengthId<ArrayType> internal constructor(
    override val arrayType: ArrayType,
    override val sort: USizeSort,
    private val defaultValue: UExpr<USizeSort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<UHeapRef, USizeSort, UInputArrayLengthId<ArrayType>>(contextMemory),
    USymbolicArrayLengthId<UHeapRef, ArrayType, UInputArrayLengthId<ArrayType>> {

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputArrayLengthId<ArrayType>, UHeapRef, USizeSort>,
        key: UHeapRef
    ): UExpr<USizeSort> = mkInputArrayLengthReading(collection, key)

    override fun UContext.mkLValue(
        key: UHeapRef
    ): ULValue<*, USizeSort> = UArrayLengthRef(key, arrayType)

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputArrayLengthId<ArrayType> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(sort.sampleUValue())
        return UInputArrayLengthId(arrayType, sort, composedDefaultValue, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, USizeSort>? = when (key) {
        is UConcreteHeapRef -> DecomposedKey(
            UAllocatedArrayLengthId(
                arrayType,
                key.address,
                sort,
                defaultValue,
                contextMemory
            ),
            Unit
        )

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
