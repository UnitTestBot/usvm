package org.usvm.ps

import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.PathSelectorFairnessStrategy
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.UState
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.algorithms.RandomizedPriorityCollection
import org.usvm.merging.CloseStatesSearcherImpl
import org.usvm.merging.MergingPathSelector
import org.usvm.statistics.ApplicationGraph
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CallStackDistanceCalculator
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.InterprocDistance
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.statistics.distances.ReachabilityKind
import org.usvm.targets.UTarget
import org.usvm.util.RealTimeStopwatch
import org.usvm.util.log2
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration

private fun <Method, Statement, Target, State> createPathSelector(
    initialStates: List<State>,
    options: UMachineOptions,
    applicationGraph: ApplicationGraph<Method, Statement>,
    coverageStatisticsFactory: () -> CoverageStatistics<Method, Statement, State>? = { null },
    cfgStatisticsFactory: () -> CfgStatistics<Method, Statement>? = { null },
    callGraphStatisticsFactory: () -> CallGraphStatistics<Method>? = { null },
    loopStatisticFactory: () -> StateLoopTracker<*, Statement, State>? = { null },
): UPathSelector<State>
    where Target : UTarget<Statement, Target>,
          State : UState<*, Method, Statement, *, Target, State> {

    val strategies = options.pathSelectionStrategies
    require(strategies.isNotEmpty()) { "At least one path selector strategy should be specified" }

    val random by lazy { Random(options.randomSeed) }

    val selectors = strategies.map { strategy ->
        when (strategy) {
            PathSelectionStrategy.BFS -> BfsPathSelector()
            PathSelectionStrategy.DFS -> DfsPathSelector()

            PathSelectionStrategy.RANDOM_PATH -> {
                val initialState = initialStates.singleOrNull()
                requireNotNull(initialState) { "Random tree path selector doesn't support multiple initial states" }

                RandomTreePathSelector.fromRoot(
                    initialState.pathNode,
                    randomNonNegativeInt = { random.nextInt(0, it) }
                )
            }

            PathSelectionStrategy.DEPTH -> createDepthPathSelector()
            PathSelectionStrategy.DEPTH_RANDOM -> createDepthPathSelector(random)

            PathSelectionStrategy.FORK_DEPTH -> createForkDepthPathSelector()
            PathSelectionStrategy.FORK_DEPTH_RANDOM -> createForkDepthPathSelector(random)

            PathSelectionStrategy.CLOSEST_TO_UNCOVERED -> createClosestToUncoveredPathSelector(
                requireNotNull(coverageStatisticsFactory()) { "Coverage statistics is required for closest to uncovered path selector" },
                requireNotNull(cfgStatisticsFactory()) { "CFG statistics is required for closest to uncovered path selector" },
                applicationGraph
            )

            PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM -> createClosestToUncoveredPathSelector(
                requireNotNull(coverageStatisticsFactory()) { "Coverage statistics is required for closest to uncovered path selector" },
                requireNotNull(cfgStatisticsFactory()) { "CFG statistics is required for closest to uncovered path selector" },
                applicationGraph,
                random
            )

            PathSelectionStrategy.TARGETED -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatisticsFactory()) { "CFG statistics is required for targeted path selector" },
                requireNotNull(callGraphStatisticsFactory()) { "Call graph statistics is required for targeted path selector" },
                applicationGraph
            )

            PathSelectionStrategy.TARGETED_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatisticsFactory()) { "CFG statistics is required for targeted path selector" },
                requireNotNull(callGraphStatisticsFactory()) { "Call graph statistics is required for targeted path selector" },
                applicationGraph,
                random
            )

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatisticsFactory()) { "CFG statistics is required for targeted call stack local path selector" },
                applicationGraph
            )

            PathSelectionStrategy.TARGETED_CALL_STACK_LOCAL_RANDOM -> createTargetedPathSelector<Method, Statement, Target, State>(
                requireNotNull(cfgStatisticsFactory()) { "CFG statistics is required for targeted call stack local path selector" },
                applicationGraph,
                random
            )
        }
    }

    selectors.singleOrNull()?.let { selector ->
        val mergingSelector = createMergingPathSelector(initialStates, selector, options, cfgStatisticsFactory)
        val resultSelector = mergingSelector.wrapIfRequired(options, loopStatisticFactory)
        resultSelector.add(initialStates.toList())
        return resultSelector
    }

    require(selectors.size >= 2) { "Cannot create collaborative path selector from less than 2 selectors" }

    val selector = when (options.pathSelectorCombinationStrategy) {
        PathSelectorCombinationStrategy.INTERLEAVED -> {
            // Since all selectors here work as one, we can wrap an interleaved selector only.
            val selector = InterleavedPathSelector(selectors)

            val mergingSelector = createMergingPathSelector(initialStates, selector, options, cfgStatisticsFactory)
            val resultSelector = mergingSelector.wrapIfRequired(options, loopStatisticFactory)
            resultSelector.add(initialStates.toList())

            resultSelector
        }

        PathSelectorCombinationStrategy.PARALLEL -> {
            // Here we should wrap all selectors independently since they work in parallel.
            val wrappedSelectors = selectors.map { selector ->
                val mergingSelector = createMergingPathSelector(initialStates, selector, options, cfgStatisticsFactory)
                mergingSelector.wrapIfRequired(options, loopStatisticFactory)
            }

            wrappedSelectors.first().add(initialStates.toList())
            wrappedSelectors.drop(1).forEach {
                it.add(initialStates.map { it.clone() }.toList())
            }

            ParallelPathSelector(wrappedSelectors)
        }
    }

    return selector
}

