package org.usvm.ps

import org.usvm.CoverageCounter
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.CoverageStatistics

class CoverageCounterPathSelector <Method, State : UState<*, Method, *, *, State>>(
    private val selector: UPathSelector<State>,
    private val coverageStatistics: CoverageStatistics<Method, *, State>,
    method: Method
) : UPathSelector<State> {
    private val methodName = method.toString().dropWhile { it != ')' }.drop(1)
    private val totalStatementsCount = coverageStatistics.getUncoveredStatements().size
    private var totalCoverage = 0

    init {
        CoverageCounter.addTest(methodName, totalStatementsCount.toFloat())
    }

    override fun isEmpty(): Boolean {
        return selector.isEmpty()
    }

    override fun peek(): State {
        CoverageCounter.updateDiscounts(methodName)
        return selector.peek()
    }

    override fun update(state: State) {
        selector.update(state)
    }

    override fun add(states: Collection<State>) {
        selector.add(states)
    }

    override fun remove(state: State) {
        val newTotalCoverage = totalStatementsCount - coverageStatistics.getUncoveredStatements().size
        CoverageCounter.updateResults(methodName, (newTotalCoverage - totalCoverage).toFloat())
        totalCoverage = newTotalCoverage
        selector.remove(state)
    }

    fun finishTest() {
        CoverageCounter.finishTest(methodName)
    }

    fun savePath() {
        if (selector is BfsWithLoggingPathSelector<*, *, *>) {
            selector.savePath()
        }
    }
}
