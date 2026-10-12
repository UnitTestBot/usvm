package org.usvm.mcp.json

import org.usvm.machine.TsAnalysisStopReason
import org.usvm.mcp.exec.AnalysisOutcome
import org.usvm.mcp.exec.ResolvedCase

fun AnalysisOutcome<*>.toDiagnosticsDto(
    resolutionErrors: List<String> = emptyList(),
): AnalysisDiagnosticsDto = AnalysisDiagnosticsDto(
    stopReason = stopReason.name,
    unsupportedPaths = unsupportedPaths,
    resolutionErrors = resolutionErrors,
)

val AnalysisDiagnosticsDto.incomplete: Boolean
    get() = stopReason != TsAnalysisStopReason.EXHAUSTED.name ||
        unsupportedPaths.isNotEmpty() || resolutionErrors.isNotEmpty()

fun ResolvedCase.resolutionErrorOrNull(): String? =
    if (test == null) error ?: "unknown resolution error" else null
