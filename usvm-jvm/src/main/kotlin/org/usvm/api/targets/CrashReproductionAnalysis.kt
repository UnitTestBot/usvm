package org.usvm.api.targets

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.toType
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UCallStackFrame
import org.usvm.UMachineOptions
import org.usvm.logger
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcTargetWeighter
import org.usvm.machine.JcVirtualMethodCallInst
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.ps.TargetWeight
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

class DistanceTargetWeight(private val distance: Distance) : TargetWeight {
    private val distanceRoot = distance.root()
    override fun compareTo(other: TargetWeight): Int = if (other is DistanceTargetWeight) {
        distanceRoot.compareTo(other.distanceRoot)
    } else {
        -other.compareRhs(this)
    }

    override fun compareRhs(other: TargetWeight): Int = -1

    override fun toDouble(): Double {
        TODO("Not yet implemented")
    }
}

sealed interface Distance : Comparable<Distance> {
    fun addNested(other: Distance): Distance
    fun addBias(bias: UInt): Distance
    fun root(): Distance
    fun tail(): Distance
}

object InfiniteDistance : Distance {
    override fun root(): Distance = InfiniteDistance
    override fun tail(): Distance = InfiniteDistance
    override fun addNested(other: Distance): Distance = InfiniteDistance
    override fun addBias(bias: UInt): Distance = InfiniteDistance
    override fun compareTo(other: Distance): Int = when (other) {
        is InfiniteDistance -> 0
        is ConcreteDistance -> 1
    }
}

enum class DistanceKind {
    TARGET,
    METHOD,
    PATH_LENGTH,
}

class ConcreteDistance(
    private val kind: DistanceKind,
    private var value: UInt,
    private val maxValue: UInt,
) : Distance {
    init {
        check(value <= maxValue) { "Incorrect distance" }
    }

    private var parent: ConcreteDistance? = null
    private var child: ConcreteDistance? = null

    override fun compareTo(other: Distance): Int = when (other) {
        is ConcreteDistance -> when (kind) {
            DistanceKind.TARGET -> compareTarget(other)
            DistanceKind.METHOD -> compareMethod(other)
            DistanceKind.PATH_LENGTH -> comparePathLength(other)
        }

        is InfiniteDistance -> -1
    }

    // target always less than others
    private fun compareTarget(other: ConcreteDistance): Int =
        if (other.kind != DistanceKind.TARGET) -1 else compareValueAndChildren(other)

    private fun compareMethod(other: ConcreteDistance): Int = when (other.kind) {
        // target always less than others
        DistanceKind.TARGET -> 1
        // Equal method distance, but current has one more method on call stack
        DistanceKind.PATH_LENGTH -> 1
        DistanceKind.METHOD -> compareValueAndChildren(other)
    }

    private fun comparePathLength(other: ConcreteDistance): Int = when (other.kind) {
        // target always less than others
        DistanceKind.TARGET -> 1
        // Equal method distance, but other has one more method on call stack
        DistanceKind.METHOD -> -1
        DistanceKind.PATH_LENGTH -> compareValueAndChildren(other)
    }

    private fun compareValueAndChildren(other: ConcreteDistance): Int {
        val valueCompare = value.compareTo(other.value)
        if (valueCompare != 0) return valueCompare

        val thisChild = child ?: return if (other.child == null) 0 else -1
        val otherChild = other.child ?: return 1
        return thisChild.compareTo(otherChild)
    }

    override fun addNested(other: Distance): Distance = when (other) {
        is InfiniteDistance -> InfiniteDistance
        is ConcreteDistance -> {
            val tail = tail() as ConcreteDistance
            val otherRoot = other.root() as ConcreteDistance

            otherRoot.parent = tail
            tail.child = otherRoot

            otherRoot.updateParentBias()

            other
        }
    }

    override fun addBias(bias: UInt): Distance {
        value += bias
        updateParentBias()
        return this
    }

    private fun updateParentBias() {
        val parent = parent ?: return
        parent.addBias(value / (maxValue + 1u))
        value %= (maxValue + 1u)
    }

    override fun root(): Distance {
        var root: ConcreteDistance = this
        while (root.parent != null) {
            root = root.parent!!
        }
        return root
    }

    override fun tail(): Distance {
        var tail: ConcreteDistance = this
        while (tail.child != null) {
            tail = tail.child!!
        }
        return tail
    }
}

