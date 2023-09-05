package org.usvm.ps

import org.usvm.*
import org.usvm.OtherPathSelectionStrategy
import org.usvm.OtherUMachineOptions
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.DistanceStatistics

fun <Method, Statement, State : UState<*, Method, Statement, *, State>> otherCreatePathSelector(
    initialState: State,
    options: OtherUMachineOptions,
    coverageStatistics: () -> CoverageStatistics<Method, Statement, State>? = { null },
    distanceStatistics: () -> DistanceStatistics<Method, Statement>? = { null },
    applicationGraph: () -> ApplicationGraph<Method, Statement>? = { null }
) : UPathSelector<State> {
    val strategies = options.pathSelectionStrategies
    val method = applicationGraph()?.methodOf(initialState.currentStatement)
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val selectors = strategies.map { strategy ->
        when (strategy) {
            OtherPathSelectionStrategy.BFS_WITH_LOGGING -> BfsWithLoggingPathSelector(
                requireNotNull(initialState.pathLocation.parent) { "Paths tree root is required for BFS with logging path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for BFS with logging path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for BFS with logging path selector" },
                requireNotNull(applicationGraph()) { "Application graph is required for BFS with logging path selector" }
            )

            OtherPathSelectionStrategy.INFERENCE_WITH_LOGGING -> InferencePathSelector(
                requireNotNull(initialState.pathLocation.parent) { "Paths tree root is required for Inference with logging path selector" },
                requireNotNull(coverageStatistics()) { "Coverage statistics is required for Inference with logging path selector" },
                requireNotNull(distanceStatistics()) { "Distance statistics is required for Inference with logging path selector" },
                requireNotNull(applicationGraph()) { "Application graph is required for Inference with logging path selector" }
            )
        }
    }

    val propagateExceptions = options.exceptionsPropagation

    selectors.singleOrNull()?.let { selector ->
        val resultSelector = selector.wrapIfRequired(propagateExceptions)
        resultSelector.add(listOf(initialState))
        return resultSelector.wrapCoverageCounter(requireNotNull(coverageStatistics()), requireNotNull(method))
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

    return selector.wrapCoverageCounter(requireNotNull(coverageStatistics()), requireNotNull(method))
}

private fun <Method, State : UState<*, Method, *, *, State>> UPathSelector<State>.wrapCoverageCounter(
    coverageStatistics: CoverageStatistics<Method, *, State>,
    method: Method
) = CoverageCounterPathSelector(this, coverageStatistics, method)

/**
 * Wraps the selector into an [ExceptionPropagationPathSelector] if [propagateExceptions] is true.
 */
private fun <State : UState<*, *, *, *, State>> UPathSelector<State>.wrapIfRequired(propagateExceptions: Boolean) =
    if (propagateExceptions && this !is ExceptionPropagationPathSelector<State>) {
        ExceptionPropagationPathSelector(this)
    } else {
        this
    }
