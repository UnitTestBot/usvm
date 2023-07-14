package org.usvm.ps

import org.usvm.UState
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.PathsTreeStatistics

internal class CoverageInferencePathSelector<State : UState<*, *, Method, Statement>, Statement, Method>(
    pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    private val coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    private val applicationGraph: ApplicationGraph<Method, Statement>
) : InferencePathSelector<State, Statement, Method>(
    pathsTreeStatistics,
    coverageStatistics,
    distanceStatistics,
    applicationGraph
) {
    private var sameBlock = true

    override fun getReward(state: State): Float {
        val statement = state.currentStatement
        if (statement === null) {
            return 0.0f
        }
        if (applicationGraph.successors(statement).toList().isNotEmpty()) {
            return 0.0f
        }
        return coverageStatistics.getUncoveredStatements().toSet()
            .intersect(state.path.toSet()).size.toFloat()
    }

    override fun peek(): State {
        if (sameBlock || queue.size == 1) {
            return queue[chosenStateId]
        }
        return super.peek()
    }

    override fun update(state: State) {
        val statement = state.currentStatement
        if (statement === null) {
            return
        }
        if (applicationGraph.successors(statement).toList().size != 1) {
            sameBlock = false
        }
    }
}