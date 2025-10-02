package org.usvm.api.reachability.dto

import kotlinx.serialization.Serializable

/**
 * Individual analysis result for a reachability target.
 */
@Serializable
data class AnalysisResultDto(
    val targetId: String,
    val reachable: Boolean,
    val executionTime: Long = 0L, // in milliseconds
    val errorMessage: String? = null,
)

/**
 * Summary statistics for the analysis run.
 */
@Serializable
data class AnalysisSummaryDto(
    val totalTargets: Int,
    val reachableTargets: Int,
    val unreachableTargets: Int,
    val unknownTargets: Int,
)

/**
 * Complete analysis report containing all results and metadata.
 */
@Serializable
data class AnalysisReportDto(
    val projectPath: String,
    val solverType: String,
    val totalTime: Long, // in milliseconds
    val results: List<AnalysisResultDto>,
    val summary: AnalysisSummaryDto,
)
