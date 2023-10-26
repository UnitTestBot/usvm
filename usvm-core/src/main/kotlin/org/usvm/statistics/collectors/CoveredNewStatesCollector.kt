package org.usvm.statistics.collectors

import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.UMachineObserver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [UMachineObserver] which collects states if the coverage increased or if the
 * state was terminated by exception.
 *
 * @param coverageStatistics [CoverageStatistics] used to track coverage.
 * @param isException if true, state is collected regardless of coverage.
 */
class CoveredNewStatesCollector<State>(
    private val coverageStatistics: CoverageStatistics<*, *, *>,
    private val isException: (State) -> Boolean
) : StatesCollector<State> {
    private val mutableCollectedStates = mutableListOf<State>()
    override val collectedStates: List<State> = mutableCollectedStates

    private var previousCoveredStatements = coverageStatistics.getTotalCoveredStatements()

    override fun onStateTerminated(state: State, stateReachable: Boolean, isConsumed: AtomicBoolean) {
        if (!stateReachable) {
            return
        }

        if (isException(state)) {
            mutableCollectedStates.add(state)
            isConsumed.set(true)
            return
        }

        val currentCoveredStatements = coverageStatistics.getTotalCoveredStatements()
        if (currentCoveredStatements > previousCoveredStatements) {
            previousCoveredStatements = currentCoveredStatements
            mutableCollectedStates += state
            isConsumed.set(true)
        }
    }
}
