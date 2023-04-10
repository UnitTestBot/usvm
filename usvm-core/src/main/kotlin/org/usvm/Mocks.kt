package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.utils.asExpr

interface UMockEvaluator {
    fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort>
}

/**
 * A model for an indexed mocker that stores mapping
 * from mock symbols and invocation indices to expressions.
 */
class UIndexedMockModel<Method>(
    private val values: Map<Pair<*, Int>, UExpr<*>>,
) : UMockEvaluator {

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)

        val sort = symbol.sort
        @Suppress("UNCHECKED_CAST")
        val key = symbol.method as Method to symbol.callIndex

        return values.getOrDefault(key, sort.sampleValue()).asExpr(sort)
    }
}

interface UMocker<Method> : UMockEvaluator {
    fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort
    ): Pair<UMockSymbol<Sort>, UMocker<Method>>
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

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> = symbol
}
