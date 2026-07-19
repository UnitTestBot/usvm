package org.usvm.ts.pbt.hybrid

import mu.KotlinLogging
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsParametersState
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.api.targets.TsTarget
import org.usvm.machine.TsInputTypeHints
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.external.stableBranchId
import org.usvm.ts.pbt.interpreter.EtsConcreteInterpreter
import org.usvm.ts.pbt.interpreter.ExecutionListener
import org.usvm.ts.pbt.interpreter.ExecutionResult
import org.usvm.ts.pbt.interpreter.VUndefined
import org.usvm.ts.pbt.report.toVValueOrNull
import org.usvm.util.TsTestResolver
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
private const val NANOS_PER_MILLISECOND: Long = 1_000_000

/** Opt-in orchestration flags. The all/false/false combination preserves the legacy batch. */
data class SymbolicOrchestrationConfig(
    val targetShardSize: TargetShardSize = TargetShardSize.ALL,
    val replayPruneBetweenShards: Boolean = false,
    val symbolicProgressStop: Boolean = false,
    val progressTimeout: Duration? = null,
    val tsTargetReachabilityPruning: Boolean = false,
) {
    init {
        require(progressTimeout == null || progressTimeout > ZERO && progressTimeout.isFinite()) {
            "progressTimeout must be positive and finite, got $progressTimeout"
        }
    }

    internal val usesShardedExecution: Boolean
        get() = targetShardSize != TargetShardSize.ALL || replayPruneBetweenShards
}

enum class SymbolicAttemptKind(val idSuffix: String) {
    PRIMARY("primary"),
    FALLBACK("fallback"),
}

/** Counters shaped for a future TelemetryRecorder adapter, without a report-schema dependency. */
data class TargetShardRunMetrics(
    val shardId: String,
    val attempt: SymbolicAttemptKind,
    val plannedTargets: Int,
    val activeRoots: Int,
    val replayPrunedTargets: Int,
    val machineStarted: Boolean,
    val states: Long,
    val steps: ULong,
    val solverQueries: Long? = null,
    val witnesses: Int,
    val replayExecutions: Int,
    val replayConfirmedEdges: Int,
    val wallMs: Long,
) {
    init {
        require(shardId.isNotBlank()) { "shardId must not be blank" }
        require(plannedTargets > 0) { "plannedTargets must be positive" }
        require(activeRoots in 0..plannedTargets) { "activeRoots is outside planned target count" }
        require(replayPrunedTargets == plannedTargets - activeRoots) {
            "planned targets must split into active and replay-pruned targets"
        }
        require(states >= 0 && witnesses >= 0 && replayExecutions >= 0 && replayConfirmedEdges >= 0)
        require(wallMs >= 0) { "shard wall time must be non-negative" }
    }
}

data class SymbolicShardStatistics(
    val targetShardSize: String,
    val replayPruneBetweenShards: Boolean,
    val runs: List<TargetShardRunMetrics>,
) {
    val plannedShards: Int get() = runs.size
    val machineRuns: Int get() = runs.count(TargetShardRunMetrics::machineStarted)
    val skippedShards: Int get() = runs.count { it.activeRoots == 0 }
    val replayPrunedTargets: Int get() = runs.sumOf(TargetShardRunMetrics::replayPrunedTargets)
    val witnesses: Int get() = runs.sumOf(TargetShardRunMetrics::witnesses)
    val replayExecutions: Int get() = runs.sumOf(TargetShardRunMetrics::replayExecutions)
    val replayConfirmedEdges: Int get() = runs.sumOf(TargetShardRunMetrics::replayConfirmedEdges)
}

private data class ReplayObservation(
    val executed: Boolean,
    val coveredEdges: Set<CoverageTracker.BranchEdge>,
    val newlyCoveredEdges: Set<CoverageTracker.BranchEdge>,
    val result: ExecutionResult?,
) {
    companion object {
        val NOT_EXECUTED = ReplayObservation(false, emptySet(), emptySet(), null)
    }
}

