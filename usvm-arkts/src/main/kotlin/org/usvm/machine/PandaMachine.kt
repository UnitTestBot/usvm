package org.usvm.machine

import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaProject
import org.usvm.CoverageZone
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.machine.state.PandaState
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.distances.PlainCallGraphStatistics
import kotlin.time.Duration.Companion.seconds

@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
class PandaMachine(
    project: PandaProject,
    private val options: UMachineOptions,
) : UMachine<PandaState>() {
    private val typeSystem = PandaTypeSystem(1.seconds)
    private val components = PandaComponents(typeSystem, options)
    private val ctx: PandaContext = PandaContext(components)
    private val interpreter: PandaInterpreter = PandaInterpreter(ctx)
    private val applicationGraph: PandaApplicationGraph = PandaApplicationGraph(project)

    fun analyze(
        methods: List<PandaMethod>,
        targets: List<PandaTarget> = emptyList(),
    ): List<PandaState> {
        val initialStates = mutableMapOf<PandaMethod, PandaState>()

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
            initialStates,
            options,
            applicationGraph,
            TimeStatistics<PandaMethod, PandaState>(),
//            coverageStatistics = { coverageStatistics },
            cfgStatisticsFactory = { null },
//            callGraphStatistics = { callGraphStatistics },
            loopStatisticFactory = { null }
        )

        run(
            interpreter,
            pathSelector,
            CompositeUMachineObserver(emptyList()),
            isStateTerminated = { false },
            stopStrategy = { false }
        )
        TODO()
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}