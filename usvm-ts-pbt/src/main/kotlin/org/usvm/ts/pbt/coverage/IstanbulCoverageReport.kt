package org.usvm.ts.pbt.coverage

import org.usvm.ts.pbt.backend.CoverageCollectorIdentity
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.model.PropertyId
import java.nio.file.Path

/** All identities and path boundaries required to convert one isolated c8 report. */
data class IstanbulCoverageContext(
    val backendId: String,
    val backendVersion: String,
    val propertyId: PropertyId,
    val sourceRoots: List<String>,
    val propertyEntryPointPaths: Set<String>,
    val adapterRoot: String,
    val runtimeVersion: String,
    val collector: CoverageCollectorIdentity,
    val request: PropertyCoverageRequest,
)

/** Invalid or absent coverage that prevents construction of a trustworthy artifact. */
class CoverageArtifactException(
    val diagnostic: CoverageDiagnostic,
    cause: Throwable? = null,
) : IllegalArgumentException(diagnostic.message, cause) {
    internal companion object {
        fun create(
            code: String,
            message: String,
            path: String,
            cause: Throwable? = null,
        ) = CoverageArtifactException(
            diagnostic = CoverageDiagnostic(
                code = code,
                message = message,
                path = path,
            ),
            cause = cause,
        )
    }
}

/**
 * Decodes and filters one source-mapped Istanbul JSON report produced by an isolated c8 run.
 * [checkBudget] can cancel between I/O, parsing and entry decoding; the bounded JSON parse itself is not preemptible.
 */
fun decodeIstanbulCoverageReport(
    reportPath: Path,
    context: IstanbulCoverageContext,
    checkBudget: () -> Unit = {},
    maxReportBytes: Long = MAX_COVERAGE_REPORT_BYTES,
): PropertyCoverageArtifact {
    val report = IstanbulCoverageReportReader.read(reportPath, checkBudget, maxReportBytes)
    val decoder = IstanbulCoverageDecoder(context, checkBudget)

    return decoder.decode(report)
}
