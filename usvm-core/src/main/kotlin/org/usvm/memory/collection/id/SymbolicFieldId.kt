package org.usvm.memory.collection.id

import io.ksmt.cache.hash
import io.ksmt.utils.sampleValue
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.memory.collection.region.UFieldRef
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.UFlatUpdates
import org.usvm.memory.collection.key.UHeapRefKeyInfo
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo

interface USymbolicFieldId<Field, Key, Sort : USort, out FieldId : USymbolicFieldId<Field, Key, Sort, FieldId>> :
    USymbolicCollectionId<Key, Sort, FieldId> {
    val field: Field
}

/**
 * An id for a collection storing the concretely allocated [field] at heap address [address].
 */
data class UAllocatedFieldId<Field, Sort : USort> internal constructor(
    override val field: Field,
    val address: UConcreteHeapAddress,
    override val sort: Sort
) : USymbolicFieldId<Field, Unit, Sort, UAllocatedFieldId<Field, Sort>> {
    override val defaultValue: UExpr<Sort> = sort.sampleValue()

    override fun <R> accept(visitor: UCollectionIdVisitor<R>) =
        visitor.visit(this)

    override fun rebindKey(key: Unit): DecomposedKey<*, Sort>? = null

    override fun toString(): String = "allocatedField<$field>($address)"

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

    override fun keyInfo(): USymbolicCollectionKeyInfo<Unit, *> =
        error("This should not be called")

    override fun instantiate(
        collection: USymbolicCollection<UAllocatedFieldId<Field, Sort>, Unit, Sort>,
        key: Unit
    ): UExpr<Sort> {
        error("This should not be called")
    }
}

/**
 * An id for a collection storing the specific [field].
 */
class UInputFieldId<Field, Sort : USort> internal constructor(
    override val field: Field,
    override val sort: Sort,
    contextMemory: UWritableMemory<*>?,
) : USymbolicCollectionIdWithContextMemory<UHeapRef, Sort, UInputFieldId<Field, Sort>>(contextMemory),
    USymbolicFieldId<Field, UHeapRef, Sort, UInputFieldId<Field, Sort>> {

    override val defaultValue: UExpr<Sort>? get() = null

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

    override fun <Type> map(composer: UComposer<Type>): UInputFieldId<Field, Sort> =
        UInputFieldId(field, sort, composer.memory.toWritableMemory())

    override fun keyInfo() = UHeapRefKeyInfo

    override fun rebindKey(key: UHeapRef): DecomposedKey<*, Sort>? =
        when (key) {
            is UConcreteHeapRef -> DecomposedKey(UAllocatedFieldId(field, key.address, sort), Unit)
            else -> null
        }

    override fun <R> accept(visitor: UCollectionIdVisitor<R>): R =
        visitor.visit(this)

    fun emptyRegion(): USymbolicCollection<UInputFieldId<Field, Sort>, UHeapRef, Sort> =
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
