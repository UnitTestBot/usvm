package org.usvm.ts.pbt.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Machine-readable report of one hybrid analysis run — the raw material for
 * the experimental section (coverage timelines, per-target attribution,
 * hint ablation data, honesty counters).
 */
@Serializable
data class HybridReport(
    val config: ConfigEcho,
    val methods: List<MethodReport>,
) {
    companion object {
        private val json = Json { prettyPrint = true }
        fun encode(report: HybridReport): String = json.encodeToString(report)
        fun decode(text: String): HybridReport = json.decodeFromString(text)
    }
}

@Serializable
data class ConfigEcho(
    val mode: String,
    val seed: Long,
    val pbtMaxIterations: Int,
    val pbtTimeBudgetMs: Long,
    val perTargetTimeoutMs: Long,
    val hintFallback: Boolean,
)

@Serializable
data class MethodReport(
    val method: String,
    val totalStmts: Int,
    val totalBranches: Int,
    val coveredStmts: Int,
    val coveredBranches: Int,
    val stmtCoverage: Double,
    val branchCoverage: Double,
    val timeline: List<TimelinePoint>,
    val pbt: PbtReport?,
    val symbolic: SymbolicReport?,
    val typeProfile: Map<Int, List<String>>,
    val totalWallMs: Long,
)

@Serializable
data class TimelinePoint(
    val elapsedMs: Long,
    val phase: String,
    val coveredStmts: Int,
    val coveredBranches: Int,
)

@Serializable
data class PbtReport(
    val executions: Int,
    val returned: Int,
    val threw: Int,
    val diverged: Int,
    val unsupported: Int,
    val wallMs: Long,
    val failures: List<FailureReport>,
    val unsupportedReasons: Map<String, Int> = emptyMap(),
)

@Serializable
data class FailureReport(
    val description: String,
    val args: List<String>,
    val shrunkArgs: List<String>,
)

@Serializable
data class SymbolicReport(
    val targets: List<TargetReport>,
    val reached: Int,
    val wallMs: Long,
)

@Serializable
data class TargetReport(
    val branch: String,
    val reached: Boolean,
    val wallMs: Long,
    val steps: Long,
    val hintsUsed: Boolean,
    val fallbackUsed: Boolean,
    val replayConfirmed: Boolean,
    val inputs: List<String>?,
)
