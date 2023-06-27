package org.usvm.machine

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.UMachine
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.ps.createPathSelector
import org.usvm.statistics.*
import org.usvm.stopstrategies.createStopStrategy

class JcMachine(
    cp: JcClasspath,
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem()
    private val components = JcComponents(typeSystem)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    private val distanceStatistics = DistanceStatistics(applicationGraph)

    fun analyze(method: JcMethod, options: JcMachineOptions = JcMachineOptions()): List<JcState> {
        val initialState = interpreter.getInitialState(method)

        val coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState> = CoverageStatistics(setOf(method), applicationGraph)
        val pathsTreeStatistics = PathsTreeStatistics(initialState)

        val pathSelector = createPathSelector(
            options.pathSelectionStrategy,
            { pathsTreeStatistics },
            { coverageStatistics },
            { distanceStatistics },
            options.randomSeed
        )
        pathSelector.add(listOf(initialState))

        // TODO: implement state limit stop strategy
        val stopStrategy = createStopStrategy(options.expectedCoverage, options.stepLimit) { coverageStatistics }

        val statesCollector = CoveredNewStatesCollector<JcState>(coverageStatistics) { it.methodResult is JcMethodResult.Exception }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(
                coverageStatistics,
                pathsTreeStatistics,
                statesCollector
            ),
            continueAnalyzing = { !isFinishedState(it) },
            stopStrategy = stopStrategy,
        )

        return statesCollector.collectedStates
    }

    private fun isFinishedState(state: JcState): Boolean {
        return state.callStack.isEmpty() || state.methodResult is JcMethodResult.Exception
    }

    override fun close() {
        components.close()
    }
}
