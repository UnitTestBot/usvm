package org.usvm.statistics

import org.usvm.CoverageCounter
import org.usvm.UState

class CoverageCounterStatistics<State : UState<*, *, *, *, *, State>>(
    private val coverageStatistics: CoverageStatistics<*, *, State>,
    private val coverageCounter: CoverageCounter,
    private val methodName: String
) : UMachineObserver<State> {
    private val totalStatementsCount = coverageStatistics.getUncoveredStatements().size
    private var totalCoverage = 0

    init {
        coverageCounter.addTest(methodName, totalStatementsCount.toFloat())
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        coverageCounter.updateDiscounts(methodName)
    }

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (!stateReachable) return
        val newTotalCoverage = totalStatementsCount - coverageStatistics.getUncoveredStatements().size
        coverageCounter.updateResults(methodName, (newTotalCoverage - totalCoverage).toFloat())
        totalCoverage = newTotalCoverage
    }
}
