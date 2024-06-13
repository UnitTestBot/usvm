package org.usvm.model

import io.ksmt.expr.KExpr
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UReadOnlyMemoryRegion


/**
 * A specific [UMemoryRegion] for one-dimensional regions generalized by a single expression of a [KeySort].
 */
class UMemory1DArray<KeySort : USort, Sort : USort> internal constructor(
    val values: PersistentMap<UExpr<KeySort>, UExpr<Sort>>,
    val constValue: UExpr<Sort>,
) : UReadOnlyMemoryRegion<KExpr<KeySort>, Sort> {

    /**
     * A constructor that is used in cases when we try to evaluate
     * an expression from a region that was never translated.
     */
    constructor(
        mappedConstValue: UExpr<Sort>,
    ) : this(persistentHashMapOf(), mappedConstValue)

    override fun read(key: KExpr<KeySort>): UExpr<Sort> = values.getOrDefault(key, constValue)
}

/**
 * A specific [UMemoryRegion] for two-dimensional regions generalized by a pair
 * of two expressions with [Key1Sort] and [Key2Sort] sorts.
 */
class UMemory2DArray<Key1Sort : USort, Key2Sort : USort, Sort : USort> internal constructor(
    val values: PersistentMap<Pair<UExpr<Key1Sort>, UExpr<Key2Sort>>, UExpr<Sort>>,
    val constValue: UExpr<Sort>,
) : UReadOnlyMemoryRegion<Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>, Sort> {
    /**
     * A constructor that is used in cases when we try to evaluate
     * an expression from a region that was never translated.
     */
    constructor(
        mappedConstValue: UExpr<Sort>,
    ) : this(persistentHashMapOf(), mappedConstValue)

    override fun read(key: Pair<KExpr<Key1Sort>, KExpr<Key2Sort>>): UExpr<Sort> {
        return values.getOrDefault(key, constValue)
    }
}
