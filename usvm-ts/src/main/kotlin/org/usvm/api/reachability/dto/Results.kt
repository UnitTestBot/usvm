package org.usvm.api.reachability.dto

import kotlinx.serialization.Serializable

/**
 * Individual analysis result.
 */
@Serializable
data class AnalysisResultDto(
    val targetId: String,
    val reachable: Boolean,
    val executionTime: Long = 0L, // in milliseconds
    val errorMessage: String? = null
)

/**
 * Container for all analysis results.
 */
@Serializable
data class AnalysisResultsDto(
    val results: List<AnalysisResultDto>,
    val summary: AnalysisSummaryDto
)

/**
 * Summary information about the analysis run.
 */
@Serializable
data class AnalysisSummaryDto(
    val totalTargets: Int,
    val reachableTargets: Int,
    val unreachableTargets: Int,
    val unknownTargets: Int,
    val totalExecutionTimeMs: Long,
)