fun <Method, Statement, Target, State> createPathSelector(
    initialState: State,
    options: UMachineOptions,
    applicationGraph: ApplicationGraph<Method, Statement>,
    coverageStatisticsFactory: () -> CoverageStatistics<Method, Statement, State>? = { null },
    cfgStatisticsFactory: () -> CfgStatistics<Method, Statement>? = { null },
    callGraphStatisticsFactory: () -> CallGraphStatistics<Method>? = { null },
    loopStatisticFactory: () -> StateLoopTracker<*, Statement, State>? = { null },
): UPathSelector<State> where Target : UTarget<Statement, Target>, State : UState<*, Method, Statement, *, Target, State> =
    createPathSelector(
        listOf(initialState),
        options,
        applicationGraph,
        coverageStatisticsFactory,
        cfgStatisticsFactory,
        callGraphStatisticsFactory,
        loopStatisticFactory
    )

fun <Method, Statement, Target, State> createPathSelector(
    initialStates: Map<Method, State>,
    options: UMachineOptions,
    applicationGraph: ApplicationGraph<Method, Statement>,
    timeStatistics: TimeStatistics<Method, State>,
    coverageStatisticsFactory: () -> CoverageStatistics<Method, Statement, State>? = { null },
    cfgStatisticsFactory: () -> CfgStatistics<Method, Statement>? = { null },
    callGraphStatisticsFactory: () -> CallGraphStatistics<Method>? = { null },
    loopStatisticFactory: () -> StateLoopTracker<*, Statement, State>? = { null },
): UPathSelector<State> where Target : UTarget<Statement, Target>, State : UState<*, Method, Statement, *, Target, State> {
    if (options.timeout == Duration.INFINITE || initialStates.size == 1) {
        return createPathSelector(
            initialStates.values.toList(),
            options,
            applicationGraph,
            coverageStatisticsFactory,
            cfgStatisticsFactory,
            callGraphStatisticsFactory,
            loopStatisticFactory
        )
    }

    fun getRemainingTimeMs(): Duration {
        val diff = options.timeout - timeStatistics.runningTime
        return diff.coerceAtLeast(Duration.ZERO)
    }

    fun createBasePathSelector(method: Method) =
        createPathSelector(
            initialStates.getValue(method),
            options,
            applicationGraph,
            coverageStatisticsFactory,
            cfgStatisticsFactory,
            callGraphStatisticsFactory,
            loopStatisticFactory
        )

    val coverageStatistics = coverageStatisticsFactory()
    val initialStateToEntrypoint = mutableMapOf<State, Method>()
    initialStates.forEach { (m, s) -> initialStateToEntrypoint[s] = m }

    val pathSelector = when (options.pathSelectorFairnessStrategy) {
        PathSelectorFairnessStrategy.CONSTANT_TIME -> {
            val getMethodCoverage = coverageStatistics?.let {
                { m: Method -> coverageStatistics.getMethodCoverage(m) }
            } ?: { 0f }

            ConstantTimeFairPathSelector(
                initialStates.keys,
                RealTimeStopwatch(),
                ::getRemainingTimeMs,
                { it.entrypoint },
                getMethodCoverage,
                ::createBasePathSelector
            )
        }

        PathSelectorFairnessStrategy.COMPLETELY_FAIR ->
            CompletelyFairPathSelector(
                initialStates.keys,
                RealTimeStopwatch(),
                { it.entrypoint },
                ::createBasePathSelector
            )
    }

    val totalCoveragePercents = 100f
    when (options.coverageZone) {
        CoverageZone.METHOD -> {
            coverageStatistics?.addOnCoveredObserver { _, method, _ ->
                if (coverageStatistics.getMethodCoverage(method) == totalCoveragePercents) {
                    pathSelector.removeKey(method)
                }
            }
        }
        CoverageZone.CLASS -> {
            coverageStatistics?.addOnCoveredObserver { _, method, _ ->
                if (coverageStatistics.getTransitiveMethodCoverage(method) == totalCoveragePercents) {
                    pathSelector.removeKey(method)
                }
            }
        }
        CoverageZone.TRANSITIVE -> {}
    }

    return pathSelector
}

