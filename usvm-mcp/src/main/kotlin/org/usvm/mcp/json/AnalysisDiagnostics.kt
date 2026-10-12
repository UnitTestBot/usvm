package org.usvm.mcp.json

import org.usvm.machine.TsAnalysisStopReason
import org.usvm.mcp.exec.AnalysisOutcome

fun AnalysisOutcome<*>.toDiagnosticsDto(
    resolutionErrors: List<String> = emptyList(),
): AnalysisDiagnosticsDto = AnalysisDiagnosticsDto(
    stopReason = stopReason.name,
    unsupportedPaths = unsupportedPaths,
    executionErrors = executionErrors,
    resolutionErrors = resolutionErrors,
)

val AnalysisDiagnosticsDto.incomplete: Boolean
    get() = stopReason != TsAnalysisStopReason.EXHAUSTED.name ||
        unsupportedPaths.isNotEmpty() || executionErrors.isNotEmpty() || resolutionErrors.isNotEmpty()
