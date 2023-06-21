package org.usvm.machine

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.usvm.UMachine
import org.usvm.UPathSelector
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.DfsPathSelector
import org.usvm.ps.combinators.ParallelSelector
import org.usvm.ps.stopstrategies.TargetsCoveredStoppingStrategy

class JcMachine(
    cp: JcClasspath,
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem()
    private val components = JcComponents(typeSystem)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    fun analyze(method: JcMethod): List<JcState> {
        val collectedStates = mutableListOf<JcState>()
        val coveredStoppingStrategy = TargetsCoveredStoppingStrategy(listOf(method), applicationGraph)

        val pathSelector = getPathSelector(method)

        run(
            interpreter,
            pathSelector,
            onState = { state ->
                // TODO: think on correct place for this
                if (!continueAnalyzing(state)) {
                    val uncoveredStatementsBefore = coveredStoppingStrategy.uncoveredStatementsCount
                    coveredStoppingStrategy.onStateTermination(state)
                    // TODO: maybe we need do call onStateVisit(state)
                    val uncoveredStatementsCountAfter = coveredStoppingStrategy.uncoveredStatementsCount
                    if (uncoveredStatementsCountAfter < uncoveredStatementsBefore ||
                        state.methodResult is JcMethodResult.Exception // TODO: strange hack, we should cache it
                    ) {
                        collectedStates += state
                    }
                } else {
                    coveredStoppingStrategy.onStateVisit(state)
                }
            },
            continueAnalyzing = ::continueAnalyzing,
            stoppingStrategy = coveredStoppingStrategy,
        )
        return collectedStates
    }

    private fun getPathSelector(target: JcMethod): UPathSelector<JcState> {
        val state = interpreter.getInitialState(target)
        val dfsPathSelector = DfsPathSelector<JcState>()
        val bfsPathSelector = BfsPathSelector<JcState>()
        val ps = InterleavedPathSelector(dfsPathSelector, bfsPathSelector)
        bfsPathSelector.add(listOf(state))
        dfsPathSelector.add(listOf(state.clone()))
        return ps
    }

    private fun continueAnalyzing(state: JcState): Boolean {
        return state.callStack.isNotEmpty() && state.methodResult !is JcMethodResult.Exception
    }

    override fun close() {
        components.close()
    }
}
