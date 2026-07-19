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
import org.usvm.ts.pbt.interpreter.EtsConcreteInterpreter
import org.usvm.ts.pbt.interpreter.ExecutionListener
import org.usvm.ts.pbt.interpreter.ExecutionResult
import org.usvm.ts.pbt.interpreter.VUndefined
import org.usvm.ts.pbt.report.toVValueOrNull
import org.usvm.util.TsTestResolver
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Resolves inputs for every state that reaches one or more terminal targets.
 * The observer runs after [ReachabilityObserver], while [StepsStatistics] runs
 * before it, so each capture contains the exact elapsed effort at discovery.
 */
private class ReachingStateCaptor(
    private val method: EtsMethod,
    private val branchesByTerminal: Map<TsTarget, CoverageTracker.UncoveredBranch>,
    private val stepsStatistics: StepsStatistics<EtsMethod, TsState>,
    private val startNanos: Long,
) : UMachineObserver<TsState> {
    data class Capture(
        val inputs: TsParametersState?,
        val wallMs: Long,
        val steps: ULong,
    )

    val captures = mutableMapOf<CoverageTracker.UncoveredBranch, Capture>()

    private fun check(state: TsState) {
        val newlyReached = state.targets.reachedTerminal
            .mapNotNull { branchesByTerminal[it] }
            .filterNot { it in captures }
        if (newlyReached.isEmpty()) return

        // The machine continues mutating live states while looking for other
        // targets, so keep the resolved input instead of retaining the state.
        val inputs = try {
            TsTestResolver().resolveInputs(method, state)
        } catch (e: Throwable) {
            logger.warn {
                "input resolution failed for " +
                    "${newlyReached.joinToString { it.edge.toString() }}: $e"
            }
            null
        }
        val capture = Capture(
            inputs = inputs,
            wallMs = (System.nanoTime() - startNanos) / 1_000_000,
            steps = stepsStatistics.totalSteps,
        )
        newlyReached.forEach { captures[it] = capture }
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        check(parent)
        forks.forEach(::check)
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        if (stateReachable) check(state)
    }
}

data class TargetOutcome(
    val branch: CoverageTracker.BranchEdge,
    val reached: Boolean,
    /** Elapsed batch time when this target was reached, or total batch time otherwise. */
    val wallMs: Long,
    /** Batch step count when this target was reached, or total batch steps otherwise. */
    val steps: ULong,
    val hintsUsed: Boolean,
    val fallbackUsed: Boolean,
    /** Input valuation extracted from the reaching state, when resolvable. */
    val inputs: TsParametersState?,
    val replayConfirmed: Boolean,
)

class SymbolicPhaseResult(
    val outcomes: List<TargetOutcome>,
    /** Real aggregate duration; unlike per-target discovery times, counted once per batch. */
    val wallMs: Long,
    /** Real aggregate steps; unlike per-target discovery steps, counted once per batch. */
    val steps: ULong,
    val machineRuns: Int,
) {
    val reachedCount: Int get() = outcomes.count { it.reached }
}

private data class BatchAttempt(
    val outcomes: List<TargetOutcome>,
    val wallMs: Long,
    val steps: ULong,
)

/**
 * Phase 2 of the hybrid analysis. All branches left by PBT are passed to one
 * targeted `TsMachine` run. If observed-type hints make some targets
 * unreachable, only that residual subset is retried in one hint-free run.
 * Solver selection deliberately remains Yices.
 */
