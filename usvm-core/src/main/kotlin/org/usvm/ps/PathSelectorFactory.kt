package org.usvm.ps

import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.RandomizedPriorityCollection
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.InterprocDistance
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.statistics.distances.ReachabilityKind
import org.usvm.targets.UTarget
import org.usvm.util.log2
import kotlin.math.max
import kotlin.random.Random

fun <Method, Statement, Target, State> createPathSelector(
    initialState: State,
    options: UMachineOptions,
    applicationGraph: ApplicationGraph<Method, Statement>,
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    cfgStatistics: () -> CfgStatistics<Method, Statement>? = { null },
    callGraphStatistics: () -> CallGraphStatistics<Method>? = { null },
): UPathSelector<State>
        where Target : UTarget<Statement, Target>,
              State : UState<*, Method, Statement, *, Target, State> {

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
                requireNotNull(cfgStatistics()) { "CFG statistics is required for closest to uncovered path selector" },
                applicationGraph
            )

            PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM -> createClosestToUncoveredPathSelector(
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for closest to uncovered path selector" },
                requireNotNull(cfgStatistics()) { "CFG statistics is required for closest to uncovered path selector" },
                applicationGraph,
                random
            )

            PathSelectionStrategy.TARGETED -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatistics()) { "CFG statistics is required for targeted path selector" },
                requireNotNull(callGraphStatistics()) { "Call graph statistics is required for targeted path selector" },
                applicationGraph
            )

            PathSelectionStrategy.TARGETED_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatistics()) { "CFG statistics is required for targeted path selector" },
                requireNotNull(callGraphStatistics()) { "Call graph statistics is required for targeted path selector" },
                applicationGraph,
                random
            )

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatistics()) { "CFG statistics is required for targeted call stack local path selector" },
                applicationGraph
            )

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatistics()) { "CFG statistics is required for targeted call stack local path selector" },
                applicationGraph,
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
    cfgStatistics: CfgStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    random: Random? = null,
): UPathSelector<State> {
    val distanceCalculator = CallStackDistanceCalculator(
        targets = coverageStatistics.getUncoveredStatements(),
        cfgStatistics = cfgStatistics,
        applicationGraph
    )

    coverageStatistics.addOnCoveredObserver { _, method, statement ->
        distanceCalculator.removeTarget(
            method,
            statement
        )
    }

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { distanceCalculator.calculateDistance(it.currentStatement, it.callStack) }
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = {
            1.0 / max(
                distanceCalculator.calculateDistance(it.currentStatement, it.callStack).toDouble(),
                1.0
            )
        }
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

internal fun <Method, Statement, Target, State> createTargetedPathSelector(
    cfgStatistics: CfgStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    random: Random? = null,
): UPathSelector<State>
        where Target : UTarget<Statement, Target>,
              State : UState<*, Method, Statement, *, Target, State> {

    val distanceCalculator = MultiTargetDistanceCalculator<Method, Statement, _> { loc ->
        CallStackDistanceCalculator(
            targets = listOf(loc),
            cfgStatistics = cfgStatistics,
            applicationGraph = applicationGraph
        )
    }

    fun calculateDistanceToTargets(state: State) =
        state.targets.minOfOrNull { target ->
            val location = target.location
            if (location == null) {
                0u
            } else {
                distanceCalculator.calculateDistance(
                    state.currentStatement,
                    state.callStack,
                    location
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

/**
 * Converts [InterprocDistance] to integer weight with the following properties:
 * - All distances with [ReachabilityKind.LOCAL] have smaller weight than the others.
 * - For greater distances one-step distance is less significant (logarithmic scale).
 * - All distances lie in [[0; 64]] interval.
 * - Only infinite distances map to weight equal to 64.
 */
private fun InterprocDistance.logWeight(): UInt {
    if (isInfinite) {
        return 64u
    }
    var weight = log2(distance) // weight is in [0; 32)
    assert(weight < 32u)
    if (reachabilityKind != ReachabilityKind.LOCAL) {
        weight += 32u // non-local's weight is in [32, 64)
    }
    return weight
}

internal fun <Method, Statement, Target, State> createTargetedPathSelector(
    cfgStatistics: CfgStatistics<Method, Statement>,
    callGraphStatistics: CallGraphStatistics<Method>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    random: Random? = null,
): UPathSelector<State>
        where Target : UTarget<Statement, Target>,
              State : UState<*, Method, Statement, *, Target, State> {

    val distanceCalculator = MultiTargetDistanceCalculator<Method, Statement, _> { stmt ->
        InterprocDistanceCalculator(
            targetLocation = stmt,
            applicationGraph = applicationGraph,
            cfgStatistics = cfgStatistics,
            callGraphStatistics = callGraphStatistics
        )
    }

    fun calculateWeight(state: State) =
        state.targets.minOfOrNull { target ->
            val location = target.location
            if (location == null) {
                0u
            } else {
                distanceCalculator.calculateDistance(
                    state.currentStatement,
                    state.callStack,
                    location
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
