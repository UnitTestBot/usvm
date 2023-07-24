package org.usvm.ps

import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.statistics.PathsTreeStatistics
import org.usvm.util.DeterministicPriorityCollection
import org.usvm.util.RandomizedPriorityCollection
import kotlin.math.max
import kotlin.random.Random

fun <Method, Statement, State : UState<*, *, Method, Statement>> createPathSelector(
    initialState: State,
    options: UMachineOptions,
    pathsTreeStatistics: () -> PathsTreeStatistics<Method, Statement, State>? = { null },
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    distanceStatistics: () -> DistanceStatistics<Method, Statement>? = { null },
): UPathSelector<State> {
    val strategies = options.pathSelectionStrategies
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val random by lazy { Random(options.randomSeed) }

    val selectors = strategies.map { strategy ->
        when (strategy) {
            PathSelectionStrategy.BFS -> BfsPathSelector()
            PathSelectionStrategy.DFS -> DfsPathSelector()
            PathSelectionStrategy.RANDOM_PATH -> RandomTreePathSelector(
                pathsTreeStatistics = requireNotNull(pathsTreeStatistics()) { "Paths tree statistics is required for random tree path selector" },
                randomNonNegativeInt = { random.nextInt(0, Int.MAX_VALUE) }
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

    val propagateExceptions = options.exceptionsPropagation

    selectors.singleOrNull()?.let { selector ->
        val resultSelector = selector.wrapIfRequired(propagateExceptions)
        resultSelector.add(listOf(initialState))
        return resultSelector
    }

    require(selectors.size >= 2) { "Cannot create collaborative path selector from less than 2 selectors" }

    val selector = when (options.pathSelectorCombinationStrategy) {
        PathSelectorCombinationStrategy.INTERLEAVED -> {
            // Since all selectors here work as one, we can wrap an interleaved selector only.
            val interleavedPathSelector = InterleavedPathSelector(selectors).wrapIfRequired(propagateExceptions)
            interleavedPathSelector.add(listOf(initialState))
            interleavedPathSelector
        }

        PathSelectorCombinationStrategy.PARALLEL -> {
            // Here we should wrap all selectors independently since they work in parallel.
            val wrappedSelectors = selectors.map { it.wrapIfRequired(propagateExceptions) }

            wrappedSelectors.first().add(listOf(initialState))
            wrappedSelectors.drop(1).forEach {
                @Suppress("UNCHECKED_CAST")
                it.add(listOf(initialState.clone() as State))
            }

            ParallelPathSelector(wrappedSelectors)
        }
    }

    return selector
}

/**
 * Wraps the selector into an [ExceptionPropagationPathSelector] if [propagateExceptions] is true.
 */
private fun <State : UState<*, *, *, *>> UPathSelector<State>.wrapIfRequired(propagateExceptions: Boolean) =
    if (propagateExceptions && this !is ExceptionPropagationPathSelector<State>) {
        ExceptionPropagationPathSelector(this)
    } else {
        this
    }

private fun <State : UState<*, *, *, *>> compareById(): Comparator<State> = compareBy { it.id }

private fun <State : UState<*, *, *, *>> createDepthPathSelector(random: Random? = null): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { it.path.size }
        )
    }

    // Notice: random never returns 1.0
    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(it.path.size, 1) }
    )
}

private fun <Method, Statement, State : UState<*, *, Method, Statement>> createClosestToUncoveredPathSelector(
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    random: Random? = null,
): UPathSelector<State> {
    val weighter = ShortestDistanceToTargetsStateWeighter(
        targets = coverageStatistics.getUncoveredStatements(),
        getCfgDistance = distanceStatistics::getShortestCfgDistance,
        getCfgDistanceToExitPoint = distanceStatistics::getShortestCfgDistanceToExitPoint
    )

    coverageStatistics.addOnCoveredObserver { _, method, statement -> weighter.removeTarget(method, statement) }

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = weighter
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(weighter.weight(it).toDouble(), 1.0) }
    )
}

private fun <Method, Statement, State : UState<*, *, Method, Statement>> createForkDepthPathSelector(
    pathsTreeStatistics: PathsTreeStatistics<Method, Statement, State>,
    random: Random? = null,
): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { pathsTreeStatistics.getStateDepth(it) }
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(pathsTreeStatistics.getStateDepth(it).toDouble(), 1.0) }
    )
}
