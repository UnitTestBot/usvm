package org.usvm.memory.collection.id

import io.ksmt.cache.hash
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.UFlatUpdates
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.key.UHeapRefKeyInfo
import org.usvm.memory.collection.key.USingleKeyInfo
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.memory.collection.region.UFieldRef
import org.usvm.sampleUValue

interface USymbolicFieldId<Field, Key, Sort : USort, out FieldId : USymbolicFieldId<Field, Key, Sort, FieldId>> :
    USymbolicCollectionId<Key, Sort, FieldId> {
    val field: Field
}

/**
 * An id for a collection storing the concretely allocated [field] at heap address [address].
 */
class UAllocatedFieldId<Field, Sort : USort> internal constructor(
    override val field: Field,
    val address: UConcreteHeapAddress,
    override val sort: Sort,
    val defaultValue: UExpr<Sort> = sort.sampleUValue(),
    contextMemory: UWritableMemory<*>? = null
) : USymbolicCollectionIdWithContextMemory<Unit, Sort, UAllocatedFieldId<Field, Sort>>(contextMemory),
    USymbolicFieldId<Field, Unit, Sort, UAllocatedFieldId<Field, Sort>> {
    override fun rebindKey(key: Unit): DecomposedKey<*, Sort>? = null

    override fun toString(): String = "allocatedField<$field>($address)"

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = USingleKeyInfo

    override fun UContext.mkReading(
        collection: USymbolicCollection<UAllocatedFieldId<Field, Sort>, Unit, Sort>,
        key: Unit
    ): UExpr<Sort> {
        check(collection.updates.isEmpty()) { "Can't instantiate allocated field reading from non-empty collection" }
        return defaultValue
    }

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UAllocatedFieldId<Field, Sort>, Unit, Sort>,
        key: Unit
    ): ULValue<*, Sort> = UFieldRef(sort, mkConcreteHeapRef(address), field)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UAllocatedFieldId<*, *>

        if (field != other.field) return false
        if (address != other.address) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = hash(field, address, sort)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: Unit,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        error("This should not be called")
    }

    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Unit> =
        error("This should not be called")

    override fun <Type> map(composer: UComposer<Type>): UAllocatedFieldId<Field, Sort> =
        error("This should not be called")

    override fun emptyRegion(): USymbolicCollection<UAllocatedFieldId<Field, Sort>, Unit, Sort> =
        error("This should not be called")
}

/**
 * An id for a collection storing the specific [field].
 */
class UInputFieldId<Field, Sort : USort> internal constructor(
    override val field: Field,
    override val sort: Sort,
    private val defaultValue: UExpr<Sort>? = null,
    contextMemory: UWritableMemory<*>? = null,
) : USymbolicCollectionIdWithContextMemory<UHeapRef, Sort, UInputFieldId<Field, Sort>>(contextMemory),
    USymbolicFieldId<Field, UHeapRef, Sort, UInputFieldId<Field, Sort>> {

    override fun UContext.mkReading(
        collection: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef
    ): UExpr<Sort> = mkInputFieldReading(collection, key)

    override fun UContext.mkLValue(
        collection: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef
    ): ULValue<*, Sort> = UFieldRef(sort, key, field)

    override fun <Type> write(
        memory: UWritableMemory<Type>,
        key: UHeapRef,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        memory.write(UFieldRef(sort, key, field), value, guard)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

    override fun <Type> map(composer: UComposer<Type>): UInputFieldId<Field, Sort> {
        check(contextMemory == null) { "contextMemory is not null in composition" }
        val composedDefaultValue = composer.compose(sort.sampleUValue())
        return UInputFieldId(field, sort, composedDefaultValue, composer.memory.toWritableMemory())
    }

    override fun keyInfo() = UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, Sort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(
                UAllocatedFieldId(
                    field,
                    key.address,
                    sort,
                    defaultValue ?: sort.sampleUValue(),
                    contextMemory
                ),
                Unit
            )
            else -> null
        }

    override fun emptyRegion(): USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort> =
        USymbolicCollection(this, UFlatUpdates(keyInfo()))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UInputFieldId<*, *>

        if (field != other.field) return false
        if (sort != other.sort) return false

        return true
    }

    override fun hashCode(): Int = hash(field, sort)

    override fun toString(): String = "inputField<$field>()"
}
