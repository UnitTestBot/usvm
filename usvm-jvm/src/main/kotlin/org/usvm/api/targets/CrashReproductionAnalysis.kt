package org.usvm.api.targets

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.toType
import org.usvm.BannedState
import org.usvm.PathSelectionStrategy
import org.usvm.PathsTrieNode
import org.usvm.SolverType
import org.usvm.UCallStackFrame
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.UnsatBannedState
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.UPriorityCollection
import org.usvm.constraints.LocationConstraintSource
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.logger
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodEntrypointInst
import org.usvm.machine.JcPathSelectorProvider
import org.usvm.machine.JcTargetBlackLister
import org.usvm.machine.JcTargetWeighter
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.ps.ExceptionPropagationPathSelector
import org.usvm.ps.StateWeighter
import org.usvm.ps.TargetWeight
import org.usvm.ps.WeightedPathSelector
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

class DistanceTargetWeight(val distance: Distance) : TargetWeight {
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

    override fun toString(): String = "INF"
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

    override fun toString(): String =
        "(${kind.name} $value" + (child?.toString()?.let { " $it" } ?: "") + ")"
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

    fun calculateDistance(state: JcState, statement: JcInst): Distance {
        val hasMethodResult = state.methodResult is JcMethodResult.Success
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

class BudgetPsFrame(
    var currentBudget: UInt,
    val location: PathsTrieNode<JcState, JcInst>,
    val bannedLocation: PathsTrieNode<JcState, JcInst>,
    val possibleLocationAlternatives: List<PathsTrieNode<JcState, JcInst>>,
    val otherStates: MutableList<JcState>,
    val parentFrame: BudgetPsFrame?
)

data class LocationBudgetInfo(
    val requestedBudget: UInt = 0u,
    val unusedBudget: UInt = 0u,
)

class LocationBudgetPs(
    val base: UPathSelector<JcState>
) : UPathSelector<JcState> {
    private var overallStepsAmount = 0u
    private val budgetHistory = hashMapOf<PathsTrieNode<JcState, JcInst>, LocationBudgetInfo>()
    private var locationBudgetFrame: BudgetPsFrame? = null

    private fun shouldForceLocation(location: PathsTrieNode<JcState, JcInst>, budget: UInt): Boolean {
        val unusedLocationBudget = budgetHistory[location]?.unusedBudget ?: 0u
        return unusedLocationBudget <= budget
    }

    private fun incRequestedBudget(location: PathsTrieNode<JcState, JcInst>, budget: UInt) {
        val currentUsage = budgetHistory[location] ?: LocationBudgetInfo()
        budgetHistory[location] = currentUsage.copy(requestedBudget = currentUsage.requestedBudget + budget)
    }

    private fun incUnusedBudget(location: PathsTrieNode<JcState, JcInst>, budget: UInt) {
        val currentUsage = budgetHistory[location] ?: LocationBudgetInfo()
        budgetHistory[location] = currentUsage.copy(unusedBudget = currentUsage.unusedBudget + budget)
    }

    fun giveBudget(
        location: PathsTrieNode<JcState, JcInst>,
        bannedLocation: PathsTrieNode<JcState, JcInst>,
        possibleLocations: List<PathsTrieNode<JcState, JcInst>>,
        budget: UInt
    ) {
        val unusedLocationBudget = budgetHistory[location]?.unusedBudget ?: 0u
        if (unusedLocationBudget <= budget) {
            giveBudget(location, bannedLocation, possibleLocations, budget, strict = true)
            return
        }

//        val otherPossibleLocations = possibleLocations.filter { it != bannedLocation && it != location }
//        if (otherPossibleLocations.isNotEmpty()) {
//            // consider other locations
//            giveBudget(
//                otherPossibleLocations.random(),
//                bannedLocation,
//                otherPossibleLocations + bannedLocation,
//                budget
//            )
//            return
//        }

        giveBudget(location, bannedLocation, possibleLocations, budget, strict = false)
    }

    private fun giveBudget(
        location: PathsTrieNode<JcState, JcInst>,
        bannedLocation: PathsTrieNode<JcState, JcInst>,
        possibleLocations: List<PathsTrieNode<JcState, JcInst>>,
        budget: UInt,
        strict: Boolean
    ) {
        val currentFrame = locationBudgetFrame
        if (currentFrame == null) {
            pushForcedLocation(location, bannedLocation, possibleLocations, budget, strict)
            return
        }

        if (location in currentFrame.possibleLocationAlternatives) {
            logger.info { "Cyclic budget request at location: $location" }
            popForcedLocation()
            return
        }

        // Force location with lower forks amount
        if (location.depth < currentFrame.location.depth) {
            popForcedLocation(reportUnusedBudget = false)
            giveBudget(location, bannedLocation, possibleLocations, budget, strict)
            return
        }

        val nestedBudget = currentFrame.currentBudget / possibleLocations.size.toUInt()
        currentFrame.currentBudget -= nestedBudget
        if (nestedBudget == 0u) return

        pushForcedLocation(location, bannedLocation, possibleLocations, nestedBudget, strict)
    }

    private fun pushForcedLocation(
        location: PathsTrieNode<JcState, JcInst>,
        bannedLocation: PathsTrieNode<JcState, JcInst>,
        possibleLocations: List<PathsTrieNode<JcState, JcInst>>,
        budget: UInt,
        strict: Boolean
    ) {
        incRequestedBudget(location, budget)
        logger.info { "Give budget $budget (${budgetHistory[location]}) to location: $location" }

        val otherStates = mutableListOf<JcState>()
        val frame = BudgetPsFrame(
            budget, location, bannedLocation, possibleLocations, otherStates, locationBudgetFrame
        )
        locationBudgetFrame = frame

        val statesInLocation = mutableListOf<JcState>()
        while (!base.isEmpty()) {
            val state = base.peek()
            base.remove(state)

            if (hasLocation(state, frame.location, strict)) {
                statesInLocation.add(state)
            } else {
                frame.otherStates.add(state)
            }
        }

        base.add(statesInLocation)
    }

    private fun popForcedLocation(reportUnusedBudget: Boolean = true) {
        val frame = locationBudgetFrame ?: error("No forced location")

        if (reportUnusedBudget) {
            incUnusedBudget(frame.location, frame.currentBudget)
        }

        base.add(frame.otherStates)
        locationBudgetFrame = frame.parentFrame
    }

    private fun hasLocation(
        state: JcState,
        location: PathsTrieNode<JcState, JcInst>,
        strict: Boolean
    ): Boolean {
        var stateLocation = state.pathLocation
        while (stateLocation.depth > location.depth) {
            stateLocation = stateLocation.parent ?: return false
        }

        if (strict) return stateLocation == location

        var loc = location
        while (loc.depth > stateLocation.depth) {
            loc = loc.parent ?: return false
        }

        return loc == stateLocation
    }

    override fun isEmpty(): Boolean {
        return base.isEmpty() && (locationBudgetFrame?.otherStates?.isEmpty() ?: true)
    }

    override fun peek(): JcState {
        overallStepsAmount++

        val frame = locationBudgetFrame
        if (frame != null && frame.currentBudget > 0u) {
            frame.currentBudget--
        }

        if (frame?.currentBudget == 0u || base.isEmpty()) {
            popForcedLocation()
        }

        return base.peek()
    }

    override fun update(state: JcState) {
        base.update(state)
    }

    override fun add(states: Collection<JcState>) {
        val frame = locationBudgetFrame
        if (frame == null) {
        base.add(states)
            return
        }

        val relevantStates = mutableListOf<JcState>()
        for (state in states) {
            if (hasLocation(state, frame.bannedLocation, strict = true)) {
                frame.otherStates.add(state)
                continue
            }
            relevantStates.add(state)
        }

        base.add(relevantStates)
    }

    override fun remove(state: JcState) {
        var frame = locationBudgetFrame
        while (frame != null) {
            if (frame.otherStates.remove(state)) return
            frame = frame.parentFrame
        }
        base.remove(state)
    }
}

private class CrashReproductionWeightedPathSelector(
    priorityCollectionFactory: () -> UPriorityCollection<JcState, DistanceTargetWeight>,
    private val weighter: StateWeighter<JcState, DistanceTargetWeight>
) : WeightedPathSelector<JcState, DistanceTargetWeight>(priorityCollectionFactory, weighter) {
    override fun add(states: Collection<JcState>) {
        val reachableStates = states.filter {
            val weight = weighter.weight(it)
            weight.distance !is InfiniteDistance
        }
        super.add(reachableStates)
    }
}

private class CrashReproductionAnalysis(
    override val targets: MutableCollection<out UTarget<*, *>>
) : UTargetController,
    JcInterpreterObserver,
    StatesCollector<JcState>,
    JcTargetWeighter<DistanceTargetWeight>,
    JcTargetBlackLister,
    JcPathSelectorProvider {

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
            val distance = weightTarget(
                applicationGraph, cfgStatistics, callGraphStatistics, target, state, state.currentStatement
            )
            DistanceTargetWeight(distance)
        }
    }

