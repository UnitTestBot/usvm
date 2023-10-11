package org.usvm

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.toType
import org.usvm.api.targets.JcTarget
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcTargetWeighter
import org.usvm.machine.JcVirtualMethodCallInst
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetController
import org.usvm.util.originalInst

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

sealed interface Distance : Comparable<Distance>

object InfiniteDistance : Distance {
    override fun compareTo(other: Distance): Int = when (other) {
        is InfiniteDistance -> 0
        is ConcreteDistance -> 1
    }
}

data class ConcreteDistance(val value: UInt) : Distance {
    override fun compareTo(other: Distance): Int = when (other) {
        is ConcreteDistance -> value.compareTo(other.value)
        is InfiniteDistance -> -1
    }
}

sealed interface ScaledDistance {
    fun toUInt(): UInt
    fun subscale(scaling: DistanceScaling, distance: Distance): ScaledDistance
    fun add(other: Distance): ScaledDistance
}

object InfiniteScaledDistance : ScaledDistance {
    override fun toUInt(): UInt = UInt.MAX_VALUE
    override fun subscale(scaling: DistanceScaling, distance: Distance): ScaledDistance = InfiniteScaledDistance
    override fun add(other: Distance): ScaledDistance = InfiniteScaledDistance
}

class ConcreteScaledDistance(
    private val scaling: DistanceScaling,
    private val ticks: UInt,
    private val value: UInt
) : ScaledDistance {
    init {
        check(value < scaling.tickSize) { "Incorrect scaling" }
    }

    override fun toUInt(): UInt = (ticks * scaling.tickSize + value)
        .also { check(it < UInt.MAX_VALUE) { "Incorrect value" } }

    override fun subscale(scaling: DistanceScaling, distance: Distance): ScaledDistance = when (distance) {
        is InfiniteDistance -> InfiniteScaledDistance
        is ConcreteDistance -> {
            val newScaling = DistanceScaling(this.scaling.scale * scaling.scale)
            check(distance.value < scaling.scale) { "Incorrect subscale" }

            val newTicks = ticks * scaling.scale + distance.value
            val newValue = value * scaling.scale + 0u

            ConcreteScaledDistance(newScaling, newTicks, newValue)
        }
    }

    override fun add(other: Distance): ScaledDistance = when (other) {
        InfiniteDistance -> InfiniteScaledDistance
        is ConcreteDistance -> {
            val clippedValue = (value + other.value).coerceAtMost(scaling.tickSize - 1u)
            ConcreteScaledDistance(scaling, ticks, clippedValue)
        }
    }
}

class DistanceScaling(val scale: UInt) {
    val tickSize: UInt = UInt.MAX_VALUE / scale

    fun scale(value: UInt) = ConcreteScaledDistance(this, value, 0u)
}

