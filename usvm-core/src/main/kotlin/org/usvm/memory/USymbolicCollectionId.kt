package org.usvm.memory

import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer

typealias KeyTransformer<Key> = (Key) -> Key

/**
 * Represents any possible type of symbolic collections that can be used in symbolic memory.
 */
interface USymbolicCollectionId<Key, Sort : USort, out CollectionId : USymbolicCollectionId<Key, Sort, CollectionId>> {
    val sort: Sort

    /**
     * Performs a reading from a [collection] by a [key]. Inheritors use context heap in symbolic collection composition.
     */
    fun instantiate(
        collection: USymbolicCollection<@UnsafeVariance CollectionId, Key, Sort>,
        key: Key,
        composer: UComposer<*>?,
    ): UExpr<Sort>

    fun <Type> write(
        memory: UWritableMemory<Type>,
        key: Key,
        value: UExpr<Sort>,
        guard: UBoolExpr,
    )

    /**
     * Maps keys that belong to this collection using [transformer].
     * */
    fun <Type> keyMapper(transformer: UTransformer<Type>): KeyTransformer<Key>

    /**
     * Returns information about the key of this collection.
     * TODO: pass here context in the form of path constraints here.
     */
    fun keyInfo(): USymbolicCollectionKeyInfo<Key, *>

    fun emptyRegion(): USymbolicCollection<CollectionId, Key, Sort>
}