    override fun createBlacklist(
        baseBlackList: UForkBlackList<JcState, JcInst>,
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): UForkBlackList<JcState, JcInst> = object : UForkBlackList<JcState, JcInst> {
        override fun shouldForkTo(state: JcState, stmt: JcInst): Boolean {
//            return baseBlackList.shouldForkTo(state, stmt)
            val (crashTargets, otherTargets) = state.targets.partition { it is CrashReproductionTarget }
            if (otherTargets.isNotEmpty()) {
                TODO("Crash reproduction target combination")
            }

            val result = crashTargets.any { target ->
                target as CrashReproductionTarget
                val distance = weightTarget(applicationGraph, cfgStatistics, callGraphStatistics, target, state, stmt)
                distance != InfiniteDistance
            }

            return result
        }
    }

    private lateinit var targetWeightPs: WeightedPathSelector<JcState, DistanceTargetWeight>
    private lateinit var budgetPs: LocationBudgetPs

    override fun createPathSelector(
        initialState: JcState,
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): UPathSelector<JcState> {
        val weighter = createWeighter(
            PathSelectionStrategy.TARGETED, applicationGraph, cfgStatistics, callGraphStatistics
        )
        targetWeightPs = CrashReproductionWeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { state ->
                val (crashTargets, otherTargets) = state.targets.partition { it is CrashReproductionTarget }
                if (otherTargets.isNotEmpty()) {
                    TODO("Crash reproduction target combination")
                }

                crashTargets.mapNotNull { weighter(it, state) }
                    .minOrNull()
                    ?: DistanceTargetWeight(InfiniteDistance)
            }
        )

