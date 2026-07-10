package org.usvm.ts.pbt.hybrid

import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.usvm.machine.TsInputTypeHints
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.report.ConfigEcho
import org.usvm.ts.pbt.report.FailureReport
import org.usvm.ts.pbt.report.HybridReport
import org.usvm.ts.pbt.report.MethodReport
import org.usvm.ts.pbt.report.PbtReport
import org.usvm.ts.pbt.report.SymbolicReport
import org.usvm.ts.pbt.report.TargetReport
import org.usvm.ts.pbt.report.TimelinePoint
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

enum class AnalysisMode {
    /** Only random testing at the IR level. */
    PBT_ONLY,

    /** Only targeted symbolic execution (every branch is a target). */
    SYMBOLIC_ONLY,

    /** PBT first, then targeted symbolic execution on the leftovers. */
    HYBRID,

    /** [HYBRID] + observed-type hints from the PBT phase feed the symbolic phase. */
    HYBRID_WITH_HINTS,
}

data class HybridConfig(
    val mode: AnalysisMode = AnalysisMode.HYBRID_WITH_HINTS,
    val seed: Long = 0L,
    val pbtMaxIterations: Int = 2_000,
    val pbtTimeBudget: Duration = 30.seconds,
    val perTargetTimeout: Duration = 20.seconds,
    val hintFallback: Boolean = true,
    val shrink: Boolean = true,
    val interproceduralAnalysis: Boolean = true,
)

/**
 * The orchestrator of the hybrid analysis pipeline:
 * `PBT (concrete, coverage + type profiles) -> targeted symbolic execution -> replay`.
 */
class HybridAnalyzer(
    private val scene: EtsScene,
    private val config: HybridConfig = HybridConfig(),
) {
    fun analyze(methods: List<EtsMethod>): HybridReport {
        val reports = methods.map { analyzeMethod(it) }
        return HybridReport(
            config = ConfigEcho(
                mode = config.mode.name,
                seed = config.seed,
                pbtMaxIterations = config.pbtMaxIterations,
                pbtTimeBudgetMs = config.pbtTimeBudget.inWholeMilliseconds,
                perTargetTimeoutMs = config.perTargetTimeout.inWholeMilliseconds,
                hintFallback = config.hintFallback,
            ),
            methods = reports,
        )
    }

    fun analyzeMethod(method: EtsMethod): MethodReport {
        logger.info { "analyzing ${method.signature} in mode ${config.mode}" }
        val start = System.nanoTime()
        val coverage = CoverageTracker(listOf(method))

        var pbtReport: PbtReport? = null
        var hints = TsInputTypeHints.EMPTY

        if (config.mode != AnalysisMode.SYMBOLIC_ONLY) {
            val pbtStart = System.nanoTime()
            val pbt = PbtPhase(
                scene = scene,
                method = method,
                coverage = coverage,
                seed = config.seed,
                maxIterations = config.pbtMaxIterations,
                timeBudget = config.pbtTimeBudget,
                shrink = config.shrink,
            ).run()

            pbtReport = PbtReport(
                executions = pbt.stats.executions,
                returned = pbt.stats.returned,
                threw = pbt.stats.threw,
                diverged = pbt.stats.diverged,
                unsupported = pbt.stats.unsupported,
                wallMs = (System.nanoTime() - pbtStart) / 1_000_000,
                unsupportedReasons = pbt.stats.unsupportedReasons,
                failures = pbt.failures.map { failure ->
                    FailureReport(
                        description = failure.description,
                        args = failure.args.map { it.toString() },
                        shrunkArgs = failure.shrunkArgs.map { it.toString() },
                    )
                },
            )

            if (config.mode == AnalysisMode.HYBRID_WITH_HINTS) {
                hints = pbt.typeProfiler.toHints()
            }
        }

        var symbolicReport: SymbolicReport? = null

        if (config.mode != AnalysisMode.PBT_ONLY) {
            val symStart = System.nanoTime()
            val symbolic = SymbolicPhase(
                scene = scene,
                method = method,
                coverage = coverage,
                hints = hints,
                hintFallback = config.hintFallback,
                perTargetTimeout = config.perTargetTimeout,
                interproceduralAnalysis = config.interproceduralAnalysis,
            ).run()

            symbolicReport = SymbolicReport(
                targets = symbolic.outcomes.map { outcome ->
                    TargetReport(
                        branch = "${outcome.branch.ifStmt} -> ${outcome.branch.successor}",
                        reached = outcome.reached,
                        wallMs = outcome.wallMs,
                        steps = outcome.steps.toLong(),
                        hintsUsed = outcome.hintsUsed,
                        fallbackUsed = outcome.fallbackUsed,
                        replayConfirmed = outcome.replayConfirmed,
                        inputs = outcome.inputs?.parameters?.map { it.toString() },
                    )
                },
                reached = symbolic.reachedCount,
                wallMs = (System.nanoTime() - symStart) / 1_000_000,
            )
        }

        val typeProfile: Map<Int, List<String>> = when {
            hints != TsInputTypeHints.EMPTY ->
                hints.byMethod[TsInputTypeHints.keyOf(method)]
                    ?.mapValues { (_, tags) -> tags.map { it.name }.sorted() }
                    .orEmpty()

            else -> emptyMap()
        }

        return MethodReport(
            method = method.signature.toString(),
            totalStmts = coverage.allStmts.size,
            totalBranches = coverage.allBranches.size,
            coveredStmts = coverage.coveredStmtCount,
            coveredBranches = coverage.coveredBranchCount,
            stmtCoverage = coverage.stmtCoverage(),
            branchCoverage = coverage.branchCoverage(),
            timeline = coverage.timeline.map {
                TimelinePoint(it.elapsedMs, it.phase, it.coveredStmts, it.coveredBranches)
            },
            pbt = pbtReport,
            symbolic = symbolicReport,
            typeProfile = typeProfile,
            totalWallMs = (System.nanoTime() - start) / 1_000_000,
        )
    }
}
