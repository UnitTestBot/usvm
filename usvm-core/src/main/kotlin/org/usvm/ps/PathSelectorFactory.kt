package org.usvm.ps

import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.RandomizedPriorityCollection
import org.usvm.util.log2
import kotlin.math.max
import kotlin.random.Random

fun <Method, Statement, Target : UTarget<Method, Statement, Target, State>, State : UState<*, Method, Statement, *, Target, State>> createPathSelector(
    initialState: State,
    options: UMachineOptions,
    applicationGraph: ApplicationGraph<Method, Statement>,
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    distanceStatistics: () -> DistanceStatistics<Method, Statement>? = { null }
): UPathSelector<State> {
    val strategies = options.pathSelectionStrategies
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val random by lazy { Random(options.randomSeed) }

    val callGraphReachabilityStatistics =
        if (options.targetSearchDepth > 0u) {
            CallGraphReachabilityStatistics(options.targetSearchDepth, applicationGraph)
        } else null

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

            PathSelectionStrategy.TARGETED -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(distanceStatistics()) { "Distance statistics is required for targeted path selector" },
                applicationGraph,
                callGraphReachabilityStatistics
            )
            PathSelectionStrategy.TARGETED_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(distanceStatistics()) { "Distance statistics is required for targeted path selector" },
                applicationGraph,
                callGraphReachabilityStatistics,
                random
            )

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(distanceStatistics()) { "Distance statistics is required for targeted call stack local path selector" }
            )
            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(distanceStatistics()) { "Distance statistics is required for targeted call stack local path selector" },
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

/**
 * Wraps the selector into an [ExceptionPropagationPathSelector] if [propagateExceptions] is true.
 */
private fun <State : UState<*, *, *, *, *, State>> UPathSelector<State>.wrapIfRequired(propagateExceptions: Boolean) =
    if (propagateExceptions && this !is ExceptionPropagationPathSelector<State>) {
        ExceptionPropagationPathSelector(this)
    } else {
        this
    }

private fun <State : UState<*, *, *, *, *, State>> compareById(): Comparator<State> = compareBy { it.id }

private fun <State : UState<*, *, *, *, *, State>> createDepthPathSelector(random: Random? = null): UPathSelector<State> {
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
    val distanceCalculator = CallStackDistanceCalculator(
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

internal fun <Method, Statement, Target : UTarget<Method, Statement, Target, State>, State : UState<*, Method, Statement, *, Target, State>> createTargetedPathSelector(
    distanceStatistics: DistanceStatistics<Method, Statement>,
    random: Random? = null,
): UPathSelector<State> {
    val distanceCalculator = DynamicTargetsShortestDistanceCalculator<Method, Statement, UInt> { m, s ->
        CallStackDistanceCalculator(
            targets = listOf(m to s),
            getCfgDistance = distanceStatistics::getShortestCfgDistance,
            getCfgDistanceToExitPoint = distanceStatistics::getShortestCfgDistanceToExitPoint
        )
    }

    fun calculateDistanceToTargets(state: State) =
        state.targets.minOfOrNull { target ->
            if (target.location == null) {
                0u // i. e. ExitTarget case
            } else {
                distanceCalculator.calculateDistance(
                    state.currentStatement,
                    state.callStack,
                    target.location
                )
            }
        } ?: UInt.MAX_VALUE

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

internal fun InterprocDistance.logWeight(): UInt {
    var weight = log2(distance) // In KLEE, the number of stepped memory instructions is also added to distance
    assert(weight <= 32u)
    if (reachabilityKind != ReachabilityKind.LOCAL) {
        weight += 32u
    }
    return distance
}

internal fun <Method, Statement, Target : UTarget<Method, Statement, Target, State>, State : UState<*, Method, Statement, *, Target, State>> createTargetedPathSelector(
    distanceStatistics: DistanceStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    callGraphReachabilityStatistics: CallGraphReachabilityStatistics<Method, Statement>? = null,
    random: Random? = null,
): UPathSelector<State> {
    val distanceCalculator = DynamicTargetsShortestDistanceCalculator<Method, Statement, InterprocDistance> { m, s ->
        InterprocDistanceCalculator(
            targetLocation = m to s,
            applicationGraph = applicationGraph,
            getCfgDistance = distanceStatistics::getShortestCfgDistance,
            getCfgDistanceToExitPoint = distanceStatistics::getShortestCfgDistanceToExitPoint,
            checkReachabilityInCallGraph = if (callGraphReachabilityStatistics != null) (callGraphReachabilityStatistics::checkReachability) else { m1, m2 -> m1 == m2 }
        )
    }

    fun calculateWeight(state: State) =
        state.targets.minOfOrNull { target ->
            if (target.location == null) {
                0u // i. e. ExitTarget case
            } else {
                distanceCalculator.calculateDistance(
                    state.currentStatement,
                    state.callStack,
                    target.location
                ).logWeight()
            }
        } ?: UInt.MAX_VALUE

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = ::calculateWeight
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(calculateWeight(it).toDouble(), 1.0) }
    )
}
