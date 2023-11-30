package org.usvm.machine

import mu.KLogging
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.bridge.GoBridge
import org.usvm.machine.interpreter.GoInterpreter
import org.usvm.machine.state.GoState
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector

val logger = object : KLogging() {}.logger

class GoMachine(
    private val options: UMachineOptions
) : UMachine<GoState>() {
    private val bridge = GoBridge()
    private val typeSystem = GoTypeSystem(options.typeOperationsTimeout)
    private val applicationGraph = GoApplicationGraph(bridge)
    private val components = GoComponents(typeSystem, options)
    private val ctx = GoContext(components)
    private val interpreter = GoInterpreter()

    fun analyze(file: String) {
        logger.debug("{}.analyze()", this)

        bridge.initialize(file)

        val method = bridge.getMain()
        val pathSelector = createPathSelector(
            GoState(ctx),
            UMachineOptions(),
            applicationGraph,
        )
        val coverageStats: CoverageStatistics<GoMethod, GoInst, GoState> = CoverageStatistics(setOf(method), applicationGraph)
        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<GoState>(coverageStats) { false }
                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
            }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(listOf(statesCollector)),
            isStateTerminated = ::isStateTerminated,
        )
    }

    private fun isStateTerminated(state: GoState): Boolean {
        return state.callStack.isEmpty()
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}