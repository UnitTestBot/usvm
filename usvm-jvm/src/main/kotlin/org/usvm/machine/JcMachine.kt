package org.usvm.machine

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.methods
import org.usvm.CoverageZone
import org.usvm.UMachineOptions
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.UMachine
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.ps.createPathSelector
import org.usvm.statistics.*
import org.usvm.stopstrategies.createStopStrategy

class JcMachine(
    cp: JcClasspath,
    private val options: UMachineOptions
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem()
    private val components = JcComponents(typeSystem, options.solverType)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    private val distanceStatistics = DistanceStatistics(applicationGraph)

    fun analyze(
        method: JcMethod
    ): List<JcState> {
        val initialState = interpreter.getInitialState(method)

        // TODO: now paths tree doesn't support parallel execution processes. It should be replaced with forest
        val disablePathsTreeStatistics = options.pathSelectorCombinationStrategy == PathSelectorCombinationStrategy.PARALLEL

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD -> setOf(method)
                // TODO: more adequate method filtering. !it.isConstructor is used to exclude default constructor which is often not covered
                CoverageZone.CLASS -> method.enclosingClass.methods.filter {
                    it.enclosingClass == method.enclosingClass && !it.isConstructor
                }.toSet()
            }

        val coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState> = CoverageStatistics(methodsToTrackCoverage, applicationGraph)
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
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )

        return statesCollector.collectedStates
    }

    private fun isStateTerminated(state: JcState): Boolean {
        return state.callStack.isEmpty() || state.methodResult is JcMethodResult.Exception
    }

    override fun close() {
        components.close()
    }
}
