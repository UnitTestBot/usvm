package org.usvm.ps

import org.usvm.*
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.PathsTreeStatistics
import org.usvm.util.DiscretePdf
import org.usvm.util.VanillaPriorityQueue
import kotlin.math.max
import kotlin.random.Random

fun <Method, Statement, State : UState<*, *, Method, Statement>> createPathSelector(
    initialState: State,
    options: MachineOptions,
    pathsTreeStatistics: () -> PathsTreeStatistics<Method, Statement, State>? = { null },
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    distanceStatistics: () -> DistanceStatistics<Method, Statement>? = { null }
) : UPathSelector<State> {
    val strategies = options.pathSelectionStrategies
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val random by lazy { Random(options.randomSeed) }

    val selectors = strategies.map { strategy ->
        when (strategy) {
            PathSelectionStrategy.BFS -> BfsPathSelector()
            PathSelectionStrategy.DFS -> DfsPathSelector()
            PathSelectionStrategy.RANDOM_PATH -> RandomTreePathSelector(
                requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for random tree path selector" },
                { random.nextInt(0, Int.MAX_VALUE) }
            )
            PathSelectionStrategy.DEPTH -> createDepthPathSelector()
            PathSelectionStrategy.DEPTH_RANDOM -> createDepthPathSelector(random)
            PathSelectionStrategy.FORK_DEPTH -> createForkDepthPathSelector(
                requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for fork depth path selector" }
            )
            PathSelectionStrategy.FORK_DEPTH_RANDOM -> createForkDepthPathSelector(
                requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for fork depth path selector" },
                random
            )
            PathSelectionStrategy.CLOSEST_TO_UNCOVERED -> createClosestToUncoveredPathSelector(
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for closest to uncovered path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for closest to uncovered path selector" }
            )
            PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM -> createClosestToUncoveredPathSelector(
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for closest to uncovered path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for closest to uncovered path selector" },
                random
            )
        }
    }

    if (selectors.size == 1) {
        val selector = selectors.single()
        selector.add(listOf(initialState))
        return selector
    }

    return when (options.pathSelectorCombinationStrategy) {
        PathSelectorCombinationStrategy.INTERLEAVED -> {
            val interleavedPathSelector = InterleavedPathSelector(selectors)
            interleavedPathSelector.add(listOf(initialState))
            interleavedPathSelector
        }
        PathSelectorCombinationStrategy.PARALLEL -> {
            selectors.first().add(listOf(initialState))
            selectors.drop(1).forEach {
                @Suppress("UNCHECKED_CAST")
                it.add(listOf(initialState.clone() as State))
            }
            ParallelPathSelector(selectors)
        }
    }
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
