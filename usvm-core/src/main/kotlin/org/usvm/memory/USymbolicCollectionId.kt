package org.usvm.memory

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.uctx

typealias KeyTransformer<Key> = (Key) -> Key
typealias KeyMapper<Key, MappedKey> = (Key) -> MappedKey?

data class DecomposedKey<Key, Sort : USort>(val collectionId: USymbolicCollectionId<Key, Sort, *>, val key: Key)

/**
 * Represents any possible type of symbolic collections that can be used in symbolic memory.
 */
interface USymbolicCollectionId<Key, Sort : USort, out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> {
    val sort: Sort

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

            // transformedKey belongs to symbolic collection with expectedId.
            if (decomposedKey == null) {
                @Suppress("UNCHECKED_CAST")
                return@filter transformedKey as MappedKey
            }

            if (decomposedKey.collectionId != expectedId)
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

    fun emptyRegion(): USymbolicCollection<CollectionId, Key, Sort>
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
        val lValue = sort.uctx.mkLValue(key)
        contextMemory.read(lValue)
    }

    abstract fun UContext.mkReading(
        collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>,
        key: Key
    ): UExpr<Sort>

    abstract fun UContext.mkLValue(key: Key): ULValue<*, Sort>

    override fun <Type> write(memory: UWritableMemory<Type>, key: Key, value: UExpr<Sort>, guard: UBoolExpr) {
        val lValue = guard.uctx.mkLValue(key)
        memory.write(lValue, value, guard)
    }
}
