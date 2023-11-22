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

interface MockLiteral

interface UMocker<Method> : UMockEvaluator {
    fun <Sort : USort> call(
        method: Method,
        args: Sequence<UExpr<out USort>>,
        sort: Sort,
    ): UMockSymbol<Sort>
    val trackedLiterals: Collection<MockLiteral>

    fun <Sort : USort> createMockSymbol(trackLiteral: MockLiteral?, sort: Sort): UExpr<Sort>

    fun getTrackedExpression(trackLiteral: MockLiteral): UExpr<USort>

    fun clone(): UMocker<Method>
}

class UIndexedMocker<Method>(
    private var methodMockClauses: PersistentMap<Method, PersistentList<UMockSymbol<out USort>>> = persistentHashMapOf(),
    private var trackedSymbols: PersistentMap<MockLiteral, UExpr<out USort>> = persistentHashMapOf(),
    private var untrackedSymbols: PersistentList<UExpr<out USort>> = persistentListOf(),
) : UMocker<Method>, UMergeable<UIndexedMocker<Method>, MergeGuard> {
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

    override val trackedLiterals: Collection<MockLiteral>
        get() = trackedSymbols.keys

    override fun <Sort : USort> createMockSymbol(trackLiteral: MockLiteral?, sort: Sort): UExpr<Sort> {
        val const = sort.uctx.mkTrackedMockSymbol(sort)

        if (trackLiteral != null) {
            // TODO check for duplicates
            trackedSymbols = trackedSymbols.put(trackLiteral, const)
        } else {
            untrackedSymbols = untrackedSymbols.add(const)
        }

        return const
    }

    override fun getTrackedExpression(trackLiteral: MockLiteral): UExpr<USort> {
        if (trackLiteral !in trackedSymbols) error("Access by unregistered track literal $trackLiteral")

        return trackedSymbols.getValue(trackLiteral).cast()
    }

    override fun clone(): UIndexedMocker<Method> = UIndexedMocker(methodMockClauses, trackedSymbols)

    /**
     * Check if this [UIndexedMocker] can be merged with [other] indexed mocker.
     *
     * TODO: now the only supported case is: this internal content reference equals to other internal content.
     *
     * @return the merged indexed mocker.
     */
    override fun mergeWith(other: UIndexedMocker<Method>, by: MergeGuard): UIndexedMocker<Method>? {
        if (methodMockClauses !== other.methodMockClauses
            || trackedSymbols !== other.trackedSymbols
            || untrackedSymbols !== other.untrackedSymbols
        ) {
            return null
        }

        return this
    }
}
