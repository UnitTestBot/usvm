package org.usvm.collection.field

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
import org.usvm.uctx

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
    val idDefaultValue: UExpr<Sort>? = null,
) : USymbolicFieldId<Field, Unit, Sort, UAllocatedFieldId<Field, Sort>> {
    val defaultValue: UExpr<Sort> by lazy { idDefaultValue ?: sort.sampleUValue() }

//    override fun rebindKey(key: Unit): DecomposedKey<*, Sort>? = null

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedFieldId<Field, Sort>, Unit, Sort>,
        key: Unit,
        transformer: UComposer<*>?
    ): UExpr<Sort> {
        check(collection.updates.isEmpty()) { "Can't instantiate allocated field reading from non-empty collection" }
        return transformer?.let { defaultValue.accept(it) } ?: defaultValue
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: Unit, value: UExpr<Sort>, guard: UBoolExpr) {
        val lvalue = UFieldLValue(sort, value.uctx.mkConcreteHeapRef(address), field)
        memory.write(lvalue, value, guard)
    }

    override fun toString(): String = "allocatedField<$field>($address)"

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> = USingleKeyInfo

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


    override fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Unit> =
        error("This should not be called")

//    override fun <Type> map(composer: UComposer<Type>): UAllocatedFieldId<Field, Sort> =
//        error("This should not be called")

    override fun emptyRegion(): USymbolicCollection<UAllocatedFieldId<Field, Sort>, Unit, Sort> =
        error("This should not be called")
}

/**
 * An id for a collection storing the specific [field].
 */
class UInputFieldId<Field, Sort : USort> internal constructor(
    override val field: Field,
    override val sort: Sort,
) : USymbolicFieldId<Field, UHeapRef, Sort, UInputFieldId<Field, Sort>> {

    override fun instantiate(
        collection: USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort>,
        key: UHeapRef,
        transformer: UComposer<*>?,
    ): UExpr<Sort> {
        if (transformer == null) {
            return key.uctx.mkInputFieldReading(collection, key)
        }
        val memory = transformer.memory.toWritableMemory()
        collection.applyTo(memory, composer)
        return memory.read(UFieldLValue(sort, key, field))
    }

    override fun <Type> write(memory: UWritableMemory<Type>, key: UHeapRef, value: UExpr<Sort>, guard: UBoolExpr) {
        UFieldLValue(sort, key, field)
    }

    override fun <Type> keyMapper(
        transformer: UTransformer<Type>,
    ): KeyTransformer<UHeapRef> = { transformer.apply(it) }

//    override fun <Type> map(composer: UComposer<Type>): UInputFieldId<Field, Sort> {
//        check(contextMemory == null) { "contextMemory is not null in composition" }
//        val composedDefaultValue = composer.compose(sort.sampleUValue())
//        return UInputFieldId(field, sort, composedDefaultValue, composer.memory.toWritableMemory())
//    }

    override fun keyInfo() = UHeapRefKeyInfo

//    override fun rebindKey(key: UHeapRef): DecomposedKey<*, Sort>? =
//        when (key) {
//            is UConcreteHeapRef -> DecomposedKey(
//                UAllocatedFieldId(field, key.address, sort, defaultValue),
//                Unit
//            )
//
//            else -> null
//        }

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