class CrashTargetInterprocDistanceCalculator(
    private val baseDistance: ScaledDistance,
    private val target: CrashReproductionLocationTarget,
    private val applicationGraph: JcApplicationGraph,
    private val cfgStatistics: CfgStatistics<JcMethod, JcInst>,
    private val callGraphStatistics: CallGraphStatistics<JcMethod>
) {
    private val targetLocation: JcInst = requireNotNull(target.location)
    private val targetMethod: JcMethod by lazy { applicationGraph.methodOf(targetLocation) }

    private val targetCallStack by lazy {
        resolveTargetCallStack()
    }

    private val targetMethodScaling: DistanceScaling by lazy {
        DistanceScaling(targetMethod.instList.size.toUInt())
    }

    private fun resolveTargetCallStack(): List<JcMethod> {
        val targetCallStack = arrayListOf<JcMethod>()

        var current: CrashReproductionLocationTarget? = target
        while (current != null) {
            targetCallStack.add(current.location!!.location.method)
            current = current.parent as? CrashReproductionLocationTarget
        }

        return targetCallStack.asReversed()
    }

    fun calculateDistance(state: JcState): ScaledDistance {
        val hasMethodResult = state.methodResult is JcMethodResult.Success
        val statement = state.currentStatement
        val normalizedCallStack = state.callStack.toMutableList()
        var normalizedStatement = statement.originalInst()
        if (statement is JcConcreteMethodCallInst) {
            val entrypoint = statement.entrypoint
            if (entrypoint != null) {
                normalizedStatement = entrypoint
                normalizedCallStack += UCallStackFrame(statement.method, statement.returnSite)
            }
        }
        return calculateDistanceNormalized(normalizedStatement, normalizedCallStack, hasMethodResult)
    }

    private val statementOccurrences = hashMapOf<JcInst, UInt>()
    private fun incOccurrences(statement: JcInst): UInt {
        val current = statementOccurrences[statement] ?: 0u
        statementOccurrences[statement] = current + 1u
        return current
    }

    private fun calculateDistanceNormalized(
        statement: JcInst,
        callStack: List<UCallStackFrame<JcMethod, JcInst>>,
        hasMethodResult: Boolean
    ): ScaledDistance {
        var targetIdx = 0
        var currentIdx = 0
        while (targetIdx < targetCallStack.size && currentIdx < callStack.size) {
            if (targetCallStack[targetIdx] != callStack[currentIdx].method) break
            targetIdx++
            currentIdx++
        }

        val targetRemain = targetCallStack.size - targetIdx
        val currentRemain = callStack.size - currentIdx

        if (targetRemain == 0 && currentRemain == 0) {
            return if (hasMethodResult) {
                val distance = calculateMethodResultLocalDistance(statement)
                baseDistance
                    .subscale(targetMethodScaling, distance)
                    .addStatementDecay(statementOccurrences = 0u)
            } else {
                val occurrences = incOccurrences(statement)
                val distance = calculateLocalDistance(statement)
                baseDistance
                    .subscale(targetMethodScaling, distance)
                    .addStatementDecay(occurrences)
            }
        }

        if (targetRemain > 0 && currentRemain == 0) {
            val upStackDistance = calculateUpStackDistance(statement, targetCallStack[targetIdx])
            return baseDistance.add(upStackDistance) // note: no method scaling
        }

        if (targetRemain == 0 && currentRemain > 0) {
            val returnSite = callStack[currentIdx].returnSite ?: return InfiniteScaledDistance
            val returnSiteDistance = calculateMethodResultLocalDistance(returnSite)
            if (returnSiteDistance == InfiniteDistance) return InfiniteScaledDistance

            val downStackDistance = calculateDownStackDistance(statement, callStack.subList(currentIdx, callStack.size))

            val occurrences = incOccurrences(returnSite)
            return baseDistance
                .subscale(targetMethodScaling, returnSiteDistance)
                .addStatementDecay(occurrences)
                .add(downStackDistance)
        }

        return InfiniteScaledDistance
    }

    private fun calculateMethodResultLocalDistance(inst: JcInst): Distance =
        applicationGraph.successors(inst)
            .map { calculateLocalDistance(it) }
            .minOrNull()
            ?: InfiniteDistance

    private fun calculateLocalDistance(statement: JcInst): Distance {
        val statementMethod = applicationGraph.methodOf(statement)

        check(targetMethod == statementMethod) {
            "No local distance for different methods"
        }

        return shortestDistance(statementMethod, statement, targetLocation)
    }

    private fun calculateUpStackDistance(
        statement: JcInst,
        nextTargetMethod: JcMethod
    ): Distance {
        var minDistanceToCall: Distance = InfiniteDistance
        val statementMethod = applicationGraph.methodOf(statement)
        for (statementOfMethod in applicationGraph.statementsOf(statementMethod)) {
            for (callee in applicationGraph.callees(statementOfMethod)) {
                if (callGraphStatistics.checkReachability(callee, nextTargetMethod)) {
                    val distanceToCall = shortestDistance(statementMethod, statement, statementOfMethod)
                    minDistanceToCall = minOf(minDistanceToCall, distanceToCall)
                }
            }
        }
        return minDistanceToCall
    }

    private fun calculateDownStackDistance(
        statement: JcInst,
        returnStack: List<UCallStackFrame<JcMethod, JcInst>>
    ): Distance {
        val statementMethod = applicationGraph.methodOf(statement)
        check(statementMethod == returnStack.last().method) {
            "Statement method not in stack"
        }

        var distance = 0u
        var currentStatement = statement
        for ((method, returnStatement) in returnStack.asReversed()) {
            distance *= 2u
            distance += cfgStatistics.getShortestDistanceToExit(method, currentStatement)
            currentStatement = returnStatement ?: break
        }

        return ConcreteDistance(distance.coerceAtMost(UInt.MAX_VALUE - 1u))
    }

    private fun shortestDistance(fromMethod: JcMethod, fromStatement: JcInst, toStatement: JcInst): Distance {
        val distance = cfgStatistics.getShortestDistance(fromMethod, fromStatement, toStatement)
        return if (distance != UInt.MAX_VALUE) ConcreteDistance(distance) else InfiniteDistance
    }

    @Suppress("UNUSED_PARAMETER")
    private fun ScaledDistance.addStatementDecay(statementOccurrences: UInt) = this
}

