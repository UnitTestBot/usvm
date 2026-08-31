package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.usvm.ts.pbt.backend.CoverageArtifactKind
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import org.usvm.ts.pbt.backend.CoverageProvenance
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.SourceFileCoverage
import java.nio.file.Files
import java.nio.file.Path

/** Selects relevant report entries and assembles the coverage artifact for one property run. */
internal class IstanbulCoverageDecoder(
    private val context: IstanbulCoverageContext,
) {
    private val sourceRoots = context.sourceRoots.map(::normalizeCoveragePath)
    private val propertyEntryPoints = context.propertyEntryPointPaths
        .mapTo(hashSetOf(), ::normalizeCoveragePath)
    private val adapterRoot = normalizeCoveragePath(context.adapterRoot)

    fun decode(report: JsonObject): PropertyCoverageArtifact {
        val diagnostics = mutableListOf<CoverageDiagnostic>()
        val files = decodeFiles(report, diagnostics)
        val sortedDiagnostics = diagnostics.sortedWith(
            compareBy(CoverageDiagnostic::path, CoverageDiagnostic::code),
        )

        return buildArtifact(files, sortedDiagnostics)
    }

    private fun decodeFiles(
        report: JsonObject,
        diagnostics: MutableList<CoverageDiagnostic>,
    ): List<SourceFileCoverage> {
        val decodedFiles = buildList {
            for ((reportKey, reportEntry) in report) {
                val decodedFile = decodeFile(reportKey, reportEntry, diagnostics)
                if (decodedFile != null) {
                    add(decodedFile)
                }
            }
        }

        return decodedFiles.sortedBy(SourceFileCoverage::path)
    }

    private fun decodeFile(
        reportKey: String,
        reportEntry: JsonElement,
        diagnostics: MutableList<CoverageDiagnostic>,
    ): SourceFileCoverage? {
        val coveragePath = "coverage[$reportKey]"
        val fileJson = IstanbulJsonValue(reportEntry, coveragePath)
        val fileObject = fileJson.asObject()
        val reportedPath = fileJson.optionalString(name = "path")
        val path = normalizeCoveragePath(reportedPath ?: reportKey)

        if (isGeneratedJavaScriptBelowSourceRoot(path)) {
            diagnostics += sourceMapDiagnostic(path)
            return null
        }

        val scope = classifyScope(path) ?: return null
        if (!shouldRetain(path, scope)) {
            return null
        }

        return IstanbulSourceFileDecoder(
            file = fileObject,
            path = path,
            reportKey = reportKey,
        ).decode()
    }

    private fun sourceMapDiagnostic(path: String): CoverageDiagnostic {
        val sourceMapPath = Path.of("$path.map")
        return buildSourceMapDiagnostic(
            path = path,
            sourceMapExists = Files.exists(sourceMapPath),
        )
    }

    private fun classifyScope(path: String): CoverageScope? = when {
        "/node_modules/" in path -> CoverageScope.DEPENDENCIES
        isWithin(path, adapterRoot) -> CoverageScope.GENERATED_BACKEND_WRAPPERS
        path in propertyEntryPoints -> CoverageScope.PROPERTY_ENTRY_POINTS
        sourceRoots.any { sourceRoot -> isWithin(path, sourceRoot) } -> CoverageScope.SOURCE_UNDER_TEST
        else -> null
    }

    private fun shouldRetain(path: String, scope: CoverageScope): Boolean {
        val scopeWasRequested = scope in context.request.scopes
        val matchesIncludePatterns = matchesCoveragePath(
            path = path,
            patterns = context.request.includePatterns,
            sourceRoots = sourceRoots,
        )
        val matchesExcludePatterns = context.request.excludePatterns.isNotEmpty() &&
            matchesCoveragePath(
                path = path,
                patterns = context.request.excludePatterns,
                sourceRoots = sourceRoots,
            )

        return scopeWasRequested && matchesIncludePatterns && !matchesExcludePatterns
    }

    private fun isGeneratedJavaScriptBelowSourceRoot(path: String): Boolean {
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val isGeneratedJavaScript = extension in GENERATED_JAVASCRIPT_EXTENSIONS
        val isBelowSourceRoot = sourceRoots.any { sourceRoot -> isWithin(path, sourceRoot) }

        return isGeneratedJavaScript && isBelowSourceRoot
    }

    private fun buildArtifact(
        files: List<SourceFileCoverage>,
        diagnostics: List<CoverageDiagnostic>,
    ): PropertyCoverageArtifact {
        val provenance = CoverageProvenance(
            collector = context.collector,
            runtimeId = "node",
            runtimeVersion = context.runtimeVersion,
            sourceRoots = sourceRoots,
            request = context.request,
        )

        return PropertyCoverageArtifact(
            kind = CoverageArtifactKind.NODE_SOURCE,
            backendId = context.backendId,
            backendVersion = context.backendVersion,
            propertyId = context.propertyId,
            provenance = provenance,
            files = files,
            diagnostics = diagnostics,
        )
    }

    private companion object {
        val GENERATED_JAVASCRIPT_EXTENSIONS = hashSetOf("js", "mjs", "cjs")
    }
}
