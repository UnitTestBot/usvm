package org.usvm

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.toType
import org.usvm.api.targets.JcTarget
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetController

fun StringBuilder.printTarget(target: JcTarget, indent: Int = 0) {
    appendLine("\t".repeat(indent) + target)
    target.children.forEach { printTarget(it, indent + 1) }
}

fun JcInst.printInst() = "${location.method.enclosingClass.name}#${location.method.name} | $this"

sealed class CrashReproductionTarget(location: JcInst? = null) : JcTarget(location)

class CrashReproductionLocationTarget(location: JcInst) : CrashReproductionTarget(location) {
    override fun toString(): String = location?.printInst() ?: ""
}

class CrashReproductionExceptionTarget(val exception: JcClassOrInterface) : CrashReproductionTarget() {
    override fun toString(): String = "Exception: $exception"
}

private class CrashReproductionAnalysis(
    override val targets: MutableCollection<out UTarget<*, *>>
) : UTargetController, JcInterpreterObserver, StatesCollector<JcState> {
    override val collectedStates = arrayListOf<JcState>()

    private val stats = hashMapOf<JcInst, Int>()
    private val statsNoEx = hashMapOf<JcInst, Int>()

    private fun collectStateStats(state: JcState) {
        stats[state.currentStatement] = (stats[state.currentStatement] ?: 0) + 1

        if (state.methodResult !is JcMethodResult.JcException) {
            statsNoEx[state.currentStatement] = (statsNoEx[state.currentStatement] ?: 0) + 1
        }
    }

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        collectStateStats(parent)

        propagateExceptionTarget(parent)
        propagatePrevLocationTarget(parent)

        forks.forEach {
            propagateExceptionTarget(it)
            propagatePrevLocationTarget(it)
        }

        logger.info {
            parent.currentStatement.printInst().padEnd(120) + "@@@  " + "${parent.targets.toList()}"
        }
    }

    private fun propagateCurrentLocationTarget(state: JcState) = propagateLocationTarget(state) { it.currentStatement }
    private fun propagatePrevLocationTarget(state: JcState) = propagateLocationTarget(state) {
        it.pathLocation.parent?.statement ?: error("This is impossible by construction")
    }

    private inline fun propagateLocationTarget(state: JcState, stmt: (JcState) -> JcInst) {
        val stateLocation = stmt(state)
        val targets = state.targets
            .filterIsInstance<CrashReproductionLocationTarget>()
            .filter { it.location == stateLocation }

        targets.forEach { it.propagate(state) }
    }

    private fun propagateExceptionTarget(state: JcState) {
        val mr = state.methodResult
        if (mr is JcMethodResult.JcException) {
            val exTargets = state.targets
                .filterIsInstance<CrashReproductionExceptionTarget>()
                .filter { mr.type == it.exception.toType() }
            exTargets.forEach {
                collectedStates += state.clone()
                it.propagate(state)
            }
        }
    }
}

fun reproduceCrash(cp: JcClasspath, targets: List<CrashReproductionTarget>): List<JcState> {
    val options = UMachineOptions(
        targetSearchDepth = 3u, // high values (e.g. 10) significantly degrade performance
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        stopOnTargetsReached = true,
        stopOnCoverage = -1,
        timeoutMs = 1_000_000,
        solverUseSoftConstraints = false,
        solverQueryTimeoutMs = 10_000,
        solverType = SolverType.YICES
    )
    val crashReproduction = CrashReproductionAnalysis(targets.toMutableList())

    val entrypoint = targets.mapNotNull { it.location?.location?.method }.distinct().single()

    JcMachine(cp, options, crashReproduction).use { machine ->
        machine.analyze(entrypoint, targets)
    }

    return crashReproduction.collectedStates
}
