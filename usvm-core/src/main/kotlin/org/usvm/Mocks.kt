package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.ksmt.expr.KExpr
import org.ksmt.solver.KModel
import org.ksmt.sort.KBoolSort
import org.ksmt.sort.KBv32Sort
import org.ksmt.sort.KIntSort

interface UMockEvaluator {
    fun eval(symbol: UMockSymbol): UExpr
}

class UIndexedMockModel(val map: Map<UMockSymbol, UExpr>): UMockEvaluator {
    override fun eval(symbol: UMockSymbol): UExpr = map.getValue(symbol)
}

interface UMocker<Method>: UMockEvaluator {
    fun call(method: Method, args: Iterable<UExpr>, sort: USort): Pair<UMockSymbol, UMocker<Method>>
    fun decode(model: KModel): UMockEvaluator
}

class UIndexedMocker<Method>(
    private val ctx: UContext,
    private val clauses: PersistentMap<Method, PersistentList<UMockSymbol>> = persistentMapOf()
)
    : UMocker<Method>
{
    override fun call(method: Method, args: Iterable<UExpr>, sort: USort): Pair<UMockSymbol, UMocker<Method>> {
        val currentClauses = clauses.getOrDefault(method, persistentListOf())
        val index = currentClauses.count()
        val const = UIndexedMethodReturnValue(ctx, method, index, sort) // TODO: create expressions via ctx
        val updatedClauses = clauses.put(method, currentClauses.add(const))
        return Pair(const, UIndexedMocker(ctx, updatedClauses))
    }

    override fun decode(model: KModel): UMockEvaluator {
        TODO("Not yet implemented")
    }

    override fun eval(symbol: UMockSymbol): UExpr = symbol
}
