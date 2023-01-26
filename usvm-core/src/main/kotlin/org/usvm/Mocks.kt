package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.solver.KModel

interface UMockEvaluator {
    fun eval(symbol: UMockSymbol): UExpr<USort>
}

class UIndexedMockModel(val map: Map<UMockSymbol, UExpr<USort>>): UMockEvaluator {
    override fun eval(symbol: UMockSymbol): UExpr<USort> = map.getValue(symbol)
}

interface UMocker<Method>: UMockEvaluator {
    fun call(method: Method, args: Iterable<UExpr<USort>>, sort: USort): Pair<UMockSymbol, UMocker<Method>>
    fun decode(model: KModel): UMockEvaluator
}

class UIndexedMocker<Method>(
    private val ctx: UContext,
    private val clauses: PersistentMap<Method, PersistentList<UMockSymbol>> = persistentMapOf()
)
    : UMocker<Method>
{
    override fun call(method: Method, args: Iterable<UExpr<USort>>, sort: USort): Pair<UMockSymbol, UMocker<Method>> {
        val currentClauses = clauses.getOrDefault(method, persistentListOf())
        val index = currentClauses.count()
        val const = UIndexedMethodReturnValue(ctx, method, index, sort) // TODO: create expressions via ctx
        val updatedClauses = clauses.put(method, currentClauses.add(const))
        return Pair(const, UIndexedMocker(ctx, updatedClauses))
    }

    override fun decode(model: KModel): UMockEvaluator {
        TODO("Not yet implemented")
    }

    override fun eval(symbol: UMockSymbol): UExpr<USort> = symbol
}