        budgetPs = LocationBudgetPs(
//            ExceptionPropagationPathSelector(targetWeightPs)
            targetWeightPs
        )

//        val selector = budgetPs
        val selector = ExceptionPropagationPathSelector(budgetPs)
        selector.add(listOf(initialState))

        return selector
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
        state: JcState,
        statement: JcInst
    ): Distance {
        when (target) {
            is CrashReproductionExceptionTarget -> return targetDistance(target)
            is CrashReproductionLocationTarget -> {
                val targetDistance = targetDistance(target)

                if (statement is JcMethodEntrypointInst) {
                    return targetDistance
                }

                val distanceCalculator = distanceCalculators.getOrPut(target) {
                    CrashTargetInterprocDistanceCalculator(
                        target = target,
                        applicationGraph = applicationGraph,
                        cfgStatistics = cfgStatistics,
                        callGraphStatistics = callGraphStatistics
                    )
                }

                val locationDistance = distanceCalculator.calculateDistance(state, statement)

                if (locationDistance is InfiniteDistance) {
                    logger.info { "UNREACHABLE: ${target.location?.printInst()} | ${statement.printInst()}" }
                }

                val result = targetDistance.addNested(locationDistance)

                if (target.parent == deepestTarget) {
                    if (target.location?.location?.method == statement.location.method) {
                        logger.info { "DISTANCE ${result.root()} | ${statement.printInst()} | ${target.location?.printInst()}" }
                    }
                }

                return result
            }
        }
    }

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        collectStateStats(parent)

        propagateLocationTarget(parent)
        propagateExceptionTarget(parent)

        forks.forEach {
            propagateLocationTarget(it)
            propagateExceptionTarget(it)
        }

        logger.debug {
            val targets = parent.targets.toList()
            parent.currentStatement.printInst().padEnd(120) + "@@@  " + "$targets"
        }
    }

    override fun onStateDeath(state: JcState, bannedStates: Sequence<BannedState>) {
        logger.warn { "State death: ${bannedStates.toList()}" }

//        if (!bannedStates.any { it is BlackListBannedState<*> }) return

        val conflicts = bannedStates.filterIsInstance<UnsatBannedState>().toList()

        val possibleConflictLocations = conflicts
            .flatMap { conflict -> conflict.core.core.map { it.second } }
            .filterIsInstance<LocationConstraintSource>()
            .filter { it.location.depth < state.pathLocation.depth }
            .map { it.location }

        val conflictLocation = possibleConflictLocations.randomOrNull() ?: return
        var nextToConflictLocation = state.pathLocation
        while (nextToConflictLocation.parent != conflictLocation) {
            nextToConflictLocation = nextToConflictLocation.parent ?: return
        }

        val possibleForks = conflictLocation.children.values.toList()

        val alternativeForkLocations = possibleForks.filter { it != nextToConflictLocation }
        if (alternativeForkLocations.isEmpty()) return

        @Suppress("UNCHECKED_CAST")
        budgetPs.giveBudget(
            alternativeForkLocations.random() as PathsTrieNode<JcState, JcInst>,
            nextToConflictLocation,
            possibleForks as List<PathsTrieNode<JcState, JcInst>>,
            state.pathLocation.depth.toUInt()
        )
    }

    private fun propagateLocationTarget(state: JcState) {
        val stateLocation = state.currentStatement.originalInst()
        // todo: don't delay target propagation, change weighter instead
        if (stateLocation.callExpr != null && state.currentStatement !is JcConcreteMethodCallInst) {
            return
        }

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
