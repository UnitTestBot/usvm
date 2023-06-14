package org.usvm

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcTypedMethod
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.combinators.CustomStoppingStrategySelector
import org.usvm.ps.combinators.InterleavedSelector
import org.usvm.ps.stopstregies.TargetsCoveredStoppingStrategy
import org.usvm.state.JcMethodResult
import org.usvm.state.JcState

class JcMachine(
    cp: JcClasspath,
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    fun analyze(method: JcTypedMethod): List<JcState> {
        val collectedStates = mutableListOf<JcState>()
        val coveredStoppingStrategy = TargetsCoveredStoppingStrategy(listOf(method), applicationGraph)

        val pathSelector = CustomStoppingStrategySelector(getPathSelector(method), coveredStoppingStrategy)

        run(
            interpreter,
            pathSelector,
            onState = { state ->
                if (!isInterestingState(state)) {
                    val uncoveredStatementsBefore = coveredStoppingStrategy.uncoveredStatementsCount
                    coveredStoppingStrategy.onStateTermination(state)
                    val uncoveredStatementsCountAfter = coveredStoppingStrategy.uncoveredStatementsCount
                    if (uncoveredStatementsCountAfter < uncoveredStatementsBefore || state.methodResult is JcMethodResult.Exception) {
                        collectedStates += state
                    }
                } else {
                    coveredStoppingStrategy.onStateVisit(state)
                }
            },
            continueAnalyzing = ::isInterestingState,
        )
        return collectedStates
    }

    private fun getPathSelector(target: JcTypedMethod): UPathSelector<JcState> {
        val state = getInitialState(target)
        val dfsPathSelector = DfsPathSelector<JcState>()
        val bfsPathSelector = BfsPathSelector<JcState>()
        val ps = InterleavedSelector(dfsPathSelector, bfsPathSelector)
        bfsPathSelector.add(listOf(state))
        dfsPathSelector.add(listOf(state.clone()))
        return ps
    }

    private fun getInitialState(method: JcTypedMethod): JcState =
        interpreter.getInitialState(method)

    private fun isInterestingState(state: JcState): Boolean {
        return state.callStack.isNotEmpty() && state.methodResult !is JcMethodResult.Exception
    }

    override fun close() {
        components.close()
    }
}