package org.usvm.machine

import io.ksmt.utils.asExpr
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jacodb.api.JcMethod
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UMockSymbol
import org.usvm.UMocker
import org.usvm.USort
import org.usvm.merging.MergeGuard
import org.usvm.uctx

class JcMocker(
    var symbols: PersistentMap<UHeapRef, PersistentList<UMockSymbol<*>>> = persistentMapOf(),
    var statics: PersistentList<UMockSymbol<*>> = persistentListOf(),
    private var clauses: PersistentMap<JcMethod, PersistentList<UMockSymbol<out USort>>> = persistentMapOf(),
) : UMocker<JcMethod> {

    override fun <Sort : USort> call(
        method: JcMethod,
        args: Sequence<UExpr<out USort>>,
        sort: Sort,
    ): UMockSymbol<Sort> {
        val currentClauses = clauses.getOrDefault(method, persistentListOf())
        val index = currentClauses.size
        val const = sort.uctx.mkIndexedMethodReturnValue(method, index, sort)

        clauses = clauses.put(method, currentClauses.add(const))

        if (method.isStatic || method.isConstructor) {
            statics = statics.add(const)
        } else {
            val instance = args.first().asExpr(sort.uctx.addressSort)
            symbols = symbols.put(instance, symbols.getOrDefault(instance, persistentListOf()).add(const))
        }

        return const
    }

    override fun clone(): UMocker<JcMethod> {
        return JcMocker(symbols, statics, clauses)
    }

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        return symbol
    }

    override fun mergeWith(other: UMocker<JcMethod>, by: MergeGuard): UMocker<JcMethod>? {
        return null
    }
}