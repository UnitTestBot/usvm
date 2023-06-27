package org.usvm.machine

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.MachineOptions
import org.usvm.PathSelectorCombinationStrategy
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

    fun analyze(
        method: JcMethod,
        options: MachineOptions = MachineOptions()
    ): List<JcState> {
        val initialState = interpreter.getInitialState(method)

        // TODO: now paths tree doesn't support parallel execution processes. It should be replaced with forest
        val disablePathsTreeStatistics = options.pathSelectorCombinationStrategy == PathSelectorCombinationStrategy.PARALLEL

        val coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState> = CoverageStatistics(setOf(method), applicationGraph)
        val pathsTreeStatistics = PathsTreeStatistics(initialState)

        val pathSelector = createPathSelector(
            initialState,
            options,
            { if (disablePathsTreeStatistics) null else pathsTreeStatistics },
            { coverageStatistics },
            { distanceStatistics }
        )

        val statesCollector = CoveredNewStatesCollector<JcState>(coverageStatistics) { it.methodResult is JcMethodResult.Exception }
        val stopStrategy = createStopStrategy(options, { coverageStatistics }, { statesCollector.collectedStates.size })

        val observers = mutableListOf<UMachineObserver<JcState>>(coverageStatistics)
        if (!disablePathsTreeStatistics) {
            observers.add(pathsTreeStatistics)
        }
        observers.add(statesCollector)

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
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