private typealias ReplayWitness = (
    TsParametersState,
    List<CoverageTracker.UncoveredBranch>,
) -> ReplayObservation

/**
 * Resolves inputs for every state that reaches one or more terminal targets. When [replayWitness]
 * is supplied, concrete EtsIR replay runs synchronously before symbolic exploration can continue.
 */
@Suppress("TooGenericExceptionCaught")
private class ReachingStateCaptor(
    private val method: EtsMethod,
    private val branchesByTerminal: Map<TsTarget, CoverageTracker.UncoveredBranch>,
    private val stepsStatistics: StepsStatistics<EtsMethod, TsState>,
    private val startNanos: Long,
    private val replayWitness: ReplayWitness? = null,
) : UMachineObserver<TsState> {
    data class Capture(
        val inputs: TsParametersState?,
        val wallMs: Long,
        val steps: ULong,
        val replayConfirmed: Boolean?,
    )

    val captures = mutableMapOf<CoverageTracker.UncoveredBranch, Capture>()
    val newlyCoveredReplayEdges = mutableSetOf<CoverageTracker.BranchEdge>()
    private val observedStates: MutableSet<TsState> = Collections.newSetFromMap(IdentityHashMap())
    var machineStarted: Boolean = false
        private set
    val statesObserved: Long get() = observedStates.size.toLong()
    var witnesses: Int = 0
        private set
    var replayExecutions: Int = 0
        private set

    override fun onMachineStarted() {
        machineStarted = true
    }

    private fun check(state: TsState) {
        val newlyReached = state.targets.reachedTerminal
            .mapNotNull { branchesByTerminal[it] }
            .filterNot { it in captures }
        if (newlyReached.isEmpty()) return
        witnesses++

        // The machine continues mutating live states while looking for other targets, so retain
        // only the resolved input. A single state can witness several terminal targets.
        val inputs = try {
            TsTestResolver().resolveInputs(method, state)
        } catch (e: Throwable) {
            logger.warn {
                "input resolution failed for " +
                    "${newlyReached.joinToString { it.edge.toString() }}: $e"
            }
            null
        }
        val replay = inputs?.let { resolved ->
            try {
                replayWitness?.invoke(resolved, newlyReached)
            } catch (e: Throwable) {
                logger.warn { "immediate EtsIR replay failed for ${newlyReached.size} target(s): $e" }
                ReplayObservation.NOT_EXECUTED
            }
        }
        if (replay?.executed == true) replayExecutions++
        replay?.newlyCoveredEdges?.let(newlyCoveredReplayEdges::addAll)
        val wallMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLISECOND
        val steps = stepsStatistics.totalSteps
        newlyReached.forEach { branch ->
            captures[branch] = Capture(
                inputs = inputs,
                wallMs = wallMs,
                steps = steps,
                replayConfirmed = replay?.coveredEdges?.contains(branch.edge),
            )
        }
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        observedStates += parent
        check(parent)
        forks.forEach { fork ->
            observedStates += fork
            check(fork)
        }
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        observedStates += state
        if (stateReachable) check(state)
    }

    override fun onStatePeeked(state: TsState) {
        observedStates += state
    }
}

data class TargetOutcome(
    val branch: CoverageTracker.BranchEdge,
    val reached: Boolean,
    /** Elapsed attempt time when this target was reached, or total attempt time otherwise. */
    val wallMs: Long,
    /** Attempt step count when this target was reached, or total attempt steps otherwise. */
    val steps: ULong,
    val hintsUsed: Boolean,
    val fallbackUsed: Boolean,
    /** Input valuation extracted from the reaching state, when resolvable. */
    val inputs: TsParametersState?,
    /** Concrete EtsIR coverage is the sole source of truth for this edge. */
    val replayConfirmed: Boolean,
)

class SymbolicPhaseResult(
    val outcomes: List<TargetOutcome>,
    /** Real aggregate duration; unlike per-target discovery times, counted once per batch. */
    val wallMs: Long,
    /** Real aggregate steps; unlike per-target discovery steps, counted once per batch. */
    val steps: ULong,
    val machineRuns: Int,
    /** Null on the exact legacy monolithic path. */
    val shardStatistics: SymbolicShardStatistics? = null,
) {
    val reachedCount: Int get() = outcomes.count { it.reached }
}

