package org.usvm.machine

import mu.KLogging
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.methods
import org.usvm.*
import org.usvm.api.targets.JcTarget
import org.usvm.forkblacklists.TargetsReachableForkBlackList
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.ps.FeaturesLoggingPathSelector
import org.usvm.ps.modifiedCreatePathSelector
import org.usvm.statistics.*
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.InterprocDistance
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.getMethodFullName
import org.usvm.util.originalInst

val logger = object : KLogging() {}.logger

class ModifiedJcMachine(
    cp: JcClasspath,
    private val options: ModifiedUMachineOptions,
    private val interpreterObserver: JcInterpreterObserver? = null
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(
        typeSystem, options.basicOptions.solverType,
        options.basicOptions.useSolverForForks
    )
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph, interpreterObserver)

    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    fun analyze(
        method: JcMethod,
        targets: List<JcTarget> = emptyList(),
        coverageCounter: CoverageCounter? = null,
        mlConfig: MLConfig? = null
    ): List<JcState> {
        logger.debug("{}.analyze({}, {})", this, method, targets)
        val initialState = interpreter.getInitialState(method, targets)

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
            applicationGraph
        )

        val callGraphStatistics =
            when (options.basicOptions.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> JcCallGraphStatistics(
                    options.basicOptions.targetSearchDepth,
                    applicationGraph,
                    typeSystem.topTypeStream(),
                    subclassesToTake = 10
                )
            }

        val transparentCfgStatistics = transparentCfgStatistics()

        val pathSelector = modifiedCreatePathSelector(
            initialState,
            options,
            applicationGraph,
            { coverageStatistics },
            { transparentCfgStatistics },
            { callGraphStatistics },
            { mlConfig },
        )

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

        if (interpreterObserver is UMachineObserver<*>) {
            @Suppress("UNCHECKED_CAST")
            observers.add(interpreterObserver as UMachineObserver<JcState>)
        }

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
        // TODO: use the same calculator which is used for path selector
        if (targets.isNotEmpty()) {
            val distanceCalculator = MultiTargetDistanceCalculator<JcMethod, JcInst, InterprocDistance> { stmt ->
                InterprocDistanceCalculator(
                    targetLocation = stmt,
                    applicationGraph = applicationGraph,
                    cfgStatistics = cfgStatistics,
                    callGraphStatistics = callGraphStatistics
                )
            }
            interpreter.forkBlackList = TargetsReachableForkBlackList(distanceCalculator, shouldBlackList = { isInfinite })
        } else {
            interpreter.forkBlackList = UForkBlackList.createDefault()
        }

        val methodFullName = getMethodFullName(method)
        if (coverageCounter != null) {
            observers.add(CoverageCounterStatistics(coverageStatistics, coverageCounter, methodFullName))
        }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )

        coverageCounter?.finishTest(methodFullName)
        if (pathSelector is FeaturesLoggingPathSelector<*, *, *> && mlConfig != null) {
            if (mlConfig.logFeatures && mlConfig.mode != Mode.Test) {
                pathSelector.savePath()
            }
        }

        return statesCollector.collectedStates
    }

    /**
     * Returns a wrapper for the [cfgStatistics] that ignores [JcTransparentInstruction]s.
     * Instead of calculating statistics for them, it just takes the statistics for
     * their original instructions.
     */
    private fun transparentCfgStatistics() = object : CfgStatistics<JcMethod, JcInst> {
        override fun getShortestDistance(method: JcMethod, stmtFrom: JcInst, stmtTo: JcInst): UInt {
            return cfgStatistics.getShortestDistance(method, stmtFrom.originalInst(), stmtTo.originalInst())
        }

        override fun getShortestDistanceToExit(method: JcMethod, stmtFrom: JcInst): UInt {
            return cfgStatistics.getShortestDistanceToExit(method, stmtFrom.originalInst())
        }
    }

    private fun isStateTerminated(state: JcState): Boolean {
        return state.callStack.isEmpty()
    }

    override fun close() {
        components.close()
    }
}
