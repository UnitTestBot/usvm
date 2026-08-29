package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path

/** Reads bounded raw V8 source-map caches that c8 does not retain in its final Istanbul report. */
internal fun inspectRawV8SourceMapDiagnostics(
    rawDirectory: Path,
    sourceRoots: List<String>,
    maxReportFiles: Int = MAX_RAW_V8_REPORT_FILES,
    maxReportBytes: Long = MAX_COVERAGE_REPORT_BYTES,
): List<CoverageDiagnostic> {
    require(maxReportFiles > 0) { "Raw V8 report file limit must be positive" }
    require(maxReportBytes > 0) { "Raw V8 report byte limit must be positive" }

    if (!Files.isDirectory(rawDirectory)) {
        throw CoverageArtifactException.create(
            code = PbtDiagnosticCode.COVERAGE_REPORT_MISSING,
            message = "c8 did not produce the expected raw V8 coverage directory: $rawDirectory",
            path = rawDirectory.toString(),
        )
    }

    val reportPaths = listRawReportPaths(rawDirectory, maxReportFiles)
    if (reportPaths.isEmpty()) {
        throw CoverageArtifactException.create(
            code = PbtDiagnosticCode.COVERAGE_REPORT_MISSING,
            message = "c8 did not produce any raw V8 coverage reports in $rawDirectory",
            path = rawDirectory.toString(),
        )
    }
    requireAllowedRawReportSizes(reportPaths, maxReportBytes)
    val normalizedSourceRoots = sourceRoots.map(::normalizeCoveragePath)
    val diagnostics = reportPaths.flatMap { reportPath ->
        inspectRawReport(
            reportPath = reportPath,
            sourceRoots = normalizedSourceRoots,
        )
    }

    return diagnostics
        .distinct()
        .sortedWith(COVERAGE_DIAGNOSTIC_ORDER)
}

