package org.usvm

import io.ksmt.utils.cast
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import org.usvm.merging.MergeGuard
import org.usvm.merging.UMergeable

interface UMockEvaluator {
    fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort>
}

interface TrackedLiteral

interface UMocker<Method> : UMockEvaluator, UMergeable<UMocker<Method>, MergeGuard> {
    fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort,
    ): UMockSymbol<Sort>
    val trackedLiterals: Collection<TrackedLiteral>

    fun <Sort : USort> createMockSymbol(trackedLiteral: TrackedLiteral?, sort: Sort): UExpr<Sort>

    fun getTrackedExpression(trackedLiteral: TrackedLiteral): UExpr<USort>

    fun clone(): UMocker<Method>
}

class UIndexedMocker<Method>(
    private var methodMockClauses: PersistentMap<Method, PersistentList<UMockSymbol<out USort>>> = persistentHashMapOf(),
    private var trackedSymbols: PersistentMap<TrackedLiteral, UExpr<out USort>> = persistentHashMapOf(),
    private var untrackedSymbols: PersistentList<UExpr<out USort>> = persistentListOf(),
) : UMocker<Method>{
    override fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort,
    ): UMockSymbol<Sort> {
        val currentClauses = methodMockClauses.getOrDefault(method, persistentListOf())
        val index = currentClauses.size
        val const = sort.uctx.mkIndexedMethodReturnValue(method, index, sort)
        methodMockClauses = methodMockClauses.put(method, currentClauses.add(const))

        return const
    }

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> = symbol

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

    override fun clone(): UIndexedMocker<Method> = UIndexedMocker(methodMockClauses, trackedSymbols, untrackedSymbols)

    /**
     * Check if this [UIndexedMocker] can be merged with [other] indexed mocker.
     *
     * TODO: now the only supported case is: this internal content reference equals to other internal content.
     *
     * @return the merged indexed mocker.
     */
    override fun mergeWith(other: UMocker<Method>, by: MergeGuard): UIndexedMocker<Method>? {
        if (other !is UIndexedMocker<Method>) return null

        if (methodMockClauses !== other.methodMockClauses
            || trackedSymbols !== other.trackedSymbols
            || untrackedSymbols !== other.untrackedSymbols
        ) {
            return null
        }

        return this
    }
}