private class CrashReproductionAnalysis(
    override val targets: MutableCollection<out UTarget<*, *>>
) : UTargetController, JcInterpreterObserver, StatesCollector<JcState>, JcTargetWeighter {

    private val targetWeight = hashMapOf<CrashReproductionTarget, UInt>()
    init {
        targets.forEach { computeTargetWeights(it, weight = 0u) }
    }

    private fun computeTargetWeights(target: UTarget<*, *>, weight: UInt) {
        if (target !is CrashReproductionTarget) return
        targetWeight.putIfAbsent(target, weight)
        target.children.forEach { computeTargetWeights(it, weight + 1u) }
    }

    override val collectedStates = arrayListOf<JcState>()

    private val stats = hashMapOf<JcInst, Int>()
    private val statsNoEx = hashMapOf<JcInst, Int>()

    private fun collectStateStats(state: JcState) {
        stats[state.currentStatement] = (stats[state.currentStatement] ?: 0) + 1

        if (state.methodResult !is JcMethodResult.JcException) {
            statsNoEx[state.currentStatement] = (statsNoEx[state.currentStatement] ?: 0) + 1
        }
    }

    override fun createWeighter(
        strategy: PathSelectionStrategy,
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): (JcTarget, JcState) -> UInt? = { target, state ->
        if (target !is CrashReproductionTarget) {
            null
        } else {
            weightTarget(applicationGraph, cfgStatistics, callGraphStatistics, target, state)
        }
    }

    private val distanceCalculators =
        hashMapOf<CrashReproductionLocationTarget, CrashTargetInterprocDistanceCalculator>()

    private fun weightTarget(
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        target: CrashReproductionTarget,
        state: JcState
    ): UInt = when (target) {
        is CrashReproductionExceptionTarget -> 0u
        is CrashReproductionLocationTarget -> {
            val distanceCalculator = distanceCalculators.getOrPut(target) {
                val maxTargetWeight = targetWeight.size.toUInt()
                val targetScaling = DistanceScaling(maxTargetWeight)
                val targetWeight = targetWeight.getValue(target)

                CrashTargetInterprocDistanceCalculator(
                    baseDistance = targetScaling.scale(maxTargetWeight - 1u - targetWeight),
                    target = target,
                    applicationGraph = applicationGraph,
                    cfgStatistics = cfgStatistics,
                    callGraphStatistics = callGraphStatistics
                )
            }

            val interprocDistance = distanceCalculator.calculateDistance(state)

            if (interprocDistance == InfiniteScaledDistance) {
                logger.info { "UNREACHABLE: ${target.location?.printInst()} | ${state.currentStatement.printInst()}" }
            }

            interprocDistance.toUInt()
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
            val targets = parent.targets.toList()
            parent.currentStatement.printInst().padEnd(120) + "@@@  " + "$targets"
        }
    }

    private fun propagateCurrentLocationTarget(state: JcState) =
        propagateLocationTarget(state) { it.currentStatement.originalInst() }

    private fun propagatePrevLocationTarget(state: JcState) = propagateLocationTarget(state) {
        it.pathLocation.parent?.statement?.originalInst() ?: error("This is impossible by construction")
    }

    private inline fun propagateLocationTarget(state: JcState, stmt: (JcState) -> JcInst) {
        if (state.currentStatement is JcVirtualMethodCallInst) {
            return
        }

        val stateLocation = stmt(state)
        val targets = state.targets
            .filterIsInstance<CrashReproductionLocationTarget>()
            .filter {
                it.location == stateLocation
//                        && checkTargetCallStack(it, state) == ReachabilityKind.LOCAL
            }

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

fun reproduceCrash(cp: JcClasspath, target: CrashReproductionTarget): List<JcState> {
    val options = UMachineOptions(
        targetSearchDepth = 1u, // high values (e.g. 10) significantly degrade performance
        pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
        stopOnTargetsReached = true,
        stopOnCoverage = -1,
        timeoutMs = 1_000_000,
        solverUseSoftConstraints = false,
        solverQueryTimeoutMs = 10_000,
        solverType = SolverType.YICES
    )
    val jcOptions = JcMachineOptions(
        virtualCallForkOnRemainingTypes = false
    )

    val crashReproduction = CrashReproductionAnalysis(mutableListOf(target))

    val entrypoint = target.location?.location?.method ?: error("No entrypoint")

    JcMachine(cp, options, crashReproduction, jcOptions).use { machine ->
        machine.analyze(entrypoint, listOf(target))
    }

    return crashReproduction.collectedStates
}