/** Raw source-map evidence takes precedence over final-report guesses for the same generated script. */
internal fun mergeCoverageDiagnostics(
    finalDiagnostics: List<CoverageDiagnostic>,
    rawDiagnostics: List<CoverageDiagnostic>,
): List<CoverageDiagnostic> {
    val rawSourceMapPaths = rawDiagnostics
        .filter(::isSourceMapDiagnostic)
        .mapNotNullTo(hashSetOf(), CoverageDiagnostic::path)
    val retainedFinalDiagnostics = finalDiagnostics.filterNot { diagnostic ->
        isSourceMapDiagnostic(diagnostic) && diagnostic.path in rawSourceMapPaths
    }

    return (retainedFinalDiagnostics + rawDiagnostics)
        .distinct()
        .sortedWith(COVERAGE_DIAGNOSTIC_ORDER)
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

internal fun normalizeCoveragePath(path: String): String = Path.of(path)
    .toAbsolutePath()
    .normalize()
    .toString()
    .replace('\\', '/')

private fun listRawReportPaths(rawDirectory: Path, maxReportFiles: Int): List<Path> = try {
    val reportPaths = mutableListOf<Path>()
    Files.newDirectoryStream(rawDirectory, "*.json").use { entries ->
        for (entry in entries) {
            addRawReportPath(
                reportPaths = reportPaths,
                reportPath = entry,
                rawDirectory = rawDirectory,
                maxReportFiles = maxReportFiles,
            )
        }
    }

    reportPaths.sortedBy { path -> path.fileName.toString() }
} catch (error: CoverageArtifactException) {
    throw error
} catch (error: IOException) {
    throw invalidRawReport(
        message = "Cannot list raw V8 coverage reports: ${error.message}",
        path = rawDirectory,
        cause = error,
    )
}

private fun addRawReportPath(
    reportPaths: MutableList<Path>,
    reportPath: Path,
    rawDirectory: Path,
    maxReportFiles: Int,
) {
    if (reportPaths.size == maxReportFiles) {
        throw CoverageArtifactException.create(
            code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
            message = "Raw V8 coverage contains more than $maxReportFiles report files",
            path = rawDirectory.toString(),
        )
    }

    reportPaths.add(reportPath)
}

private fun requireAllowedRawReportSizes(reportPaths: List<Path>, maxReportBytes: Long) {
    var totalBytes = 0L
    for (reportPath in reportPaths) {
        val reportBytes = try {
            Files.size(reportPath)
        } catch (error: IOException) {
            throw invalidRawReport(
                message = "Cannot read raw V8 coverage report size: ${error.message}",
                path = reportPath,
                cause = error,
            )
        }

        if (reportBytes > maxReportBytes - totalBytes) {
            throw invalidRawReport(
                message = "Raw V8 coverage reports exceed $maxReportBytes bytes",
                path = reportPath,
            )
        }

        totalBytes += reportBytes
    }
}

private fun inspectRawReport(reportPath: Path, sourceRoots: List<String>): List<CoverageDiagnostic> {
    val report = readRawReport(reportPath)
    val sourceMapCache = readSourceMapCache(report, reportPath) ?: return emptyList()

    return sourceMapCache.mapNotNull { (scriptUrl, cacheEntryElement) ->
        inspectSourceMapCacheEntry(
            reportPath = reportPath,
            scriptUrl = scriptUrl,
            cacheEntry = requireSourceMapCacheEntry(
                reportPath = reportPath,
                scriptUrl = scriptUrl,
                element = cacheEntryElement,
            ),
            sourceRoots = sourceRoots,
        )
    }
}

private fun readRawReport(reportPath: Path): JsonObject {
    val reportText = readRawReportText(reportPath)

    return parseRawReport(reportText, reportPath)
}

private fun readRawReportText(reportPath: Path): String = try {
    Files.readString(reportPath)
} catch (error: IOException) {
    throw invalidRawReport(
        message = "Cannot read raw V8 coverage report: ${error.message}",
        path = reportPath,
        cause = error,
    )
}

private fun parseRawReport(reportText: String, reportPath: Path): JsonObject = try {
    PropertyManifestJson.json.parseToJsonElement(reportText) as? JsonObject
        ?: throw invalidRawReport(
            message = "Raw V8 coverage report must be a JSON object",
            path = reportPath,
        )
} catch (error: CoverageArtifactException) {
    throw error
} catch (error: IllegalArgumentException) {
    throw invalidRawReport(
        message = "Raw V8 coverage report is not valid JSON: ${error.message}",
        path = reportPath,
        cause = error,
    )
}

private fun readSourceMapCache(report: JsonObject, reportPath: Path): JsonObject? {
    val sourceMapCacheElement = report["source-map-cache"] ?: return null
    return sourceMapCacheElement as? JsonObject
        ?: throw invalidRawReport(
            message = "Raw V8 source-map-cache must be a JSON object",
            path = "$reportPath.source-map-cache",
        )
}

private fun requireSourceMapCacheEntry(
    reportPath: Path,
    scriptUrl: String,
    element: JsonElement,
): JsonObject = element as? JsonObject
    ?: throw invalidRawReport(
        message = "Raw V8 source-map cache entry must be a JSON object",
        path = "$reportPath.source-map-cache[$scriptUrl]",
    )

private fun inspectSourceMapCacheEntry(
    reportPath: Path,
    scriptUrl: String,
    cacheEntry: JsonObject,
    sourceRoots: List<String>,
): CoverageDiagnostic? {
    val data = cacheEntry["data"]
        ?: throw invalidRawReport(
            message = "Raw V8 source-map cache entry is missing data",
            path = "$reportPath.source-map-cache[$scriptUrl].data",
        )
    if (data != JsonNull) return null

    val scriptUri = parseScriptUri(reportPath, scriptUrl)
    if (scriptUri.scheme != "file") return null

    val scriptPath = scriptUri.toCoveragePath(reportPath, scriptUrl)
    if (!isGeneratedJavaScriptBelowSourceRoot(scriptPath, sourceRoots)) return null

    val referencedUrl = cacheEntry["url"] as? JsonPrimitive
    if (referencedUrl == null || !referencedUrl.isString || referencedUrl.content.isBlank()) {
        throw invalidRawReport(
            message = "Raw V8 source-map cache entry must contain a source-map URL",
            path = "$reportPath.source-map-cache[$scriptUrl].url",
        )
    }

    val sourceMapPath = resolveSourceMapPath(
        scriptUri = scriptUri,
        scriptPath = Path.of(scriptPath),
        referencedUrl = referencedUrl.content,
    )
    val sourceMapExists = sourceMapPath == null || Files.exists(sourceMapPath)

    return buildSourceMapDiagnostic(
        path = scriptPath,
        sourceMapExists = sourceMapExists,
    )
}

private fun parseScriptUri(reportPath: Path, scriptUrl: String): URI = try {
    URI(scriptUrl)
} catch (error: IllegalArgumentException) {
    throw invalidRawReport(
        message = "Raw V8 source-map cache key is not a valid script URL: ${error.message}",
        path = "$reportPath.source-map-cache[$scriptUrl]",
        cause = error,
    )
}

private fun URI.toCoveragePath(reportPath: Path, scriptUrl: String): String {
    return try {
        normalizeCoveragePath(toLocalFilePath().toString())
    } catch (error: IllegalArgumentException) {
        throw invalidScriptUrlPath(reportPath, scriptUrl, error)
    } catch (error: URISyntaxException) {
        throw invalidScriptUrlPath(reportPath, scriptUrl, error)
    }
}

private fun invalidScriptUrlPath(
    reportPath: Path,
    scriptUrl: String,
    error: Exception,
): CoverageArtifactException = invalidRawReport(
    message = "Raw V8 script URL cannot be converted to a path: ${error.message}",
    path = "$reportPath.source-map-cache[$scriptUrl]",
    cause = error,
)

private fun resolveSourceMapPath(scriptUri: URI, scriptPath: Path, referencedUrl: String): Path? {
    if (referencedUrl.startsWith("data:")) return null

    return try {
        val referenceUri = URI(referencedUrl)
        val resolvedUri = scriptUri.resolve(referenceUri)

        if (resolvedUri.scheme == "file") resolvedUri.toLocalFilePath().normalize() else null
    } catch (_: IllegalArgumentException) {
        resolveSourceMapPathFallback(scriptPath, referencedUrl)
    } catch (_: URISyntaxException) {
        resolveSourceMapPathFallback(scriptPath, referencedUrl)
    }
}

@Throws(URISyntaxException::class)
private fun URI.toLocalFilePath(): Path {
    val localFileUri = URI(scheme, authority, path, null, null)

    return Path.of(localFileUri)
}

private fun resolveSourceMapPathFallback(scriptPath: Path, referencedUrl: String): Path? =
    runCatching {
        val referencedPath = Path.of(referencedUrl)

        if (referencedPath.isAbsolute) {
            referencedPath.normalize()
        } else {
            scriptPath.parent.resolve(referencedPath).normalize()
        }
    }.getOrNull()

private fun isGeneratedJavaScriptBelowSourceRoot(path: String, sourceRoots: List<String>): Boolean {
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    return extension in GENERATED_JAVASCRIPT_EXTENSIONS && sourceRoots.any { root -> isWithin(path, root) }
}

private fun isSourceMapDiagnostic(diagnostic: CoverageDiagnostic): Boolean =
    diagnostic.code == PbtDiagnosticCode.COVERAGE_SOURCE_MAP_MISSING ||
        diagnostic.code == PbtDiagnosticCode.COVERAGE_SOURCE_MAP_INVALID

private fun invalidRawReport(
    message: String,
    path: Path,
    cause: Throwable? = null,
): CoverageArtifactException = invalidRawReport(
    message = message,
    path = path.toString(),
    cause = cause,
)

private fun invalidRawReport(
    message: String,
    path: String,
    cause: Throwable? = null,
): CoverageArtifactException = CoverageArtifactException.create(
    code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
    message = message,
    path = path,
    cause = cause,
)

private const val MAX_RAW_V8_REPORT_FILES = 1_024
private val GENERATED_JAVASCRIPT_EXTENSIONS = hashSetOf("js", "mjs", "cjs")
private val COVERAGE_DIAGNOSTIC_ORDER = compareBy(CoverageDiagnostic::path, CoverageDiagnostic::code)
