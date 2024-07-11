package org.usvm

import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.model.EtsFile
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.usvm.ps.createPathSelector
import org.usvm.state.TSMethodResult
import org.usvm.state.TSState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy
import kotlin.time.Duration.Companion.seconds

class TSMachine(
    private val project: EtsFile,
    private val options: UMachineOptions
) : UMachine<TSState>() {

    private val typeSystem = TSTypeSystem(typeOperationsTimeout = 1.seconds, project)
    private val components = TSComponents(typeSystem, options)
    private val ctx: TSContext = TSContext(components)
    private val applicationGraph: TSApplicationGraph = TSApplicationGraph(project)
    private val interpreter: TSInterpreter = TSInterpreter(ctx, applicationGraph)
    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    fun analyze(
        methods: List<EtsMethod>,
        targets: List<TSTarget> = emptyList(),
    ): List<TSState> {
        val initialStates = mutableMapOf<EtsMethod, TSState>()
        methods.forEach { initialStates[it] = interpreter.getInitialState(it, targets) }

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD,
                CoverageZone.TRANSITIVE,
                -> methods.toSet()
                // TODO: more adequate method filtering. !it.isConstructor is used to exclude default constructor which is often not covered
                CoverageZone.CLASS -> methods.mapNotNull { method ->
                    project.getMethodBySignature(method.signature)
                }.toSet() + methods
            }

        val kek = methods.mapNotNull { method ->
            project.getMethodBySignature(method.signature)
        }.toSet()

        val coverageStatistics: CoverageStatistics<EtsMethod, EtsStmt, TSState> = CoverageStatistics(
            methodsToTrackCoverage,
            applicationGraph
        )

        val callGraphStatistics: PlainCallGraphStatistics<EtsMethod> =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> TODO("Unsupported yet")
            }

        val loopTracker = TSLoopTracker()
        val timeStatistics = TimeStatistics<EtsMethod, TSState>()
        val transparentCfgStatistics = transparentCfgStatistics()

        val pathSelector = createPathSelector(
            initialStates,
            options,
            applicationGraph,
            timeStatistics,
            { coverageStatistics },
            { transparentCfgStatistics },
            { callGraphStatistics },
            { loopTracker }
        )

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<TSState>(coverageStatistics) {
                    it.methodResult is TSMethodResult.TSException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
                StateCollectionStrategy.ALL -> AllStatesCollector()
            }

        val observers = mutableListOf<UMachineObserver<TSState>>(coverageStatistics)
        observers.add(statesCollector)

        val stepsStatistics = StepsStatistics<EtsMethod, TSState>()

        val stopStrategy = createStopStrategy(
            options,
            targets,
            timeStatisticsFactory = { timeStatistics },
            stepsStatisticsFactory = { stepsStatistics },
            coverageStatisticsFactory = { coverageStatistics },
            getCollectedStatesCount = { statesCollector.collectedStates.size }
        )

        observers.add(timeStatistics)
        observers.add(stepsStatistics)

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers),
            isStateTerminated = { state -> state.callStack.isEmpty() },
            stopStrategy = stopStrategy
        )

        return statesCollector.collectedStates
    }

    override fun close() {
        components.close()
    }

    private fun transparentCfgStatistics() = object : CfgStatistics<EtsMethod, EtsStmt> {
        override fun getShortestDistance(method: EtsMethod, stmtFrom: EtsStmt, stmtTo: EtsStmt): UInt {
            return cfgStatistics.getShortestDistance(method, stmtFrom, stmtTo)
        }

        override fun getShortestDistanceToExit(method: EtsMethod, stmtFrom: EtsStmt): UInt {
            return cfgStatistics.getShortestDistanceToExit(method, stmtFrom)
        }
    }
}
