package org.usvm

import org.usvm.ps.ExceptionPropagationPathSelector
import org.usvm.ps.GNNPathSelector
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StateVisitsStatistics

fun <Method, Statement, BasicBlock, State : UState<*, Method, Statement, *, *, State>> createPathSelector(
    initialState: State,
    options: MLMachineOptions,
    applicationGraph: ApplicationBlockGraph<Method, BasicBlock, Statement>,
    stateVisitsStatistics: StateVisitsStatistics<Method, Statement, State>,
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
): UPathSelector<State> {
    val selector = when (options.pathSelectionStrategy) {
        MLPathSelectionStrategy.GNN -> createGNNPathSelector(
            stateVisitsStatistics,
            coverageStatistics, applicationGraph, options.heteroGNNModelPath
        )

        else -> {
            throw NotImplementedError()
        }
    }

    val propagateExceptions = options.basicOptions.exceptionsPropagation

    val resultSelector = selector.wrapIfRequired(propagateExceptions)
    resultSelector.add(listOf(initialState))

    return selector
}

private fun <State : UState<*, *, *, *, *, State>> UPathSelector<State>.wrapIfRequired(propagateExceptions: Boolean) =
    if (propagateExceptions && this !is ExceptionPropagationPathSelector<State>) {
        ExceptionPropagationPathSelector(this)
    } else {
        this
    }

private fun <Method, Statement, BasicBlock, State : UState<*, Method, Statement, *, *, State>> createGNNPathSelector(
    stateVisitsStatistics: StateVisitsStatistics<Method, Statement, State>,
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    applicationGraph: ApplicationBlockGraph<Method, BasicBlock, Statement>,
    heteroGNNModelPath: String,
): UPathSelector<State> {
    return GNNPathSelector(
        coverageStatistics,
        stateVisitsStatistics,
        applicationGraph,
        heteroGNNModelPath
    )
}
