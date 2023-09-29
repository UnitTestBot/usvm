package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

interface UMockEvaluator {
    fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort>
}

interface UMocker<Method> : UMockEvaluator {
    fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort
    ): Pair<UMockSymbol<Sort>, UMocker<Method>>
}

class UIndexedMocker<Method>(
    private val ctx: UContext<*>,
    private val clauses: PersistentMap<Method, PersistentList<UMockSymbol<out USort>>> = persistentMapOf()
) : UMocker<Method> {
    override fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort
    ): Pair<UMockSymbol<Sort>, UMocker<Method>> {
        val currentClauses = clauses.getOrDefault(method, persistentListOf())
        val index = currentClauses.count()
        val const = ctx.mkIndexedMethodReturnValue(method, index, sort)
        val updatedClauses = clauses.put(method, currentClauses.add(const))
        return Pair(const, UIndexedMocker(ctx, updatedClauses))
    }

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> = symbol
}