private data class BatchAttempt(
    val outcomes: List<TargetOutcome>,
    val wallMs: Long,
    val steps: ULong,
    val machineRuns: Int,
    val startedBranches: Set<CoverageTracker.BranchEdge>,
    val shardRuns: List<TargetShardRunMetrics> = emptyList(),
)

private data class SingleMachineAttempt(
    val outcomes: List<TargetOutcome>,
    val wallMs: Long,
    val steps: ULong,
    val machineStarted: Boolean,
    val states: Long,
    val witnesses: Int,
    val replayExecutions: Int,
    val replayConfirmedEdges: Int,
)

internal data class IndependentTargetChain(
    val branch: CoverageTracker.UncoveredBranch,
    val root: TsTarget,
    val terminal: TsTarget,
)

/** Every edge owns a distinct entry -> if -> successor chain; no prefix node is shared. */
internal fun buildIndependentTargetChains(
    method: EtsMethod,
    branches: List<CoverageTracker.UncoveredBranch>,
): List<IndependentTargetChain> = branches.map { uncovered ->
    require(uncovered.method === method) { "all targets must belong to the analyzed method" }
    val root: TsTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
    val terminal: TsTarget = TsReachabilityTarget.FinalPoint(uncovered.edge.successor)
    root.addChild(TsReachabilityTarget.IntermediatePoint(uncovered.edge.ifStmt)).addChild(terminal)
    IndependentTargetChain(uncovered, root, terminal)
}.also { chains ->
    check(chains.map(IndependentTargetChain::root).distinct().size == chains.size)
}

/**
 * Phase 2 of the hybrid analysis. The exact legacy monolithic behavior remains the default. Opt-in
 * sharding replays each obtained witness immediately and can remove all incidentally covered edges
 * before the next primary or hint-fallback shard. Solver selection deliberately remains Yices.
 */
