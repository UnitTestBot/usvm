package org.usvm.jvm.machine

import mu.KLogging
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.methods
import org.usvm.*
import org.usvm.api.targets.JcTarget
import org.usvm.jvm.JcApplicationBlockGraph
import org.usvm.jvm.interpreter.JcBlockInterpreter
import org.usvm.machine.JcComponents
import org.usvm.machine.JcContext
import org.usvm.machine.JcTypeSystem
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.statistics.*
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.stopstrategies.createStopStrategy

val logger = object : KLogging() {}.logger

class MLJcMachine(
    cp: JcClasspath,
    private val options: MLMachineOptions
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationBlockGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem, options.basicOptions.solverType)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcBlockInterpreter(ctx, applicationGraph)
    fun analyze(method: JcMethod, targets: List<JcTarget> = emptyList()): List<JcState> {
        logger.debug("{}.analyze({}, {})", this, method, targets)
        val initialState = interpreter.getInitialState(method, targets)
        applicationGraph.initialStatement = initialState.currentStatement

        val methodsToTrackCoverage =
            when (options.basicOptions.coverageZone) {
                CoverageZone.METHOD -> setOf(method)
                CoverageZone.TRANSITIVE -> setOf(method)
                // TODO: more adequate method filtering. !it.isConstructor is used to exclude default constructor which is often not covered
                CoverageZone.CLASS -> method.enclosingClass.methods.filter {
                    it.enclosingClass == method.enclosingClass && !it.isConstructor
                }.toSet()
            }

        val coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState> = CoverageStatistics(
            methodsToTrackCoverage,
            applicationGraph.jcApplicationGraph
        )

        val stateVisitsStatistics: StateVisitsStatistics<JcMethod, JcInst, JcState> = StateVisitsStatistics()

        val pathSelector =
            createPathSelector(initialState, options, applicationGraph, stateVisitsStatistics, coverageStatistics)

        val statesCollector =
            when (options.basicOptions.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<JcState>(coverageStatistics) {
                    it.methodResult is JcMethodResult.JcException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
            }

        val stopStrategy = createStopStrategy(
            options.basicOptions,
            targets,
            coverageStatistics = { coverageStatistics },
            getCollectedStatesCount = { statesCollector.collectedStates.size }
        )

        val observers = mutableListOf<UMachineObserver<JcState>>(coverageStatistics)
        observers.add(TerminatedStateRemover())

        if (options.basicOptions.coverageZone == CoverageZone.TRANSITIVE) {
            observers.add(
                TransitiveCoverageZoneObserver(
                    initialMethod = method,
                    methodExtractor = { state -> state.lastStmt.location.method },
                    addCoverageZone = { coverageStatistics.addCoverageZone(it) },
                    ignoreMethod = { false } // TODO replace with a configurable setting
                )
            )
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
        return state.callStack.isEmpty()
    }

    override fun close() {
        components.close()
    }
}
