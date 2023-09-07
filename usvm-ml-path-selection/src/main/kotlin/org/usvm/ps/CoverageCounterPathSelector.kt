package org.usvm.ps

import org.usvm.CoverageCounter
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.CoverageStatistics

class CoverageCounterPathSelector<Method, State : UState<*, Method, *, *, State>>(
    private val selector: UPathSelector<State>,
    private val coverageStatistics: CoverageStatistics<Method, *, State>,
    private val coverageCounter: CoverageCounter,
    method: Method
) : UPathSelector<State> {
    private val methodName = method.toString().dropWhile { it != ')' }.drop(1)
    private val totalStatementsCount = coverageStatistics.getUncoveredStatements().size
    private var totalCoverage = 0

    init {
        coverageCounter.addTest(methodName, totalStatementsCount.toFloat())
    }

    override fun isEmpty(): Boolean {
        return selector.isEmpty()
    }

    override fun peek(): State {
        coverageCounter.updateDiscounts(methodName)
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
        coverageCounter.updateResults(methodName, (newTotalCoverage - totalCoverage).toFloat())
        totalCoverage = newTotalCoverage
        selector.remove(state)
    }

    fun finishTest() {
        coverageCounter.finishTest(methodName)
    }

    fun savePath() {
        if (selector is FeaturesLoggingPathSelector<*, *, *>) {
            selector.savePath()
        }
    }
}