class CrashTargetInterprocDistanceCalculator(
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

    private fun resolveTargetCallStack(): List<JcMethod> {
        val targetCallStack = arrayListOf<JcMethod>()

        var current: CrashReproductionLocationTarget? = target
        while (current != null) {
            targetCallStack.add(current.location!!.location.method)
            current = current.parent as? CrashReproductionLocationTarget
        }

        return targetCallStack.asReversed()
    }

    fun calculateDistance(state: JcState): Distance {
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
        val distance = calculateDistanceNormalized(normalizedStatement, normalizedCallStack, hasMethodResult)
        val pathLenDistance = ConcreteDistance(
            DistanceKind.PATH_LENGTH,
            state.pathLocation.depth.toUInt(),
            UInt.MAX_VALUE - 1u
        )
        return distance.addNested(pathLenDistance)
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
    ): Distance {
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
                calculateMethodResultLocalDistance(statement)
            } else {
                val occurrences = incOccurrences(statement)
                val distance = calculateLocalDistance(statement)
                distance.addStatementDecay(occurrences)
            }
        }

        if (targetRemain > 0 && currentRemain == 0) {
            return calculateUpStackDistance(statement, targetCallStack[targetIdx])
        }

        if (targetRemain == 0 && currentRemain > 0) {
            val returnSite = callStack[currentIdx].returnSite ?: return InfiniteDistance
            val returnSiteDistance = calculateMethodResultLocalDistance(returnSite)
            if (returnSiteDistance == InfiniteDistance) return InfiniteDistance

            val downStackDistance = calculateDownStackDistance(statement, callStack.subList(currentIdx, callStack.size))

            val occurrences = incOccurrences(returnSite)
            return returnSiteDistance
                .addStatementDecay(occurrences)
                .addNested(downStackDistance)
        }

        return InfiniteDistance
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

        return shortestLocalDistance(statementMethod, statement, targetLocation)
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
                    val distanceToCall = shortestLocalDistance(statementMethod, statement, statementOfMethod)
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

        var distance: Distance? = null
        var currentStatement = statement
        for ((method, returnStatement) in returnStack.asReversed()) {
            val distanceToExit = cfgStatistics.getShortestDistanceToExit(method, currentStatement)
            val currentDistance = methodDistance(method, distanceToExit)
            if (distance != null) {
                currentDistance.addNested(distance)
            }
            distance = currentDistance
            currentStatement = returnStatement ?: break
        }

        return distance ?: InfiniteDistance
    }

    private fun shortestLocalDistance(method: JcMethod, fromStatement: JcInst, toStatement: JcInst): Distance {
        val distance = cfgStatistics.getShortestDistance(method, fromStatement, toStatement)
        return methodDistance(method, distance)
    }

    private fun methodDistance(method: JcMethod, value: UInt): Distance {
        if (value == UInt.MAX_VALUE) return InfiniteDistance
        return ConcreteDistance(DistanceKind.METHOD, value, method.instList.size.toUInt())
    }

    @Suppress("UNUSED_PARAMETER")
    private fun Distance.addStatementDecay(statementOccurrences: UInt) = this
//        addBias(statementOccurrences / DECAY_OCCURRENCES)

    companion object {
        private const val DECAY_OCCURRENCES = 100u
    }
}

private class CrashReproductionAnalysis(
    override val targets: MutableCollection<out UTarget<*, *>>
) : UTargetController, JcInterpreterObserver, StatesCollector<JcState>, JcTargetWeighter<DistanceTargetWeight> {

    private val targetDepth = hashMapOf<CrashReproductionTarget, UInt>()

    init {
        targets.forEach { computeTargetWeights(it, weight = 0u) }
    }

    private fun computeTargetWeights(target: UTarget<*, *>, weight: UInt) {
        if (target !is CrashReproductionTarget) return
        targetDepth.putIfAbsent(target, weight)
        target.children.forEach { computeTargetWeights(it, weight + 1u) }
    }

    private var deepestTarget: CrashReproductionTarget? = null
    private var deepestWeight: UInt = 0u
    private fun notifyTargetUpdate(target: CrashReproductionTarget) {
        val curWeight = targetDepth[target] ?: 0u
        if (deepestTarget != null && curWeight <= deepestWeight) {
            return
        }

        deepestWeight = curWeight
        deepestTarget = target
        logger.info { "REACH TARGET: $deepestTarget" }
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
    ): (JcTarget, JcState) -> DistanceTargetWeight? = { target, state ->
        if (target !is CrashReproductionTarget) {
            null
        } else {
            val distance = weightTarget(applicationGraph, cfgStatistics, callGraphStatistics, target, state)
            DistanceTargetWeight(distance)
        }
    }

    private val distanceCalculators =
        hashMapOf<CrashReproductionLocationTarget, CrashTargetInterprocDistanceCalculator>()

    private fun targetDistance(target: CrashReproductionTarget): Distance = when (target) {
        is CrashReproductionExceptionTarget -> ConcreteDistance(DistanceKind.TARGET, 0u, 0u)
        is CrashReproductionLocationTarget -> {
            val maxDepth = targetDepth.size.toUInt()
            val depth = targetDepth.getValue(target)
            val distance = maxDepth - 1u - depth
            ConcreteDistance(DistanceKind.TARGET, distance, maxDepth)
        }
    }

    private fun weightTarget(
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        target: CrashReproductionTarget,
        state: JcState
    ): Distance = when (target) {
        is CrashReproductionExceptionTarget -> targetDistance(target)
        is CrashReproductionLocationTarget -> {
            val distanceCalculator = distanceCalculators.getOrPut(target) {
                CrashTargetInterprocDistanceCalculator(
                    target = target,
                    applicationGraph = applicationGraph,
                    cfgStatistics = cfgStatistics,
                    callGraphStatistics = callGraphStatistics
                )
            }

            val locationDistance = distanceCalculator.calculateDistance(state)

            if (locationDistance is InfiniteDistance) {
                logger.info { "UNREACHABLE: ${target.location?.printInst()} | ${state.currentStatement.printInst()}" }
            }

            val targetDistance = targetDistance(target)

            targetDistance.addNested(locationDistance)
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

        targets.forEach {
            notifyTargetUpdate(it)
            it.propagate(state)
        }
    }

    private fun propagateExceptionTarget(state: JcState) {
        val mr = state.methodResult
        if (mr is JcMethodResult.JcException) {
            val exTargets = state.targets
                .filterIsInstance<CrashReproductionExceptionTarget>()
                .filter { mr.type == it.exception.toType() }
            exTargets.forEach {
                collectedStates += state.clone()
                notifyTargetUpdate(it)
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
