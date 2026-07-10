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
 * Construction of reachability target chains for uncovered branches.
 *
 * For an uncovered edge `(I, S)` the chain is
 * `InitialPoint(entry) -> IntermediatePoint(I) -> FinalPoint(S)`;
 * `ReachabilityObserver` advances a state through the chain as the
 * corresponding statements are executed.
 */
object TargetBuilder {
    fun forBranch(method: EtsMethod, branch: CoverageTracker.BranchEdge): TsTarget {
        val root: TsTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        root.addChild(TsReachabilityTarget.IntermediatePoint(branch.ifStmt))
            .addChild(TsReachabilityTarget.FinalPoint(branch.successor))
        return root
    }
}

/**
 * Captures the first state whose target set contains a reached terminal target.
 *
 * Note: the machine's own `TargetsReachedStatesCollector` only inspects
 * *terminated* states, and `stopOnTargetsReached` halts the machine as soon as
 * the target tree is fully removed — usually *before* the reaching state gets a
 * chance to terminate. Capturing at propagation time avoids the race. This
 * observer must be placed *after* [ReachabilityObserver] in the composite.
 */
private class ReachingStateCaptor : UMachineObserver<TsState> {
    var captured: TsState? = null
        private set

    private fun check(state: TsState) {
        if (captured == null && state.targets.reachedTerminal.isNotEmpty()) {
            captured = state
        }
    }

    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        check(parent)
        forks.forEach { check(it) }
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        if (stateReachable) check(state)
    }
}

data class TargetOutcome(
    val branch: CoverageTracker.BranchEdge,
    val reached: Boolean,
    val wallMs: Long,
    val steps: ULong,
    val hintsUsed: Boolean,
    val fallbackUsed: Boolean,
    /** Input valuation extracted from the reaching state, when resolvable. */
    val inputs: TsParametersState?,
    val replayConfirmed: Boolean,
)

class SymbolicPhaseResult(
    val outcomes: List<TargetOutcome>,
) {
    val reachedCount: Int get() = outcomes.count { it.reached }
}

/**
 * Phase 2 of the hybrid analysis: for every branch the PBT phase failed to cover,
 * run the usvm-ts machine in targeted mode, extract concrete inputs from the
 * reaching state, and replay them on the concrete interpreter to confirm
 * (and merge) the coverage.
 *
 * One machine run per target: `TargetsReachedStopStrategy` only stops when *all*
 * targets are reached, so batching would let a single infeasible branch consume
 * the whole time budget.
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
        val outcomes = mutableListOf<TargetOutcome>()

        for (uncovered in coverage.uncoveredBranches()) {
            if (coverage.isCovered(uncovered.edge)) continue // covered by an earlier target's replay

            val useHints = hints != TsInputTypeHints.EMPTY
            var outcome = attemptTarget(uncovered, if (useHints) hints else TsInputTypeHints.EMPTY)

            if (!outcome.reached && useHints && hintFallback) {
                logger.info { "target not reached with hints, falling back: ${uncovered.edge}" }
                val fallback = attemptTarget(uncovered, TsInputTypeHints.EMPTY)
                outcome = fallback.copy(
                    wallMs = outcome.wallMs + fallback.wallMs,
                    fallbackUsed = true,
                )
            }
            outcomes += outcome
        }
        return SymbolicPhaseResult(outcomes)
    }

    private fun attemptTarget(
        uncovered: CoverageTracker.UncoveredBranch,
        hints: TsInputTypeHints,
    ): TargetOutcome {
        val start = System.nanoTime()
        val stepsStats = StepsStatistics<EtsMethod, TsState>()
        val captor = ReachingStateCaptor()

        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.TARGETED),
            stopOnTargetsReached = true,
            exceptionsPropagation = true,
            timeout = perTargetTimeout,
            solverType = SolverType.YICES,
        )
        val tsOptions = TsOptions(
            interproceduralAnalysis = interproceduralAnalysis,
            inputTypeHints = hints,
        )

        val target = TargetBuilder.forBranch(uncovered.method, uncovered.edge)

        try {
            TsMachine(
                scene,
                options,
                tsOptions,
                // Order matters: the captor must observe *after* target propagation
                machineObserver = CompositeUMachineObserver(ReachabilityObserver(), captor, stepsStats),
            ).use { machine ->
                machine.analyze(listOf(method), listOf(target))
            }
        } catch (e: Throwable) {
            // e.g. UNSAT initial constraints under too-restrictive hints
            logger.warn { "symbolic run failed for ${uncovered.edge}: $e" }
        }

        val wallMs = (System.nanoTime() - start) / 1_000_000
        val hintsUsed = hints != TsInputTypeHints.EMPTY
        val state = captor.captured
            ?: return TargetOutcome(
                branch = uncovered.edge,
                reached = false,
                wallMs = wallMs,
                steps = stepsStats.totalSteps,
                hintsUsed = hintsUsed,
                fallbackUsed = false,
                inputs = null,
                replayConfirmed = false,
            )

        val inputs = try {
            TsTestResolver().resolveInputs(method, state)
        } catch (e: Throwable) {
            logger.warn { "input resolution failed for ${uncovered.edge}: $e" }
            null
        }

        val replayConfirmed = inputs?.let { replay(it, uncovered.edge) } ?: false
        if (!replayConfirmed) {
            // Fall back to the symbolic trace for coverage accounting
            mergeSymbolicTrace(state)
        }

        return TargetOutcome(
            branch = uncovered.edge,
            reached = true,
            wallMs = wallMs,
            steps = stepsStats.totalSteps,
            hintsUsed = hintsUsed,
            fallbackUsed = false,
            inputs = inputs,
            replayConfirmed = replayConfirmed,
        )
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
        if (result is ExecutionResult.Unsupported) {
            logger.debug { "replay unsupported for $edge: ${result.reason}" }
            return false
        }
        if (!edgeTaken) {
            logger.info {
                "replay diverged for $edge: result=$result, this=$thisValue, args=$args"
            }
        }
        return edgeTaken
    }

    private fun mergeSymbolicTrace(state: TsState) {
        val trace: List<EtsStmt> = state.pathNode.allStatements.toList().asReversed()
        coverage.mergeTrace(trace)
    }
}
