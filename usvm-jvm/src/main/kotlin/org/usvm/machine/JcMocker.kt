package org.usvm.machine

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jacodb.api.JcMethod
import org.usvm.TrackedLiteral
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
    private var trackedSymbols: PersistentMap<TrackedLiteral, UExpr<out USort>> = persistentHashMapOf(),
    private var untrackedSymbols: PersistentList<UExpr<out USort>> = persistentListOf(),
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
        return JcMocker(symbols, statics, clauses, trackedSymbols, untrackedSymbols)
    }

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        return symbol
    }

    override fun mergeWith(other: UMocker<JcMethod>, by: MergeGuard): UMocker<JcMethod>? {
        if (this === other) return this

        // todo: merge
        return null
    }

    override val trackedLiterals: Collection<TrackedLiteral>
        get() = trackedSymbols.keys

    /**
     * Creates a mock symbol. If [trackedLiteral] is not null, created expression
     * can be retrieved later by this [trackedLiteral] using [getTrackedExpression] method.
     */
    override fun <Sort : USort> createMockSymbol(trackedLiteral: TrackedLiteral?, sort: Sort): UExpr<Sort> {
        val const = sort.uctx.mkTrackedSymbol(sort)

        if (trackedLiteral != null) {
            trackedSymbols = trackedSymbols.put(trackedLiteral, const)
        } else {
            untrackedSymbols = untrackedSymbols.add(const)
        }

        return const
    }

    override fun getTrackedExpression(trackedLiteral: TrackedLiteral): UExpr<USort> {
        if (trackedLiteral !in trackedSymbols) error("Access by unregistered track literal $trackedLiteral")

        return trackedSymbols.getValue(trackedLiteral).cast()
    }
}
