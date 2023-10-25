package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.usvm.merging.MergeGuard
import org.usvm.merging.UMergeable

interface UMockEvaluator {
    fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort>
}

interface UMocker<Method> : UMockEvaluator {
    fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort
    ): UMockSymbol<Sort>

    fun clone(): UMocker<Method>
}

class UIndexedMocker<Method>(
    private var clauses: PersistentMap<Method, PersistentList<UMockSymbol<out USort>>> = persistentMapOf()
) : UMocker<Method>, UMergeable<UIndexedMocker<Method>, MergeGuard> {
    override fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort
    ): UMockSymbol<Sort> {
        val currentClauses = clauses.getOrDefault(method, persistentListOf())
        val index = currentClauses.size
        val const = sort.uctx.mkIndexedMethodReturnValue(method, index, sort)
        clauses = clauses.put(method, currentClauses.add(const))
        return const
    }

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> = symbol

    override fun clone(): UIndexedMocker<Method> = UIndexedMocker(clauses)

    /**
     * Check if this [UIndexedMocker] can be merged with [other] indexed mocker.
     *
     * TODO: now the only supported case is: this internal content reference equals to other internal content.
     *
     * @return the merged indexed mocker.
     */
    override fun mergeWith(other: UIndexedMocker<Method>, by: MergeGuard): UIndexedMocker<Method>? {
        if (clauses !== other.clauses) {
            return null
        }
        return this
    }
}
