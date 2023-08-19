package org.usvm.memory.collection.id

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.collection.USymbolicCollection
import org.usvm.memory.collection.key.USymbolicCollectionKeyInfo
import org.usvm.uctx
import org.usvm.util.Region

typealias KeyTransformer<Key> = (Key) -> Key
typealias KeyMapper<Key, MappedKey> = (Key) -> MappedKey?

data class DecomposedKey<Key, Sort : USort>(val collectionId: USymbolicCollectionId<Key, Sort, *>, val key: Key)

/**
 * Represents any possible type of symbolic collections that can be used in symbolic memory.
 */
interface USymbolicCollectionId<Key, Sort : USort, out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> {
    val sort: Sort
    val defaultValue: UExpr<Sort>?

    /**
     * Performs a reading from a [collection] by a [key]. Inheritors use context heap in symbolic collection composition.
     */
    fun instantiate(collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>, key: Key): UExpr<Sort>

    fun <Type> write(
        memory: UWritableMemory<Type>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    )

    fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Key>

    fun <Type, MappedKey> keyFilterMapper(
        transformer: UTransformer<Type>,
        expectedId: USymbolicCollectionId<MappedKey, Sort, *>
    ): KeyMapper<Key, MappedKey> {
        val mapper = keyMapper(transformer)
        return filter@{
            val transformedKey = mapper(it)
            val decomposedKey = rebindKey(transformedKey)
            if (decomposedKey == null || decomposedKey.collectionId != expectedId)
                return@filter null
            @Suppress("UNCHECKED_CAST")
            return@filter decomposedKey.key as MappedKey
        }
    }

    fun <Type> map(composer: UComposer<Type>): CollectionId

    /**
     * Checks that [key] still belongs to symbolic collection with this id. If yes, then returns null.
     * If [key] belongs to some new memory region, returns lvalue for this new region.
     * The implementation might assume that [key] is obtained by [keyMapper] from some key of symbolic collection with this id.
     */
    fun rebindKey(key: Key): DecomposedKey<*, Sort>?

    /**
     * Returns information about the key of this collection.
     * TODO: pass here context in the form of path constraints here.
     */
    fun keyInfo(): USymbolicCollectionKeyInfo<Key, *>

    fun <R> accept(visitor: UCollectionIdVisitor<R>): R
}

interface UCollectionIdVisitor<R> {
    fun <Key, Sort : USort, CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> visit(
        collectionId: USymbolicCollectionId<Key, Sort, CollectionId>
    ): Any? = error("You must provide visit implementation for ${collectionId::class} in ${this::class}")

    fun <Field, Sort : USort> visit(collectionId: UInputFieldId<Field, Sort>): R

    fun <Field, Sort : USort> visit(collectionId: UAllocatedFieldId<Field, Sort>): R

    fun <ArrayType, Sort : USort> visit(collectionId: UAllocatedArrayId<ArrayType, Sort>): R

    fun <ArrayType, Sort : USort> visit(collectionId: UInputArrayId<ArrayType, Sort>): R

    fun <ArrayType> visit(collectionId: UInputArrayLengthId<ArrayType>): R

    fun <MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> visit(collectionId: UAllocatedSymbolicMapId<MapType, KeySort, ValueSort, Reg>): R

    fun <MapType, KeySort : USort, ValueSort : USort, Reg : Region<Reg>> visit(collectionId: UInputSymbolicMapId<MapType, KeySort, ValueSort, Reg>): R

    fun <MapType> visit(collectionId: UInputSymbolicMapLengthId<MapType>): R
}

abstract class USymbolicCollectionIdWithContextMemory<
        Key, Sort : USort,
        out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>>(
    val contextMemory: UWritableMemory<*>?,
) : USymbolicCollectionId<Key, Sort, CollectionId> {

    override fun instantiate(
        collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>,
        key: Key
    ): UExpr<Sort> = if (contextMemory == null) {
        sort.uctx.mkReading(collection, key)
    } else {
        collection.applyTo(contextMemory)
        val lValue = sort.uctx.mkLValue(collection, key)
        contextMemory.read(lValue)
    }

    abstract fun UContext.mkReading(
        collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>,
        key: Key
    ): UExpr<Sort>

    abstract fun UContext.mkLValue(
        collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>,
        key: Key
    ): ULValue<*, Sort>
}
