package org.usvm.ps

import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.PathsTreeStatistics
import org.usvm.util.RandomizedPriorityCollection
import org.usvm.util.DeterministicPriorityCollection
import kotlin.math.max
import kotlin.random.Random

fun <Method, Statement, State : UState<*, *, Method, Statement>> createPathSelector(
    initialState: State,
    options: UMachineOptions,
    pathsTreeStatistics: () -> PathsTreeStatistics<Method, Statement, State>? = { null },
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    distanceStatistics: () -> DistanceStatistics<Method, Statement>? = { null },
    applicationGraph: () -> ApplicationGraph<Method, Statement>? = { null }
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
            PathSelectionStrategy.BFS_WITH_LOGGING -> BfsWithLoggingPathSelector(
                requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for BFS with logging path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for BFS with logging path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for BFS with logging path selector" },
                requireNotNull(applicationGraph()) { "Application graph is required for BFS with logging path selector" }
            )
            PathSelectionStrategy.INFERENCE_WITH_LOGGING -> BfsWithLoggingPathSelector(
                requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for Inference with logging path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for Inference with logging path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for Inference with logging path selector" },
                requireNotNull(applicationGraph()) { "Application graph is required for Inference with logging path selector" }
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
        return WeightedPathSelector({ DeterministicPriorityCollection(Comparator.naturalOrder()) }) { it.path.size }
    }

    // Notice: random never returns 1.0
    return WeightedPathSelector({ RandomizedPriorityCollection(compareById()) { random.nextDouble() } }) { 1.0 / max(it.path.size, 1) }
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
        return WeightedPathSelector({ DeterministicPriorityCollection(Comparator.naturalOrder()) }, weighter)
    }

    return WeightedPathSelector({ RandomizedPriorityCollection(compareById()) { random.nextDouble() } }) {
        1.0 / max(weighter.weight(it).toDouble(), 1.0)
    }
}

private fun <Method, Statement, State : UState<*, *, Method, Statement>> createForkDepthPathSelector(
    pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    random: Random? = null
): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector({ DeterministicPriorityCollection(Comparator.naturalOrder()) }) { pathsTreeStatistics.getStateDepth(it) }
    }

    return WeightedPathSelector({ RandomizedPriorityCollection(compareById()) { random.nextDouble() } }) {
        1.0 / max(pathsTreeStatistics.getStateDepth(it).toDouble(), 1.0)
    }
}
