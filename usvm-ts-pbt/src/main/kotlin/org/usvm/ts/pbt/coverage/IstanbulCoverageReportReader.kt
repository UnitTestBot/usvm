package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Reads one bounded Istanbul report and converts I/O and JSON failures into coverage diagnostics. */
internal object IstanbulCoverageReportReader {
    fun read(reportPath: Path): JsonObject {
        requireRegularFile(reportPath)
        requireAllowedSize(reportPath)

        val reportText = readText(reportPath)

        return parse(reportText, reportPath)
    }

    private fun requireRegularFile(reportPath: Path) {
        if (!Files.isRegularFile(reportPath)) {
            throw CoverageArtifactException.create(
                code = PbtDiagnosticCode.COVERAGE_REPORT_MISSING,
                message = "c8 did not produce the expected Istanbul report: $reportPath",
                path = reportPath.toString(),
            )
        }
    }

    private fun requireAllowedSize(reportPath: Path) {
        val reportSize = try {
            Files.size(reportPath)
        } catch (error: IOException) {
            throw unreadableReport(reportPath, error)
        }

        if (reportSize > MAX_COVERAGE_REPORT_BYTES) {
            throw CoverageArtifactException.create(
                code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
                message = "Istanbul coverage report exceeds $MAX_COVERAGE_REPORT_BYTES bytes",
                path = reportPath.toString(),
            )
        }
    }

    private fun readText(reportPath: Path): String = try {
        Files.readString(reportPath)
    } catch (error: IOException) {
        throw unreadableReport(reportPath, error)
    }

    private fun parse(reportText: String, reportPath: Path): JsonObject = try {
        PropertyManifestJson.json.parseToJsonElement(reportText).jsonObject
    } catch (error: IllegalArgumentException) {
        throw CoverageArtifactException.create(
            code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
            message = "Istanbul coverage report is not valid JSON: ${error.message}",
            path = reportPath.toString(),
            cause = error,
        )
    }

    private fun unreadableReport(
        reportPath: Path,
        error: IOException,
    ): CoverageArtifactException = CoverageArtifactException.create(
        code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
        message = "Cannot read Istanbul coverage report: ${error.message}",
        path = reportPath.toString(),
        cause = error,
    )

    private const val MAX_COVERAGE_REPORT_BYTES = 64L * 1024 * 1024
}
