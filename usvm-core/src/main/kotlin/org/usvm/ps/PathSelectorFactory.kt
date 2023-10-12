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
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.targets.UTarget
import kotlin.math.max
import kotlin.random.Random

interface TargetWeight : Comparable<TargetWeight> {
    fun toDouble(): Double
    fun compareRhs(other: TargetWeight): Int
}

class TargetUIntWeight(val value: UInt) : TargetWeight {
    override fun compareTo(other: TargetWeight): Int =
        if (other is TargetUIntWeight) {
            value.compareTo(other.value)
        } else {
            -(other.compareRhs(this))
        }

    override fun toDouble(): Double = value.toDouble()

    // UInt weight always greater than other
    override fun compareRhs(other: TargetWeight): Int = 1
}

fun <Method, Statement, Target, State> createPathSelector(
    initialState: State,
    options: UMachineOptions,
    applicationGraph: ApplicationGraph<Method, Statement>,
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    cfgStatistics: () -> CfgStatistics<Method, Statement>? = { null },
    targetWeighter: (PathSelectionStrategy) -> StateWeighter<State, TargetWeight>? = { null },
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
                requireNotNull(targetWeighter(strategy))
            )

            PathSelectionStrategy.TARGETED_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(targetWeighter(strategy)),
                random
            )

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(targetWeighter(strategy))
            )

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(targetWeighter(strategy)),
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
    targetWeighter: StateWeighter<State, TargetWeight>,
    random: Random? = null,
): UPathSelector<State>
        where Target : UTarget<Statement, Target>,
              State : UState<*, Method, Statement, *, Target, State> {

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = targetWeighter
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(targetWeighter.weight(it).toDouble(), 1.0) }
    )
}