/**
 * Wraps the selector into an [ExceptionPropagationPathSelector] if [propagateExceptions] is true.
 */
private fun <State : UState<*, *, *, *, *, State>> UPathSelector<State>.wrapIfRequired(propagateExceptions: Boolean) =
    if (propagateExceptions && this !is ExceptionPropagationPathSelector<State>) {
        ExceptionPropagationPathSelector(this)
    } else {
        this
    }
private fun <Statement, Method, State : UState<*, Method, Statement, *, *, State>> UPathSelector<State>.wrapIfRequired(
    options: UMachineOptions,
    loopStatisticFactory: () -> StateLoopTracker<*, Statement, State>?
): UPathSelector<State> {
    var ps = this
    if (options.exceptionsPropagation && ps !is ExceptionPropagationPathSelector<State>) {
        ps = ExceptionPropagationPathSelector(ps)
    }
    if (options.loopIterativeDeepening && ps !is IterativeDeepeningPs<*, *, *, State>) {
        ps = createIterativeDeepeningPathSelector(ps, options, loopStatisticFactory)
    }
    if (!options.loopIterativeDeepening && options.loopIterationLimit != null && ps !is LoopLimiterPs<*, *, *, State>) {
        ps = createLoopLimiterPathSelector(ps, options, loopStatisticFactory)
    }
    return ps
}

private fun <State : UState<*, *, *, *, *, State>> compareById(): Comparator<State> = compareBy { it.id }

private fun <State : UState<*, *, *, *, *, State>> createDepthPathSelector(random: Random? = null): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { it.pathNode.depth }
        )
    }

    // Notice: random never returns 1.0
    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(it.pathNode.depth, 1) }
    )
}

private fun <Method, Statement, State : UState<*, Method, Statement, *, *, State>> createClosestToUncoveredPathSelector(
    coverageStatistics: CoverageStatistics<Method, Statement, State>,
    cfgStatistics: CfgStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    random: Random? = null,
): UPathSelector<State> {
    val distanceCalculator = CallStackDistanceCalculator(
        targets = coverageStatistics.getUncoveredStatements(),
        cfgStatistics = cfgStatistics,
        applicationGraph
    )

    coverageStatistics.addOnCoveredObserver { _, method, statement ->
        distanceCalculator.removeTarget(
            method,
            statement
        )
    }

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { distanceCalculator.calculateDistance(it.currentStatement, it.callStack) }
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = {
            1.0 / max(
                distanceCalculator.calculateDistance(it.currentStatement, it.callStack).toDouble(),
                1.0
            )
        }
    )
}

fun <Method, Statement, State : UState<*, Method, Statement, *, *, State>> createForkDepthPathSelector(
    random: Random? = null,
): UPathSelector<State> {
    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = { it.forkPoints.depth }
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(it.forkPoints.depth.toDouble(), 1.0) }
    )
}

