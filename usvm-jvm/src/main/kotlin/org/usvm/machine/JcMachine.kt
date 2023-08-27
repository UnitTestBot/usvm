package org.usvm.machine

import mu.KLogging
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.methods
import org.usvm.*
import org.usvm.api.targets.JcTarget
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.ps.createPathSelector
import org.usvm.ps.createTargetReproductionPathSelector
import org.usvm.statistics.*
import org.usvm.stopstrategies.createStopStrategy

val logger = object : KLogging() {}.logger

class JcMachine(
    cp: JcClasspath,
    private val options: UMachineOptions
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem, options.solverType)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    private val distanceStatistics = DistanceStatistics(applicationGraph)

    fun analyze(method: JcMethod): List<JcState> {
        logger.debug("$this.analyze($method)")
        val initialState = interpreter.getInitialState(method)

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD -> setOf(method)
                CoverageZone.TRANSITIVE -> setOf(method)
                // TODO: more adequate method filtering. !it.isConstructor is used to exclude default constructor which is often not covered
                CoverageZone.CLASS -> method.enclosingClass.methods.filter {
                    it.enclosingClass == method.enclosingClass && !it.isConstructor
                }.toSet()
            }

        val coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState> = CoverageStatistics(
            methodsToTrackCoverage,
            applicationGraph
        )

        val pathSelector = createPathSelector(
            initialState,
            options,
            { coverageStatistics },
            { distanceStatistics }
        )

        val statesCollector = CoveredNewStatesCollector<JcState>(coverageStatistics) {
            it.methodResult is JcMethodResult.JcException
        }
        val stopStrategy = createStopStrategy(
            options,
            coverageStatistics = { coverageStatistics },
            getCollectedStatesCount = { statesCollector.collectedStates.size }
        )

        val observers = mutableListOf<UMachineObserver<JcState>>(coverageStatistics)

        observers.add(TerminatedStateRemover())

        if (options.coverageZone == CoverageZone.TRANSITIVE) {
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

    fun reproduceTargets(
        method: JcMethod,
        targets: List<JcTarget>,
        options: TargetReproductionOptions = TargetReproductionOptions()
    ): List<JcState> {
        logger.debug("$this.reproduceTargets($method)")
        val initialState = interpreter.getInitialState(method, targets)
        val pathSelector = createTargetReproductionPathSelector(initialState, options, distanceStatistics)

        // TODO: gracefully remove
        targets.forEach { ctx.jcInterpreterObservers += it }

        val statesCollector = TargetsReachedStatesCollector<JcState>()
        val observers = mutableListOf(
            statesCollector,
            TerminatedStateRemover()
        )
        observers.addAll(targets)

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = ::isStateTerminated,
            // TODO: configure
            stopStrategy = { false },
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
