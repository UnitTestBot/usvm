package org.usvm.api.crash

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.SolverType
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcComponents
import org.usvm.machine.JcContext
import org.usvm.machine.JcTransparentInstruction
import org.usvm.machine.JcTypeSystem
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.stopstrategies.GroupedStopStrategy
import org.usvm.stopstrategies.StopStrategy
import org.usvm.stopstrategies.TimeoutStopStrategy
import org.usvm.util.originalInst
import kotlin.time.Duration

class JcCrashReproduction(val cp: JcClasspath, private val timeout: Duration) : UMachine<JcState>() {
    val options = UMachineOptions(
        solverType = SolverType.YICES,
        timeoutMs = timeout.inWholeMilliseconds,
        stopOnCoverage = -1
    )

    data class CrashStackTraceFrame(
        val method: JcMethod,
        val inst: JcInst
    )

    private val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem, options.solverType, options.useSolverForForks)
    private val ctx = JcContext(cp, components)

    private val interpreter = JcInterpreter(ctx, applicationGraph, observer = null)

    private val cfgStatistics = transparentCfgStatistics()

    private var crashReproductionComplete: Boolean = false

    fun reproduceCrash(
        crashException: JcClassOrInterface,
        crashStackTrace: List<CrashStackTraceFrame>
    ): Boolean {
        val stopStrategy = GroupedStopStrategy(
            TimeoutStopStrategy(timeout.inWholeMilliseconds, System::currentTimeMillis),
            StopStrategy { crashReproductionComplete }
        )

        val observer = CompositeUMachineObserver<JcState>()

        val pathSelector = PobPathSelector(TargetedPathSelector())

        run(
            interpreter,
            pathSelector,
            observer = observer,
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )

        return crashReproductionComplete
    }

    private fun transparentCfgStatistics() = object : CfgStatistics<JcMethod, JcInst> {
        private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

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

    inner class PobPathSelector(
        val base: UPathSelector<JcState>
    ): UPathSelector<JcState>{
        override fun isEmpty(): Boolean {
            TODO("Not yet implemented")
        }

        override fun peek(): JcState {
            TODO("Not yet implemented")
        }

        override fun update(state: JcState) {
            TODO("Not yet implemented")
        }

        override fun add(states: Collection<JcState>) {
            TODO("Not yet implemented")
        }

        override fun remove(state: JcState) {
            TODO("Not yet implemented")
        }
    }

    inner class TargetedPathSelector : UPathSelector<JcState> {
        override fun isEmpty(): Boolean {
            TODO("Not yet implemented")
        }

        override fun peek(): JcState {
            TODO("Not yet implemented")
        }

        override fun update(state: JcState) {
            TODO("Not yet implemented")
        }

        override fun add(states: Collection<JcState>) {
            TODO("Not yet implemented")
        }

        override fun remove(state: JcState) {
            TODO("Not yet implemented")
        }
    }
}