internal fun <Method, Statement, Target, State> createMergingPathSelector(
    initialStates: List<State>,
    underlyingPathSelector: UPathSelector<State>,
    options: UMachineOptions,
    statistics: () -> CfgStatistics<Method, Statement>?,
): UPathSelector<State>
    where Target : UTarget<Statement, Target>,
          State : UState<*, Method, Statement, *, Target, State> {
    if (!options.useMerging) {
        return underlyingPathSelector
    }

    val initialState = initialStates.singleOrNull()
    requireNotNull(initialState) { "Merging path selector doesn't support multiple initial states" }

    val executionTreeTracker = ExecutionTreeTracker<State, Statement>(initialState.pathNode)
    val closeStatesSearcher = CloseStatesSearcherImpl(
        executionTreeTracker,
        requireNotNull(statistics())
    )
    val result = MergingPathSelector(
        underlyingPathSelector,
        closeStatesSearcher
    )
    return result
}

internal fun <Method, Statement, State> createIterativeDeepeningPathSelector(
    underlyingPathSelector: UPathSelector<State>,
    options: UMachineOptions,
    loopStatisticFactory: () -> StateLoopTracker<*, Statement, State>?,
): UPathSelector<State> where State : UState<*, Method, Statement, *, *, State> {
    val loopTracker = requireNotNull(loopStatisticFactory())
    return IterativeDeepeningPs(underlyingPathSelector, loopTracker, options.loopIterationLimit)
}

internal fun <Method, Statement, State> createLoopLimiterPathSelector(
    underlyingPathSelector: UPathSelector<State>,
    options: UMachineOptions,
    loopStatisticFactory: () -> StateLoopTracker<*, Statement, State>?,
): UPathSelector<State> where State : UState<*, Method, Statement, *, *, State> {
    val loopTracker = requireNotNull(loopStatisticFactory())
    return LoopLimiterPs(underlyingPathSelector, loopTracker, options.loopIterationLimit)
}

internal fun <Method, Statement, Target, State> createTargetedPathSelector(
    cfgStatistics: CfgStatistics<Method, Statement>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    random: Random? = null,
): UPathSelector<State>
    where Target : UTarget<Statement, Target>,
          State : UState<*, Method, Statement, *, Target, State> {

    val distanceCalculator = MultiTargetDistanceCalculator<Method, Statement, _> { loc ->
        CallStackDistanceCalculator(
            targets = listOf(loc),
            cfgStatistics = cfgStatistics,
            applicationGraph = applicationGraph
        )
    }

    fun calculateDistanceToTargets(state: State) =
        state.targets.minOfOrNull { target ->
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
        } ?: UInt.MAX_VALUE

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = ::calculateDistanceToTargets
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(calculateDistanceToTargets(it).toDouble(), 1.0) }
    )
}

/**
 * Converts [InterprocDistance] to integer weight with the following properties:
 * - All distances with [ReachabilityKind.LOCAL] have smaller weight than the others.
 * - For greater distances one-step distance is less significant (logarithmic scale).
 * - All distances lie in [[0; 64]] interval.
 * - Only infinite distances map to weight equal to 64.
 */
private fun InterprocDistance.logWeight(): UInt {
    if (isInfinite) {
        return 64u
    }
    var weight = log2(distance) // weight is in [0; 32)
    assert(weight < 32u)
    if (reachabilityKind != ReachabilityKind.LOCAL) {
        weight += 32u // non-local's weight is in [32, 64)
    }
    return weight
}

internal fun <Method, Statement, Target, State> createTargetedPathSelector(
    cfgStatistics: CfgStatistics<Method, Statement>,
    callGraphStatistics: CallGraphStatistics<Method>,
    applicationGraph: ApplicationGraph<Method, Statement>,
    random: Random? = null,
): UPathSelector<State>
    where Target : UTarget<Statement, Target>,
          State : UState<*, Method, Statement, *, Target, State> {

    val distanceCalculator = MultiTargetDistanceCalculator<Method, Statement, _> { stmt ->
        InterprocDistanceCalculator(
            targetLocation = stmt,
            applicationGraph = applicationGraph,
            cfgStatistics = cfgStatistics,
            callGraphStatistics = callGraphStatistics
        )
    }

    fun calculateWeight(state: State) =
        state.targets.minOfOrNull { target ->
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
        } ?: UInt.MAX_VALUE

    if (random == null) {
        return WeightedPathSelector(
            priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder()) },
            weighter = ::calculateWeight
        )
    }

    return WeightedPathSelector(
        priorityCollectionFactory = { RandomizedPriorityCollection(compareById()) { random.nextDouble() } },
        weighter = { 1.0 / max(calculateWeight(it).toDouble(), 1.0) }
    )
}
