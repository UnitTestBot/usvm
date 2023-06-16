package org.usvm.machine

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcTypedMethod
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.combinators.ParallelSelector
import org.usvm.ps.stopstregies.TargetsCoveredStoppingStrategy

class JcMachine(
    cp: JcClasspath,
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem()
    private val components = JcComponents(typeSystem)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    fun analyze(method: JcTypedMethod): List<JcState> {
        val collectedStates = mutableListOf<JcState>()
        val coveredStoppingStrategy = TargetsCoveredStoppingStrategy(listOf(method), applicationGraph)

        val pathSelector = getPathSelector(method)

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
            stoppingStrategy = coveredStoppingStrategy,
        )
        return collectedStates
    }

    private fun getPathSelector(target: JcTypedMethod): UPathSelector<JcState> {
        val state = getInitialState(target)
        val dfsPathSelector = DfsPathSelector<JcState>()
        val bfsPathSelector = BfsPathSelector<JcState>()
        val ps = ParallelSelector(dfsPathSelector, bfsPathSelector)
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
