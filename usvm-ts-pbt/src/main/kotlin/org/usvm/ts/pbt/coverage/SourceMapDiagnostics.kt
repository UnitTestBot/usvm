package org.usvm.ts.pbt.coverage

import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageDiagnostic

/** Raw V8 evidence replaces the less precise source-map guesses made from the final Istanbul report. */
internal fun mergeCoverageDiagnostics(
    finalDiagnostics: List<CoverageDiagnostic>,
    rawDiagnostics: List<CoverageDiagnostic>,
): List<CoverageDiagnostic> {
    val rawSourceMapPaths = rawDiagnostics
        .filter(CoverageDiagnostic::isSourceMapDiagnostic)
        .mapNotNullTo(hashSetOf(), CoverageDiagnostic::path)
    val retainedFinalDiagnostics = finalDiagnostics.filterNot { diagnostic ->
        diagnostic.isSourceMapDiagnostic() && diagnostic.path in rawSourceMapPaths
    }
    val combinedDiagnostics = retainedFinalDiagnostics + rawDiagnostics

    return coalesceSourceMapDiagnostics(combinedDiagnostics)
}

internal fun buildSourceMapDiagnostic(path: String, sourceMapExists: Boolean): CoverageDiagnostic {
    val diagnosticCode = if (sourceMapExists) {
        PbtDiagnosticCode.COVERAGE_SOURCE_MAP_INVALID
    } else {
        PbtDiagnosticCode.COVERAGE_SOURCE_MAP_MISSING
    }
    val diagnosticMessage = if (sourceMapExists) {
        "Executed JavaScript has a source map that c8 could not remap to its original source"
    } else {
        "Executed JavaScript below a TypeScript source root has no source map"
    }

    return CoverageDiagnostic(
        code = diagnosticCode,
        message = diagnosticMessage,
        path = path,
    )
}

internal fun coalesceSourceMapDiagnostics(diagnostics: List<CoverageDiagnostic>): List<CoverageDiagnostic> {
    val diagnosticsByScriptPath = hashMapOf<String, CoverageDiagnostic>()
    val unkeyedDiagnostics = mutableListOf<CoverageDiagnostic>()

    for (diagnostic in diagnostics) {
        val scriptPath = diagnostic.path
        if (!diagnostic.isSourceMapDiagnostic() || scriptPath == null) {
            unkeyedDiagnostics += diagnostic
            continue
        }

        val previous = diagnosticsByScriptPath[scriptPath]
        if (previous == null || diagnostic.isInvalidInsteadOfMissing(previous)) {
            diagnosticsByScriptPath[scriptPath] = diagnostic
        }
    }

    val combinedDiagnostics = unkeyedDiagnostics + diagnosticsByScriptPath.values

    return combinedDiagnostics
        .distinct()
        .sortedWith(COVERAGE_DIAGNOSTIC_ORDER)
}

private fun CoverageDiagnostic.isSourceMapDiagnostic(): Boolean =
    code == PbtDiagnosticCode.COVERAGE_SOURCE_MAP_MISSING ||
        code == PbtDiagnosticCode.COVERAGE_SOURCE_MAP_INVALID

private fun CoverageDiagnostic.isInvalidInsteadOfMissing(other: CoverageDiagnostic): Boolean =
    code == PbtDiagnosticCode.COVERAGE_SOURCE_MAP_INVALID &&
        other.code == PbtDiagnosticCode.COVERAGE_SOURCE_MAP_MISSING

private val COVERAGE_DIAGNOSTIC_ORDER = compareBy(CoverageDiagnostic::path, CoverageDiagnostic::code)
