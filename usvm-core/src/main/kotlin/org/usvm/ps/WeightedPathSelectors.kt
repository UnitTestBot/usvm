package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.*
import org.usvm.util.DiscretePdf
import org.usvm.util.VanillaPriorityQueue
import kotlin.math.max
import kotlin.random.Random

// TODO: use deterministic ids to compare states
private fun <T> compareByHash(): Comparator<T> = compareBy { it.hashCode() }

fun <State : UState<*, *, *, *>> createDepthPathSelector(random: Random? = null): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(compareByHash()) }) { it.path.size }
    }

    // NB: Random never returns 1.0!
    return WeightedPathSelector({ DiscretePdf(compareByHash()) { random.nextFloat() } }) { 1f / max(it.path.size, 1) }
}

fun <Method, Statement, State : UState<*, *, Method, Statement>> createClosestToUncoveredPathSelector(
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    statisticsObservable: StatisticsObservable<Method, Statement, State>,
    random: Random? = null
): UPathSelector<State> {
    val weighter = ShortestDistanceToTargetsStateWeighter(
        coverageStatistics.getUncoveredStatements(),
        distanceStatistics::getShortestCfgDistance,
        distanceStatistics::getShortestCfgDistanceToExitPoint
    )
    val weighterObservable = object : StatisticsObserver<Method, Statement, State> {
        override fun onStatementCovered(method: Method, statement: Statement) {
            weighter.removeTarget(method, statement)
        }
    }
    statisticsObservable += weighterObservable

    if (random == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(compareByHash()) }, weighter)
    }

    return WeightedPathSelector({ DiscretePdf(compareByHash()) { random.nextFloat() } }) {
        1f / max(weighter.weight(it).toFloat(), 1f)
    }
}

fun <Method, Statement, State : UState<*, *, Method, Statement>> createForkDepthPathSelector(
    pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    random: Random? = null
): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(compareByHash()) }) { pathsTreeStatistics.getStateDepth(it) }
    }

    return WeightedPathSelector({ DiscretePdf(compareByHash()) { random.nextFloat() } }) {
        1f / max(pathsTreeStatistics.getStateDepth(it).toFloat(), 1f)
    }
}
