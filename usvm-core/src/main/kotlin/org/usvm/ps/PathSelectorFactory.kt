package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.PathsTreeStatistics
import org.usvm.util.DiscretePdf
import org.usvm.util.VanillaPriorityQueue
import kotlin.math.max
import kotlin.random.Random

sealed class PathSelectionStrategy

/**
 * Selects the states in depth-first order.
 */
object Dfs : PathSelectionStrategy()

/**
 * Selects the states in breadth-first order.
 */
object Bfs : PathSelectionStrategy()

/**
 * Selects the next state by descending from root to leaf in
 * symbolic execution tree. The child on each step is selected randomly.
 *
 * See KLEE's random path search heuristic.
 */
object RandomPath : PathSelectionStrategy()

/**
 * Gives priority to states with shorter path lengths.
 * @param random if true, states are selected randomly with distribution
 * based on path length. Otherwise, the state with the shortest path is
 * always selected.
 */
data class Depth(val random: Boolean) : PathSelectionStrategy()

/**
 * Gives priority to states with less number of forks.
 * @param random if true, states are selected randomly with distribution
 * based on number of forks. Otherwise, the state with the least number of forks is
 * always selected.
 */
data class ForkDepth(val random: Boolean) : PathSelectionStrategy()

/**
 * Gives priority to states closer to uncovered instructions in application
 * graph.
 * @param random if true, states are selected randomly with distribution
 * based on distance to uncovered instructions. Otherwise, the closest to uncovered instruction
 * state is always selected.
 */
data class ClosestToUncovered(val random: Boolean) : PathSelectionStrategy()

fun <Method, Statement, State : UState<*, *, Method, Statement>> createPathSelector(
    strategies: List<PathSelectionStrategy>,
    pathsTreeStatistics: () -> PathsTreeStatistics<Method, Statement, State>? = { null },
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    distanceStatistics: () -> DistanceStatistics<Method, Statement>? = { null },
    randomSeed: Long = System.currentTimeMillis()
) : UPathSelector<State> {
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val random by lazy { Random(randomSeed) }

    val selectors = strategies.map { strategy ->
        when (strategy) {
            Bfs -> BfsPathSelector()
            Dfs -> DfsPathSelector()
            RandomPath -> RandomTreePathSelector(
                requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for random tree path selector" },
                { random.nextInt(0, Int.MAX_VALUE) }
            )
            is Depth -> createDepthPathSelector(
                if (strategy.random) random else null
            )
            is ForkDepth -> createForkDepthPathSelector(
                requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for fork depth path selector" },
                if (strategy.random) random else null
            )
            is ClosestToUncovered -> createClosestToUncoveredPathSelector(
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for closest to uncovered path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for closest to uncovered path selector" },
                if (strategy.random) random else null
            )
        }
    }

    if (selectors.size == 1) {
        return selectors[0]
    }

    return InterleavedPathSelector(selectors)
}

private fun <State : UState<*, *, *, *>> compareById(): Comparator<State> = compareBy { it.id }

private fun <State : UState<*, *, *, *>> createDepthPathSelector(random: Random? = null): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(Comparator.naturalOrder()) }) { it.path.size }
    }

    // Notice: random never returns 1.0
    return WeightedPathSelector({ DiscretePdf(compareById()) { random.nextFloat() } }) { 1f / max(it.path.size, 1) }
}

private fun <Method, Statement, State : UState<*, *, Method, Statement>> createClosestToUncoveredPathSelector(
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    random: Random? = null
): UPathSelector<State> {
    val weighter = ShortestDistanceToTargetsStateWeighter(
        coverageStatistics.getUncoveredStatements(),
        distanceStatistics::getShortestCfgDistance,
        distanceStatistics::getShortestCfgDistanceToExitPoint
    )

    coverageStatistics.addOnCoveredObserver { _, method, statement -> weighter.removeTarget(method, statement) }

    if (random == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(Comparator.naturalOrder()) }, weighter)
    }

    return WeightedPathSelector({ DiscretePdf(compareById()) { random.nextFloat() } }) {
        1f / max(weighter.weight(it).toFloat(), 1f)
    }
}

private fun <Method, Statement, State : UState<*, *, Method, Statement>> createForkDepthPathSelector(
    pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    random: Random? = null
): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector({ VanillaPriorityQueue(Comparator.naturalOrder()) }) { pathsTreeStatistics.getStateDepth(it) }
    }

    return WeightedPathSelector({ DiscretePdf(compareById()) { random.nextFloat() } }) {
        1f / max(pathsTreeStatistics.getStateDepth(it).toFloat(), 1f)
    }
}
