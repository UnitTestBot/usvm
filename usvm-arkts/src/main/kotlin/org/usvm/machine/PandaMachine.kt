package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaProject
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.machine.state.PandaMethodResult
import org.usvm.machine.state.PandaState
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy
import kotlin.time.Duration.Companion.seconds

class PandaMachine(
    project: PandaProject,
    private val options: UMachineOptions,
) : UMachine<PandaState>() {
    private val typeSystem = PandaTypeSystem(typeOperationsTimeout = 1.seconds, project)
    private val components = PandaComponents(typeSystem, options)
    private val ctx: PandaContext = PandaContext(components)
    private val applicationGraph: PandaApplicationGraph = PandaApplicationGraph(project)
    private val interpreter: PandaInterpreter = PandaInterpreter(ctx, applicationGraph)

    fun analyze(
        methods: List<PandaMethod>,
        targets: List<PandaTarget> = emptyList(),
    ): List<PandaState> {
        val initialStates = mutableMapOf<PandaMethod, PandaState>()

        methods.forEach {
            initialStates[it] = interpreter.getInitialState(it, targets)
        }

        val methodsToTrackCoverage =
            when (options.coverageZone) {
                CoverageZone.METHOD,
                CoverageZone.TRANSITIVE,
                -> methods.toSet()
                // TODO: more adequate method filtering. !it.isConstructor is used to exclude default constructor which is often not covered
                CoverageZone.CLASS -> methods.flatMap { method ->
                    method.enclosingClass.methods.filter {
                        it.enclosingClass == method.enclosingClass /*&& !it.isConstructor*/
                    }
                }.toSet() + methods
            }

        val coverageStatistics: CoverageStatistics<PandaMethod, PandaInst, PandaState> = CoverageStatistics(
            methodsToTrackCoverage,
            applicationGraph
        )

        val callGraphStatistics: PlainCallGraphStatistics<PandaMethod> =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> TODO("Unsupported yet")
            }

        val pathSelector = createPathSelector(
            initialStates.values.single(),
            options,
            applicationGraph,
            coverageStatisticsFactory = { coverageStatistics },
            cfgStatisticsFactory = { CfgStatisticsImpl(applicationGraph) },
            callGraphStatisticsFactory = { callGraphStatistics },
            loopStatisticFactory = { null }
        )

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<PandaState>(coverageStatistics) {
                    it.methodResult is PandaMethodResult.PandaException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
                StateCollectionStrategy.ALL -> AllStatesCollector()
            }

        val observers = mutableListOf<UMachineObserver<PandaState>>(coverageStatistics)
        observers.add(statesCollector)

        val timeStatistics = TimeStatistics<PandaMethod, PandaState>()
        val stepsStatistics = StepsStatistics<PandaMethod, PandaState>()

        // TODO add statistics
        val stopStrategy = createStopStrategy(
            options,
            targets,
            timeStatisticsFactory = { timeStatistics },
            stepsStatisticsFactory = { stepsStatistics },
            coverageStatisticsFactory = { coverageStatistics },
            getCollectedStatesCount = { statesCollector.collectedStates.size }
        )

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
}
