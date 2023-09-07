package org.usvm.machine

import mu.KLogging
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.methods
import org.usvm.*
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.ps.modifiedCreatePathSelector
import org.usvm.statistics.*
import org.usvm.stopstrategies.createStopStrategy

val logger = object : KLogging() {}.logger

class ModifiedJcMachine(
    cp: JcClasspath,
    private val options: ModifiedUMachineOptions
) : ModifiedUMachine<JcState>() {
        private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem, options.solverType)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph)

    private val distanceStatistics = DistanceStatistics(applicationGraph)

    fun analyze(
        method: JcMethod,
        coverageCounter: CoverageCounter? = null
    ): List<JcState> {
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

        val pathSelector = modifiedCreatePathSelector(
            initialState,
            options,
            { coverageStatistics },
            { distanceStatistics },
            { applicationGraph },
            { coverageCounter }
        )

        val statesCollector = CoveredNewStatesCollector<JcState>(coverageStatistics) {
            it.methodResult is JcMethodResult.JcException
        }
        val stopStrategy = createStopStrategy(
            UMachineOptions(
                pathSelectorCombinationStrategy = options.pathSelectorCombinationStrategy,
                randomSeed = options.randomSeed,
                stopOnCoverage = options.stopOnCoverage,
                stepLimit = options.stepLimit,
                collectedStatesLimit = options.collectedStatesLimit,
                timeoutMs = options.timeoutMs,
                stepsFromLastCovered = options.stepsFromLastCovered,
                coverageZone = options.coverageZone,
                exceptionsPropagation = options.exceptionsPropagation,
                solverType = options.solverType
            ),
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

    private fun isStateTerminated(state: JcState): Boolean {
        return state.callStack.isEmpty()
    }

    override fun close() {
        components.close()
    }
}
