package org.usvm.ps

import org.usvm.*
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.RandomizedPriorityCollection
import kotlin.math.max
import kotlin.random.Random

fun <Method, Statement, State : UState<*, Method, Statement, *, *, State>> createPathSelector(
    initialState: State,
    options: UMachineOptions,
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
                // Initial state is the first `real` node, not the root.
                root = requireNotNull(initialState.pathLocation.parent),
                randomNonNegativeInt = { random.nextInt(0, Int.MAX_VALUE) }
            )

            PathSelectionStrategy.DEPTH -> createDepthPathSelector()
            PathSelectionStrategy.DEPTH_RANDOM -> createDepthPathSelector(random)
            PathSelectionStrategy.FORK_DEPTH -> createForkDepthPathSelector()
            PathSelectionStrategy.FORK_DEPTH_RANDOM -> createForkDepthPathSelector(random)
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
                it.add(listOf(initialState.clone()))
            }

            ParallelPathSelector(wrappedSelectors)
        }
    }

    return selector
}

fun <Method, Statement, Target : UTarget<Method, Statement, Target, State>, State : UState<*, *, Method, Statement, *, Target, State>> createTargetReproductionPathSelector(
    initialState: State,
    options: TargetReproductionOptions,
    distanceStatistics: DistanceStatistics<Method, Statement>
): UPathSelector<State> {
    val random =
        when(options.pathSelectionStrategy) {
            TargetReproductionPathSelectionStrategy.RANDOMIZED -> Random(options.randomSeed)
            TargetReproductionPathSelectionStrategy.DETERMINISTIC -> null
        }
    val selector = createClosestToTargetsPathSelector<Method, Statement, Target, State>(distanceStatistics, random)
    selector.add(listOf(initialState))
    return selector
}

/**
 * Wraps the selector into an [ExceptionPropagationPathSelector] if [propagateExceptions] is true.
 */
private fun <State : UState<*, *, *, *, *, State>> UPathSelector<State>.wrapIfRequired(propagateExceptions: Boolean) =
    if (propagateExceptions && this !is ExceptionPropagationPathSelector<State>) {
        ExceptionPropagationPathSelector(this)
    } else {
        this
    }

private fun <State : UState<*, *, *, *, *, *, State>> compareById(): Comparator<State> = compareBy { it.id }

private fun <State : UState<*, *, *, *, *, *, State>> createDepthPathSelector(random: Random? = null): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { it.pathLocation.depth }
        )
    }

    // Notice: random never returns 1.0
    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(it.pathLocation.depth, 1) }
    )
}

private fun <Method, Statement, State : UState<*, Method, Statement, *, *, State>> createClosestToUncoveredPathSelector(
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    distanceStatistics: DistanceStatistics<Method, Statement>,
    random: Random? = null,
): UPathSelector<State> {
    val distanceCalculator = RoughIterprocShortestDistanceCalculator(
        targets = coverageStatistics.getUncoveredStatements(),
        getCfgDistance = distanceStatistics::getShortestCfgDistance,
        getCfgDistanceToExitPoint = distanceStatistics::getShortestCfgDistanceToExitPoint
    )

    coverageStatistics.addOnCoveredObserver { _, method, statement -> distanceCalculator.removeTarget(method, statement) }

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { distanceCalculator.calculateDistance(it.currentStatement, it.callStack) }
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(distanceCalculator.calculateDistance(it.currentStatement, it.callStack).toDouble(), 1.0) }
    )
}

private fun <Method, Statement, State : UState<*, Method, Statement, *, *, State>> createForkDepthPathSelector(
    random: Random? = null,
): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { it.pathLocation.depth }
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(it.pathLocation.depth.toDouble(), 1.0) }
    )
}

internal fun <Method, Statement, Target : UTarget<Method, Statement, Target, State>, State : UState<*, *, Method, Statement, *, Target, State>> createClosestToTargetsPathSelector(
    distanceStatistics: DistanceStatistics<Method, Statement>,
    random: Random? = null,
): UPathSelector<State> {
    val distanceCalculator = DynamicTargetsShortestDistanceCalculator<Method, Statement, UInt> { m, s ->
        RoughIterprocShortestDistanceCalculator(
            targets = listOf(m to s),
            getCfgDistance = distanceStatistics::getShortestCfgDistance,
            getCfgDistanceToExitPoint = distanceStatistics::getShortestCfgDistanceToExitPoint
        )
    }

    fun calculateDistanceToTargets(state: State) = distanceCalculator.calculateDistance(
        state.currentStatement,
        state.callStack,
        state.targets.map { it.location }.toList(),
        folder = { if (it.isNotEmpty()) it.min() else UInt.MAX_VALUE }
    )

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = ::calculateDistanceToTargets
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(calculateDistanceToTargets(it).toDouble(), 1.0) }
    )
}
