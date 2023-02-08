package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.solver.KModel
import org.ksmt.utils.cast

interface UMockEvaluator {
    fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort>
}

class UIndexedMockModel(val map: Map<UMockSymbol<out USort>, UExpr<out USort>>) : UMockEvaluator {
    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> = map.getValue(symbol).cast()
}

interface UMocker<Method> : UMockEvaluator {
    fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort
    ): Pair<UMockSymbol<Sort>, UMocker<Method>>

    fun decode(model: KModel): UMockEvaluator
}

class UIndexedMocker<Method>(
    private val ctx: UContext,
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

    override fun decode(model: KModel): UMockEvaluator {
        TODO("Not yet implemented")
    }

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> = symbol
}
