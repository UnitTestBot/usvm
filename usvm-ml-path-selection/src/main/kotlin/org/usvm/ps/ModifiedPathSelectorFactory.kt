package org.usvm.ps

import org.usvm.*
import org.usvm.ModifiedPathSelectionStrategy
import org.usvm.ModifiedUMachineOptions
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics

fun <Method, Statement, State : UState<*, Method, Statement, *, State>> modifiedCreatePathSelector(
    initialState: State,
    options: ModifiedUMachineOptions,
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    distanceStatistics: () -> DistanceStatistics<Method, Statement>? = { null },
    applicationGraph: () -> ApplicationGraph<Method, Statement>? = { null },
    mlConfig: () -> MLConfig? = { null }
) : UPathSelector<State> {
    val strategies = options.pathSelectionStrategies
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val selectors = strategies.map { strategy ->
        when (strategy) {
            ModifiedPathSelectionStrategy.FEATURES_LOGGING -> FeaturesLoggingPathSelector(
                requireNotNull(initialState.pathLocation.parent) { "Paths tree root is required for Features Logging path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for Features Logging path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for Features Logging path selector" },
                requireNotNull(applicationGraph()) { "Application graph is required for Features Logging path selector" },
                requireNotNull(mlConfig()) { "ML config is required for Features Logging path selector" },
                when(requireNotNull(mlConfig()).defaultAlgorithm) {
                    Algorithm.BFS -> BfsPathSelector()
                    Algorithm.ForkDepthRandom -> BfsPathSelector()
                },
            )

            ModifiedPathSelectionStrategy.MACHINE_LEARNING -> MachineLearningPathSelector(
                requireNotNull(initialState.pathLocation.parent) { "Paths tree root is required for Machine Learning path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for Machine Learning path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for Machine Learning path selector" },
                requireNotNull(applicationGraph()) { "Application graph is required for Machine Learning path selector" },
                requireNotNull(mlConfig()) { "ML config is required for Machine Learning path selector" },
                when(requireNotNull(mlConfig()).defaultAlgorithm) {
                    Algorithm.BFS -> BfsPathSelector()
                    Algorithm.ForkDepthRandom -> BfsPathSelector()
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
private fun <State : UState<*, *, *, *, State>> UPathSelector<State>.wrapIfRequired(propagateExceptions: Boolean) =
    if (propagateExceptions && this !is ExceptionPropagationPathSelector<State>) {
        ExceptionPropagationPathSelector(this)
    } else {
        this
    }
