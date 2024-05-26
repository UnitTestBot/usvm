package org.usvm.machine

import mu.KLogging
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.methods
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcTarget
import org.usvm.forkblacklists.TargetsReachableForkBlackList
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.ps.StateWeighter
import org.usvm.ps.TargetUIntWeight
import org.usvm.ps.TargetWeight
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.TerminatedStateRemover
import org.usvm.statistics.TransitiveCoverageZoneObserver
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.InterprocDistance
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.statistics.distances.logWeight
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.originalInst

val logger = object : KLogging() {}.logger

class JcMachine(
    cp: JcClasspath,
    private val options: UMachineOptions,
    private val interpreterObserver: JcInterpreterObserver? = null,
    private val jcOptions: JcMachineOptions = JcMachineOptions(),
) : UMachine<JcState>() {
    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem, options.solverType, options.useSolverForForks)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph, jcOptions, interpreterObserver)

    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    private val transparentCfgStatistics = transparentCfgStatistics()

    fun analyze(method: JcMethod, targets: List<JcTarget> = emptyList()): List<JcState> {
        logger.debug("{}.analyze({}, {})", this, method, targets)
        val initialState = interpreter.getInitialState(method, targets)

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

        val callGraphStatistics =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> JcCallGraphStatistics(
                    options.targetSearchDepth,
                    applicationGraph,
                    typeSystem.topTypeStream(),
                    subclassesToTake = 10
                )
            }

        val pathSelector = if (interpreterObserver is JcPathSelectorProvider) {
            interpreterObserver.createPathSelector(
                initialState,
                applicationGraph,
                transparentCfgStatistics,
                callGraphStatistics
            )
        } else {
            createPathSelector(
                initialState,
                options,
                applicationGraph,
                { coverageStatistics },
                { transparentCfgStatistics },
                { targetWeighter(it, callGraphStatistics) }
            )
        }

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<JcState>(coverageStatistics) {
                    it.methodResult is JcMethodResult.JcException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
            }

        val stopStrategy = createStopStrategy(
            options,
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
        // TODO: use the same calculator which is used for path selector

        interpreter.forkBlackList = UForkBlackList.createDefault()
        if (interpreterObserver is JcTargetBlackLister) {
            interpreter.forkBlackList = interpreterObserver.createBlacklist(
                interpreter.forkBlackList, applicationGraph, transparentCfgStatistics, callGraphStatistics
            )
        }

//        if (targets.isNotEmpty()) {
//            val distanceCalculator = MultiTargetDistanceCalculator<JcMethod, JcInst, InterprocDistance> { stmt ->
//                InterprocDistanceCalculator(
//                    targetLocation = stmt,
//                    applicationGraph = applicationGraph,
//                    cfgStatistics = transparentCfgStatistics(),
//                    callGraphStatistics = callGraphStatistics
//                )
//            }
//            interpreter.forkBlackList =
//                TargetsReachableForkBlackList(distanceCalculator, shouldBlackList = { isInfinite })
//        } else {
//            interpreter.forkBlackList = UForkBlackList.createDefault()
//        }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )

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

    private fun targetWeighter(
        strategy: PathSelectionStrategy,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): StateWeighter<JcState, TargetWeight> {
        when (strategy) {
            PathSelectionStrategy.TARGETED, PathSelectionStrategy.TARGETED_RANDOM -> {
                val distanceCalculator = MultiTargetDistanceCalculator<JcMethod, JcInst, _> { stmt ->
                    InterprocDistanceCalculator(
                        targetLocation = stmt,
                        applicationGraph = applicationGraph,
                        cfgStatistics = transparentCfgStatistics,
                        callGraphStatistics = callGraphStatistics
                    )
                }

                return wrapWeighter(strategy, callGraphStatistics) { state, target ->
                    val location = target.location
                    if (location == null) {
                        0u
                    } else {
                        distanceCalculator.calculateDistance(
                            state.currentStatement,
                            state.callStack,
                            location
                        ).logWeight()
                    }
                }
            }

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL, PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL_RANDOM -> {
                val distanceCalculator = MultiTargetDistanceCalculator<JcMethod, JcInst, _> { loc ->
                    CallStackDistanceCalculator(
                        targets = listOf(loc),
                        cfgStatistics = transparentCfgStatistics,
                        applicationGraph = applicationGraph
                    )
                }

                return wrapWeighter(strategy, callGraphStatistics) { state, target ->
                    val location = target.location
                    if (location == null) {
                        0u
                    } else {
                        distanceCalculator.calculateDistance(
                            state.currentStatement,
                            state.callStack,
                            location
                        )
                    }
                }
            }

            else -> error("Unexpected strategy: $strategy")
        }
    }

    private inline fun wrapWeighter(
        strategy: PathSelectionStrategy,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        default: (JcState, JcTarget) -> UInt
    ): StateWeighter<JcState, TargetWeight> {
        val targetWeighter = (interpreterObserver as? JcTargetWeighter<*>)
            ?.createWeighter(strategy, applicationGraph, transparentCfgStatistics, callGraphStatistics)
        return StateWeighter { state ->
            state.targets.minOfOrNull { target ->
                targetWeighter?.invoke(target, state) ?: TargetUIntWeight(default(state, target))
            } ?: TargetUIntWeight(UInt.MAX_VALUE)
        }
    }
}
