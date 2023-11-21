package org.usvm.statistics.collectors

import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.UMachineObserver

/**
 * [UMachineObserver] which collects states if the coverage increased or if the
 * state was terminated by exception.
 *
 * @param coverageStatistics [CoverageStatistics] used to track coverage.
 * @param isException if true, state is collected regardless of coverage.
 */
class CoveredNewStatesCollector<State>(
    private val statesCollector: StatesCollector<State>,
    private val coverageStatistics: CoverageStatistics<*, *, *>,
    private val isException: (State) -> Boolean
) : UMachineObserver<State> {
    private var previousCoveredStatements = coverageStatistics.getTotalCoveredStatements()

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (!stateReachable) {
            return
        }

        if (isException(state)) {
            statesCollector.addState(state)
            return
        }

        val currentCoveredStatements = coverageStatistics.getTotalCoveredStatements()
        if (currentCoveredStatements > previousCoveredStatements) {
            previousCoveredStatements = currentCoveredStatements
            statesCollector.addState(state)
        }
    }
}
