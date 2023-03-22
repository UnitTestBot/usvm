package org.usvm.ps.statistics

import org.usvm.ApplicationGraph
import org.usvm.UState
import java.util.concurrent.ConcurrentHashMap

@Suppress("CanBeParameter")
abstract class Statistics<Method, Statement>(
    protected val graph: ApplicationGraph<Method, Statement>
) {
    private val observers: MutableSet<StatisticsObserver> = ConcurrentHashMap.newKeySet()

    private val coveredStatements: MutableSet<Statement> = hashSetOf()

    private val visitedMethods: MutableSet<Method> = hashSetOf()
    private val visitedStatements: MutableSet<Statement> = hashSetOf()

    private val writeLockForMethods = Object()
    private val writeLockForCoveredStatements = Object()
    private val writeLockForVisitedStatements = Object()

    protected abstract fun onStatementVisit(statement: Statement)

    protected abstract fun onStatementCovered(statement: Statement)

    protected abstract fun onMethodVisit(method: Method)

    fun <Type, Field> onStateVisit(state: UState<Type, Field, Method, Statement>) {
        val lastMethod = state.lastEnteredMethod

        markMethodAsVisited(lastMethod)
        onMethodVisit(lastMethod)

        val lastStatement = state.currentStatement ?: return

        markStatementAsVisited(lastStatement)
        onStatementVisit(lastStatement)
    }

    fun <Type, Field> onStateTermination(state: UState<Type, Field, Method, Statement>) {
        state.path.forEach {
            markStatementAsCovered(it)
            onStatementCovered(it)
        }
    }

    abstract fun recalculate()

    /**
     * Subscribe to this statistics to track possible updates.
     * If any occur, [notifyObserversAboutChange] will be called on the [observer].
     */
    fun subscribe(observer: StatisticsObserver) {
        observers += observer
    }

    /**
     * Remove [observer] from the list of [observers],
     * it will no longer receive information about the statistics update.
     */
    fun unsubscribe(observer: StatisticsObserver) {
        observers -= observer
    }

    /**
     * Call this function if some changes occurred in the current statistics instance.
     * Some observers may rely on statistics state and need to be updated.
     */
    protected fun notifyObserversAboutChange() {
        observers.forEach {
            it.updateOnStatisticsChange()
        }
    }

    private fun markStatementAsCovered(statement: Statement) {
        synchronized(writeLockForCoveredStatements) {
            coveredStatements += statement
        }
    }

    private fun markStatementAsVisited(statement: Statement) {
        synchronized(writeLockForVisitedStatements) {
            visitedStatements += statement
        }
    }

    private fun markMethodAsVisited(method: Method) {
        synchronized(writeLockForMethods) {
            visitedMethods += method
        }
    }
}
