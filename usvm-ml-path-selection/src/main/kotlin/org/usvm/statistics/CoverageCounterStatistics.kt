package org.usvm.statistics

import org.usvm.CoverageCounter
import org.usvm.UState

class CoverageCounterStatistics<State : UState<*, *, *, *, *, State>>(
    private val coverageStatistics: CoverageStatistics<*, *, State>,
    private val coverageCounter: CoverageCounter,
    private val methodFullName: String
) : UMachineObserver<State> {
    private val totalStatementsCount = coverageStatistics.getUncoveredStatements().size
    private var totalCoverage = 0

    init {
        coverageCounter.addTest(methodFullName, totalStatementsCount.toFloat())
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        coverageCounter.updateDiscounts(methodFullName)
    }

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (!stateReachable) return
        val newTotalCoverage = totalStatementsCount - coverageStatistics.getUncoveredStatements().size
        coverageCounter.updateResults(methodFullName, (newTotalCoverage - totalCoverage).toFloat())
        totalCoverage = newTotalCoverage
    }
}
