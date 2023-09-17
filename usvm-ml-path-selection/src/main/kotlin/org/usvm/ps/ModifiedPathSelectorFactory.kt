package org.usvm.ps

import org.usvm.*
import org.usvm.ModifiedPathSelectionStrategy
import org.usvm.ModifiedUMachineOptions
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.RandomizedPriorityCollection
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.distances.*
import kotlin.math.max
import kotlin.random.Random

fun <Method, Statement, Target : UTarget<Statement, Target, State>, State : UState<*, Method, Statement, *, Target, State>> modifiedCreatePathSelector(
    initialState: State,
    options: ModifiedUMachineOptions,
    applicationGraph: ApplicationGraph<Method, Statement>,
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    cfgStatistics: () -> CfgStatistics<Method, Statement>? = { null },
    callGraphStatistics: () -> CallGraphStatistics<Method>? = { null },
    mlConfig: () -> MLConfig? = { null }
) : UPathSelector<State> {
    val strategies = options.pathSelectionStrategies
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val random by lazy { Random(options.basicOptions.randomSeed) }

    val selectors = strategies.map { strategy ->
        when (strategy) {
            ModifiedPathSelectionStrategy.FEATURES_LOGGING -> FeaturesLoggingPathSelector(
                requireNotNull(initialState.pathLocation.parent) { "Paths tree root is required for Features Logging path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for Features Logging path selector" },
                requireNotNull(cfgStatistics()) { "CFG statistics is required for Features Logging path selector" },
                applicationGraph,
                requireNotNull(mlConfig()) { "ML config is required for Features Logging path selector" },
                when(requireNotNull(mlConfig()).defaultAlgorithm) {
                    Algorithm.BFS -> BfsPathSelector()
                    Algorithm.ForkDepthRandom -> createForkDepthPathSelector(random)
                },
            )

            ModifiedPathSelectionStrategy.MACHINE_LEARNING -> MachineLearningPathSelector(
                requireNotNull(initialState.pathLocation.parent) { "Paths tree root is required for Machine Learning path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for Machine Learning path selector" },
                requireNotNull(cfgStatistics()) { "Distance statistics is required for Machine Learning path selector" },
                applicationGraph,
                requireNotNull(mlConfig()) { "ML config is required for Machine Learning path selector" },
                when(requireNotNull(mlConfig()).defaultAlgorithm) {
                    Algorithm.BFS -> BfsPathSelector()
                    Algorithm.ForkDepthRandom -> createForkDepthPathSelector(random)
                },
            )
        }
    }

    val propagateExceptions = options.basicOptions.exceptionsPropagation

    selectors.singleOrNull()?.let { selector ->
        val resultSelector = selector.wrapIfRequired(propagateExceptions)
        resultSelector.add(listOf(initialState))
        return resultSelector
    }

    require(selectors.size >= 2) { "Cannot create collaborative path selector from less than 2 selectors" }

    val selector = when (options.basicOptions.pathSelectorCombinationStrategy) {
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