class SymbolicPhase(
    private val scene: EtsScene,
    private val method: EtsMethod,
    private val coverage: CoverageTracker,
    private val hints: TsInputTypeHints = TsInputTypeHints.EMPTY,
    private val hintFallback: Boolean = true,
    private val perTargetTimeout: Duration = 20.seconds,
    private val interproceduralAnalysis: Boolean = true,
) {
    fun run(): SymbolicPhaseResult {
        coverage.phase = "symbolic"
        val branches = coverage.uncoveredBranches()
        if (branches.isEmpty()) {
            return SymbolicPhaseResult(emptyList(), wallMs = 0, steps = 0UL, machineRuns = 0)
        }

        val useHints = hints != TsInputTypeHints.EMPTY
        val primary = attemptTargets(branches, if (useHints) hints else TsInputTypeHints.EMPTY)

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
        val fallback = if (fallbackBranches.isEmpty()) null else {
            attemptTargets(fallbackBranches, TsInputTypeHints.EMPTY)
        }

        val fallbackByBranch = fallback?.outcomes.orEmpty().associateBy(TargetOutcome::branch)
        val outcomes = primary.outcomes.map { initial ->
            fallbackByBranch[initial.branch]?.let { retry ->
                retry.copy(
                    wallMs = initial.wallMs + retry.wallMs,
                    steps = initial.steps + retry.steps,
                    fallbackUsed = true,
                )
            } ?: initial
        }

        return SymbolicPhaseResult(
            outcomes = outcomes,
            wallMs = primary.wallMs + (fallback?.wallMs ?: 0),
            steps = primary.steps + (fallback?.steps ?: 0UL),
            machineRuns = 1 + if (fallback == null) 0 else 1,
        )
    }

    private fun attemptTargets(
        branches: List<CoverageTracker.UncoveredBranch>,
        runHints: TsInputTypeHints,
    ): BatchAttempt {
        val start = System.nanoTime()
        val stepsStatistics = StepsStatistics<EtsMethod, TsState>()
        val targetRoots = mutableListOf<TsTarget>()
        val branchesByTerminal = mutableMapOf<TsTarget, CoverageTracker.UncoveredBranch>()
        branches.forEach { uncovered ->
            require(uncovered.method == method) { "All targets must belong to the analyzed method" }
            val root: TsTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
            val terminal: TsTarget = TsReachabilityTarget.FinalPoint(uncovered.edge.successor)
            root.addChild(TsReachabilityTarget.IntermediatePoint(uncovered.edge.ifStmt))
                .addChild(terminal)
            targetRoots += root
            branchesByTerminal[terminal] = uncovered
        }
        val captor = ReachingStateCaptor(
            method = method,
            branchesByTerminal = branchesByTerminal,
            stepsStatistics = stepsStatistics,
            startNanos = start,
        )

        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            stopOnTargetsReached = true,
            exceptionsPropagation = true,
            // Preserve the old maximum budget of N independent target runs.
            timeout = perTargetTimeout * branches.size.toDouble(),
            solverType = SolverType.YICES,
        )
        val tsOptions = TsOptions(
            interproceduralAnalysis = interproceduralAnalysis,
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
                machine.analyze(listOf(method), targetRoots)
            }
        } catch (e: Throwable) {
            logger.warn {
                "symbolic batch failed for ${branches.size} targets " +
                    "(${branches.joinToString(limit = 3) { it.edge.toString() }}): $e"
            }
        }

        val wallMs = (System.nanoTime() - start) / 1_000_000
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
                TargetOutcome(
                    branch = uncovered.edge,
                    reached = true,
                    wallMs = capture.wallMs,
                    steps = capture.steps,
                    hintsUsed = hintsUsed,
                    fallbackUsed = false,
                    inputs = capture.inputs,
                    replayConfirmed = capture.inputs?.let { replay(it, uncovered.edge) } ?: false,
                )
            }
        }
        return BatchAttempt(outcomes, wallMs, stepsStatistics.totalSteps)
    }

    /** Replay extracted inputs concretely; returns true if the target edge was actually taken. */
    private fun replay(inputs: TsParametersState, edge: CoverageTracker.BranchEdge): Boolean {
        val classResolver = { name: String ->
            scene.projectAndSdkClasses.firstOrNull { it.name == name }
        }
        val thisValue = inputs.thisInstance?.toVValueOrNull(classResolver) ?: VUndefined
        val args = inputs.parameters.mapIndexed { i, value ->
            value.toVValueOrNull(classResolver) ?: run {
                logger.debug { "replay skipped: parameter $i is not representable: $value" }
                return false
            }
        }

        var edgeTaken = false
        val probe = object : ExecutionListener {
            override fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) {
                if (ifStmt == edge.ifStmt && taken == edge.successor) edgeTaken = true
            }
        }

        val interpreter = EtsConcreteInterpreter(scene)
        val result = interpreter.execute(
            method,
            thisValue,
            args,
            ExecutionListener.composite(listOf(coverage, probe)),
        )
        if (edgeTaken) return true
        if (result is ExecutionResult.Unsupported) {
            logger.debug { "replay unsupported for $edge: ${result.reason}" }
            return false
        }
        logger.info {
            "replay diverged for $edge: result=$result, this=$thisValue, args=$args"
        }
        return false
    }
}