class SymbolicPhase(
    private val scene: EtsScene,
    private val method: EtsMethod,
    private val coverage: CoverageTracker,
    private val hints: TsInputTypeHints = TsInputTypeHints.EMPTY,
    private val hintFallback: Boolean = true,
    private val perTargetTimeout: Duration = 20.seconds,
    private val interproceduralAnalysis: Boolean = true,
    private val orchestration: SymbolicOrchestrationConfig = SymbolicOrchestrationConfig(),
) {
    init {
        if (orchestration.symbolicProgressStop) {
            val effectiveProgressTimeout = orchestration.progressTimeout ?: perTargetTimeout
            require(effectiveProgressTimeout > ZERO && effectiveProgressTimeout.isFinite()) {
                "effective progress timeout must be positive and finite, got $effectiveProgressTimeout"
            }
        }
    }

    fun run(): SymbolicPhaseResult {
        coverage.phase = "symbolic"
        val branches = coverage.uncoveredBranches()
        if (branches.isEmpty()) {
            return SymbolicPhaseResult(emptyList(), wallMs = 0, steps = 0UL, machineRuns = 0)
        }

        val useHints = hints != TsInputTypeHints.EMPTY
        val primary = runAttempt(
            branches = branches,
            runHints = if (useHints) hints else TsInputTypeHints.EMPTY,
            kind = SymbolicAttemptKind.PRIMARY,
        )

        val fallbackBranches = if (useHints && hintFallback) {
            val reached = primary.outcomes.asSequence()
                .filter(TargetOutcome::reached)
                .map(TargetOutcome::branch)
                .toHashSet()
            branches.filterNot { it.edge in reached }
        } else {
            emptyList()
        }
        if (fallbackBranches.isNotEmpty()) {
            logger.info {
                "${fallbackBranches.size} targets not reached with hints, retrying without hints"
            }
        }
        val fallback = fallbackBranches.takeIf(List<CoverageTracker.UncoveredBranch>::isNotEmpty)?.let {
            runAttempt(it, TsInputTypeHints.EMPTY, SymbolicAttemptKind.FALLBACK)
        }

        val fallbackByBranch = fallback?.outcomes.orEmpty().associateBy(TargetOutcome::branch)
        val fallbackStarted = fallback?.startedBranches.orEmpty()
        var outcomes = primary.outcomes.map { initial ->
            fallbackByBranch[initial.branch]
                ?.takeIf { initial.branch in fallbackStarted }
                ?.let { retry ->
                    retry.copy(
                        wallMs = initial.wallMs + retry.wallMs,
                        steps = initial.steps + retry.steps,
                        fallbackUsed = true,
                    )
                } ?: initial
        }
        if (orchestration.usesShardedExecution) {
            // A witness for one target can cover another target whose own solver chain was never
            // started. Preserve reached/model semantics while reflecting concrete coverage truth.
            outcomes = outcomes.map { outcome ->
                if (!outcome.replayConfirmed && coverage.isCovered(outcome.branch)) {
                    outcome.copy(replayConfirmed = true)
                } else {
                    outcome
                }
            }
        }

        val allShardRuns = primary.shardRuns + fallback?.shardRuns.orEmpty()
        val shardStatistics = if (orchestration.usesShardedExecution) {
            SymbolicShardStatistics(
                targetShardSize = orchestration.targetShardSize.flagValue,
                replayPruneBetweenShards = orchestration.replayPruneBetweenShards,
                runs = allShardRuns,
            )
        } else {
            null
        }
        val machineRuns = primary.machineRuns + (fallback?.machineRuns ?: 0)
        check(shardStatistics == null || shardStatistics.machineRuns == machineRuns)

        return SymbolicPhaseResult(
            outcomes = outcomes,
            wallMs = primary.wallMs + (fallback?.wallMs ?: 0),
            steps = primary.steps + (fallback?.steps ?: 0UL),
            machineRuns = machineRuns,
            shardStatistics = shardStatistics,
        )
    }

    private fun runAttempt(
        branches: List<CoverageTracker.UncoveredBranch>,
        runHints: TsInputTypeHints,
        kind: SymbolicAttemptKind,
    ): BatchAttempt = if (orchestration.usesShardedExecution) {
        runShardedAttempt(branches, runHints, kind)
    } else {
        runLegacyAttempt(branches, runHints)
    }

    private fun runLegacyAttempt(
        branches: List<CoverageTracker.UncoveredBranch>,
        runHints: TsInputTypeHints,
    ): BatchAttempt {
        val attempt = attemptTargets(
            branches = branches,
            runHints = runHints,
            immediateReplay = false,
        )
        return BatchAttempt(
            outcomes = attempt.outcomes,
            wallMs = attempt.wallMs,
            steps = attempt.steps,
            // Preserve the historical accounting on the comparison path.
            machineRuns = 1,
            startedBranches = branches.mapTo(hashSetOf()) { it.edge },
        )
    }

    private fun runShardedAttempt(
        branches: List<CoverageTracker.UncoveredBranch>,
        runHints: TsInputTypeHints,
        kind: SymbolicAttemptKind,
    ): BatchAttempt {
        val plannedShards = TargetShardPlanner(orchestration.targetShardSize).plan(branches)
        val executions = executeResidualShards(
            shards = plannedShards.map { ResidualShardPlan(it.id, it.targets) },
            replayPruneBetweenShards = orchestration.replayPruneBetweenShards,
            targetId = { target -> stableBranchId(method, target.edge.ifStmt, target.edge.successor) },
            // Deliberately re-read CoverageTracker at the coordinator boundary before every shard.
            isReplayCovered = { target -> coverage.isCovered(target.edge) },
        ) { _, activeTargets ->
            attemptTargets(
                branches = activeTargets,
                runHints = runHints,
                immediateReplay = true,
            )
        }

        var elapsedWallMs = 0L
        var elapsedSteps = 0UL
        var machineRuns = 0
        val outcomesByBranch = linkedMapOf<CoverageTracker.BranchEdge, TargetOutcome>()
        val startedBranches = hashSetOf<CoverageTracker.BranchEdge>()
        val shardRuns = mutableListOf<TargetShardRunMetrics>()
        val hintsUsed = runHints != TsInputTypeHints.EMPTY

        executions.forEach { execution ->
            execution.replayPrunedTargets.forEach { target ->
                outcomesByBranch[target.edge] = TargetOutcome(
                    branch = target.edge,
                    reached = false,
                    wallMs = elapsedWallMs,
                    steps = elapsedSteps,
                    hintsUsed = hintsUsed,
                    fallbackUsed = false,
                    inputs = null,
                    replayConfirmed = true,
                )
            }

            val launched = execution.launchResult
            if (launched != null) {
                execution.activeTargets.mapTo(startedBranches) { it.edge }
                launched.outcomes.forEach { outcome ->
                    outcomesByBranch[outcome.branch] = outcome.copy(
                        wallMs = elapsedWallMs + outcome.wallMs,
                        steps = elapsedSteps + outcome.steps,
                    )
                }
                if (launched.machineStarted) machineRuns++
            }

            val runId = "${execution.id.value}:${kind.idSuffix}"
            shardRuns += TargetShardRunMetrics(
                shardId = runId,
                attempt = kind,
                plannedTargets = execution.plannedTargets.size,
                activeRoots = execution.activeTargets.size,
                replayPrunedTargets = execution.replayPrunedTargets.size,
                machineStarted = launched?.machineStarted == true,
                states = launched?.states ?: 0,
                steps = launched?.steps ?: 0UL,
                witnesses = launched?.witnesses ?: 0,
                replayExecutions = launched?.replayExecutions ?: 0,
                replayConfirmedEdges = launched?.replayConfirmedEdges ?: 0,
                wallMs = launched?.wallMs ?: 0,
            )
            elapsedWallMs += launched?.wallMs ?: 0
            elapsedSteps += launched?.steps ?: 0UL
        }

        check(outcomesByBranch.size == branches.size) {
            "sharded symbolic attempt lost ${branches.size - outcomesByBranch.size} target outcomes"
        }
        return BatchAttempt(
            outcomes = branches.map { target -> outcomesByBranch.getValue(target.edge) },
            wallMs = elapsedWallMs,
            steps = elapsedSteps,
            machineRuns = machineRuns,
            startedBranches = startedBranches,
            shardRuns = shardRuns,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun attemptTargets(
        branches: List<CoverageTracker.UncoveredBranch>,
        runHints: TsInputTypeHints,
        immediateReplay: Boolean,
    ): SingleMachineAttempt {
        require(branches.isNotEmpty()) { "cannot launch an empty symbolic target batch" }
        val start = System.nanoTime()
        val stepsStatistics = StepsStatistics<EtsMethod, TsState>()
        val chains = buildIndependentTargetChains(method, branches)
        val branchesByTerminal = IdentityHashMap<TsTarget, CoverageTracker.UncoveredBranch>().also { byTerminal ->
            chains.forEach { chain -> byTerminal[chain.terminal] = chain.branch }
        }
        val captor = ReachingStateCaptor(
            method = method,
            branchesByTerminal = branchesByTerminal,
            stepsStatistics = stepsStatistics,
            startNanos = start,
            replayWitness = if (immediateReplay) {
                { inputs, reached -> replay(inputs, reached.map { it.edge }) }
            } else {
                null
            },
        )

        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            stopOnTargetsReached = true,
            exceptionsPropagation = true,
            // N * timeout remains only a global safety ceiling when progress-stop is enabled.
            timeout = perTargetTimeout * branches.size.toDouble(),
            solverType = SolverType.YICES,
        )
        val tsOptions = TsOptions(
            interproceduralAnalysis = interproceduralAnalysis,
            symbolicProgressStop = orchestration.symbolicProgressStop,
            progressTimeout = if (orchestration.symbolicProgressStop) {
                orchestration.progressTimeout ?: perTargetTimeout
            } else {
                null
            },
            tsTargetReachabilityPruning = orchestration.tsTargetReachabilityPruning,
            inputTypeHints = runHints,
        )

        try {
            TsMachine(
                scene,
                options,
                tsOptions,
                machineObserver = CompositeUMachineObserver(
                    ReachabilityObserver(),
                    stepsStatistics,
                    captor,
                ),
            ).use { machine ->
                machine.analyze(listOf(method), chains.map(IndependentTargetChain::root))
            }
        } catch (e: Throwable) {
            logger.warn {
                "symbolic batch failed for ${branches.size} targets " +
                    "(${branches.joinToString(limit = 3) { it.edge.toString() }}): $e"
            }
        }

        val wallMs = (System.nanoTime() - start) / NANOS_PER_MILLISECOND
        val hintsUsed = runHints != TsInputTypeHints.EMPTY
        val outcomes = branches.map { uncovered ->
            val capture = captor.captures[uncovered]
            if (capture == null) {
                TargetOutcome(
                    branch = uncovered.edge,
                    reached = false,
                    wallMs = wallMs,
                    steps = stepsStatistics.totalSteps,
                    hintsUsed = hintsUsed,
                    fallbackUsed = false,
                    inputs = null,
                    replayConfirmed = false,
                )
            } else {
                val replayConfirmed = capture.replayConfirmed ?: capture.inputs
                    ?.let { replay(it, listOf(uncovered.edge)).coveredEdges.contains(uncovered.edge) }
                    ?: false
                TargetOutcome(
                    branch = uncovered.edge,
                    reached = true,
                    wallMs = capture.wallMs,
                    steps = capture.steps,
                    hintsUsed = hintsUsed,
                    fallbackUsed = false,
                    inputs = capture.inputs,
                    replayConfirmed = replayConfirmed,
                )
            }
        }
        return SingleMachineAttempt(
            outcomes = outcomes,
            wallMs = wallMs,
            steps = stepsStatistics.totalSteps,
            machineStarted = captor.machineStarted,
            states = captor.statesObserved,
            witnesses = captor.witnesses,
            replayExecutions = captor.replayExecutions,
            replayConfirmedEdges = captor.newlyCoveredReplayEdges.size,
        )
    }

    /** Execute one concrete EtsIR replay and return every branch edge observed in that run. */
    private fun replay(
        inputs: TsParametersState,
        expectedEdges: List<CoverageTracker.BranchEdge>,
    ): ReplayObservation {
        val classResolver = { name: String ->
            scene.projectAndSdkClasses.firstOrNull { it.name == name }
        }
        val thisValue = inputs.thisInstance?.toVValueOrNull(classResolver) ?: VUndefined
        val args = inputs.parameters.mapIndexed { index, value ->
            value.toVValueOrNull(classResolver) ?: run {
                logger.debug { "replay skipped: parameter $index is not representable: $value" }
                return ReplayObservation.NOT_EXECUTED
            }
        }

        val previouslyCovered = coverage.allBranches.filterTo(hashSetOf(), coverage::isCovered)
        val observedEdges = mutableSetOf<CoverageTracker.BranchEdge>()
        val probe = object : ExecutionListener {
            override fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) {
                observedEdges += CoverageTracker.BranchEdge(ifStmt, taken)
            }
        }

        val result = EtsConcreteInterpreter(scene).execute(
            method,
            thisValue,
            args,
            ExecutionListener.composite(listOf(coverage, probe)),
        )
        val coveredInZone = observedEdges.filterTo(hashSetOf()) { it in coverage.allBranches }
        val newlyCovered = coveredInZone.filterTo(hashSetOf()) { it !in previouslyCovered }
        if (result is ExecutionResult.Unsupported) {
            logger.debug { "replay unsupported: ${result.reason}" }
        }
        expectedEdges.filterNot(coveredInZone::contains).forEach { edge ->
            logger.info {
                "replay diverged for $edge: result=$result, this=$thisValue, args=$args"
            }
        }
        return ReplayObservation(
            executed = true,
            coveredEdges = coveredInZone,
            newlyCoveredEdges = newlyCovered,
            result = result,
        )
    }
}
