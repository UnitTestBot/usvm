package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.invariantSeparatorsPathString

/** Reads bounded raw V8 source-map caches that c8 does not retain in its final Istanbul report. */
internal fun inspectRawV8SourceMapDiagnostics(
    rawDirectory: Path,
    sourceRoots: List<String>,
    maxReportFiles: Int = MAX_RAW_V8_REPORT_FILES,
    maxReportBytes: Long = MAX_COVERAGE_REPORT_BYTES,
): List<CoverageDiagnostic> {
    require(maxReportFiles > 0) { "Raw V8 report file limit must be positive" }
    require(maxReportBytes in 1 until Int.MAX_VALUE.toLong()) {
        "Raw V8 report byte limit must be positive and fit in one byte array"
    }

    val reports = listRawReports(rawDirectory, maxReportFiles)
    val roots = sourceRoots.map { sourceRoot -> Path.of(sourceRoot).toAbsolutePath().normalize() }
    val reader = RawV8ReportReader(maxReportBytes = maxReportBytes)
    val diagnostics = reports.flatMap { report -> inspectRawReport(report, roots, reader) }

    return coalesceSourceMapDiagnostics(diagnostics)
}

/** Reads several reports under one aggregate byte budget. */
internal class RawV8ReportReader(maxReportBytes: Long) {
    private var remainingBytes = maxReportBytes

    init {
        require(maxReportBytes in 1 until Int.MAX_VALUE.toLong()) {
            "Raw V8 report byte limit must be positive and fit in one byte array"
        }
    }

    fun readText(reportPath: Path): String {
        // One byte past the remaining budget detects growth without a separate size preflight or manual read loop.
        val readLimit = remainingBytes.toInt() + LIMIT_OVERFLOW_SENTINEL_BYTES
        val bytes = try {
            Files.newInputStream(reportPath).use { input ->
                input.readNBytes(readLimit)
            }
        } catch (error: IOException) {
            failInvalidRawReport(
                message = "Cannot read raw V8 coverage report: ${error.message}",
                path = reportPath,
                cause = error,
            )
        }
        if (bytes.size > remainingBytes) {
            failInvalidRawReport(
                message = "Raw V8 coverage reports exceed the byte limit",
                path = reportPath,
            )
        }
        remainingBytes -= bytes.size

        return decodeUtf8(bytes, reportPath)
    }
}

private data class UnappliedSourceMap(
    val reportPath: Path,
    val scriptUrl: String,
    val sourceMapUrl: String,
) {
    val entryPath: String = "$reportPath.source-map-cache[$scriptUrl]"
}

private fun listRawReports(rawDirectory: Path, maxReportFiles: Int): List<Path> {
    if (!Files.isDirectory(rawDirectory)) {
        failMissingRawReports(
            message = "c8 did not produce the expected raw V8 coverage directory: $rawDirectory",
            path = rawDirectory,
        )
    }

    val listingLimit = maxReportFiles.toLong() + LIMIT_OVERFLOW_SENTINEL_FILES
    val reports = try {
        Files.list(rawDirectory).use { entries ->
            entries
                .filter { path -> path.fileName.toString().endsWith(".json") }
                .limit(listingLimit)
                .collect(Collectors.toList())
        }
    } catch (error: IOException) {
        failCannotListRawReports(rawDirectory, error)
    } catch (error: UncheckedIOException) {
        failCannotListRawReports(rawDirectory, error.cause ?: error)
    }
    if (reports.size > maxReportFiles) {
        failInvalidRawReport(
            message = "Raw V8 coverage contains more than $maxReportFiles report files",
            path = rawDirectory,
        )
    }
    if (reports.isEmpty()) {
        failMissingRawReports(
            message = "c8 did not produce any raw V8 coverage reports in $rawDirectory",
            path = rawDirectory,
        )
    }

    return reports.sortedBy { path -> path.fileName.toString() }
}

private fun decodeUtf8(bytes: ByteArray, reportPath: Path): String = try {
    Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
} catch (error: CharacterCodingException) {
    failInvalidRawReport(
        message = "Cannot decode raw V8 coverage report as UTF-8: ${error.message}",
        path = reportPath,
        cause = error,
    )
}

private fun inspectRawReport(
    reportPath: Path,
    sourceRoots: List<Path>,
    reader: RawV8ReportReader,
): List<CoverageDiagnostic> = readUnappliedSourceMaps(reportPath, reader).mapNotNull { sourceMap ->
    sourceMap.inspect(sourceRoots)
}

/**
 * Extracts only cache entries for which c8 saw a source-map URL but could not load its data.
 * Entries with object-valued `data` are already usable by c8 and need no diagnostic.
 */
private fun readUnappliedSourceMaps(reportPath: Path, reader: RawV8ReportReader): List<UnappliedSourceMap> {
    val report = parseRawReport(reader.readText(reportPath), reportPath)
    val sourceMapCache = report["source-map-cache"] ?: return emptyList()
    if (sourceMapCache !is JsonObject) {
        failInvalidRawReport(
            message = "Raw V8 source-map-cache must be a JSON object",
            path = "$reportPath.source-map-cache",
        )
    }

    return sourceMapCache.mapNotNull { (scriptUrl, entry) ->
        parseUnappliedSourceMap(reportPath, scriptUrl, entry)
    }
}

private fun parseRawReport(text: String, reportPath: Path): JsonObject {
    val report = try {
        PropertyManifestJson.json.parseToJsonElement(text)
    } catch (error: IllegalArgumentException) {
        failInvalidRawReport(
            message = "Raw V8 coverage report is not valid JSON: ${error.message}",
            path = reportPath,
            cause = error,
        )
    }

    if (report !is JsonObject) {
        failInvalidRawReport(
            message = "Raw V8 coverage report must be a JSON object",
            path = reportPath,
        )
    }

    return report
}

private fun parseUnappliedSourceMap(
    reportPath: Path,
    scriptUrl: String,
    element: JsonElement,
): UnappliedSourceMap? {
    val entryPath = "$reportPath.source-map-cache[$scriptUrl]"
    if (element !is JsonObject) {
        failInvalidRawReport(
            message = "Raw V8 source-map cache entry must be a JSON object",
            path = entryPath,
        )
    }
    val data = element["data"]
    if (data == null) {
        failInvalidRawReport(
            message = "Raw V8 source-map cache entry is missing data",
            path = "$entryPath.data",
        )
    }

    if (data is JsonObject) return null
    if (data != JsonNull) {
        failInvalidRawReport(
            message = "Raw V8 source-map cache entry data must be a JSON object when non-null",
            path = "$entryPath.data",
        )
    }

    val sourceMapUrl = readSourceMapUrl(element, entryPath)

    return UnappliedSourceMap(
        reportPath = reportPath,
        scriptUrl = scriptUrl,
        sourceMapUrl = sourceMapUrl,
    )
}

private fun readSourceMapUrl(entry: JsonObject, entryPath: String): String {
    val url = entry["url"] as? JsonPrimitive
    if (url == null || !url.isString || url.content.isBlank()) {
        failInvalidRawReport(
            message = "Raw V8 source-map cache entry must contain a source-map URL",
            path = "$entryPath.url",
        )
    }

    return url.content
}

private fun UnappliedSourceMap.inspect(sourceRoots: List<Path>): CoverageDiagnostic? {
    val scriptUri = try {
        URI.create(scriptUrl)
    } catch (error: IllegalArgumentException) {
        failInvalidRawReport(
            message = "Raw V8 source-map cache key is not a valid script URL: ${error.message}",
            path = entryPath,
            cause = error,
        )
    }
    if (scriptUri.scheme != "file") return null

    val scriptPath = try {
        Path.of(scriptUri.withoutQueryOrFragment()).toAbsolutePath().normalize()
    } catch (error: IllegalArgumentException) {
        failInvalidRawReport(
            message = "Raw V8 script URL cannot be converted to a path: ${error.message}",
            path = entryPath,
            cause = error,
        )
    }
    if (!scriptPath.isGeneratedJavaScriptBelow(sourceRoots)) return null

    val sourceMapPath = resolveSourceMapPath(scriptUri, scriptPath, sourceMapUrl)
    val sourceMapExists = sourceMapPath == null || Files.exists(sourceMapPath)

    return buildSourceMapDiagnostic(
        path = scriptPath.invariantSeparatorsPathString,
        sourceMapExists = sourceMapExists,
    )
}

private fun resolveSourceMapPath(scriptUri: URI, scriptPath: Path, sourceMapUrl: String): Path? {
    val referenceUri = try {
        URI.create(sourceMapUrl)
    } catch (_: IllegalArgumentException) {
        return runCatching { scriptPath.resolveSibling(sourceMapUrl).normalize() }.getOrNull()
    }
    val resolvedUri = scriptUri.resolve(referenceUri)
    if (resolvedUri.scheme != "file" || !resolvedUri.authority.isNullOrEmpty()) return null

    return runCatching { Path.of(resolvedUri.withoutQueryOrFragment()).normalize() }.getOrNull()
}

private fun URI.withoutQueryOrFragment(): URI = if (rawQuery == null && rawFragment == null) {
    this
} else {
    URI(scheme, authority, path, null, null)
}

private fun Path.isGeneratedJavaScriptBelow(sourceRoots: List<Path>): Boolean {
    val extension = fileName.toString().substringAfterLast('.', missingDelimiterValue = "").lowercase()

    return extension in GENERATED_JAVASCRIPT_EXTENSIONS && sourceRoots.any(::startsWith)
}

private fun failCannotListRawReports(rawDirectory: Path, cause: Throwable): Nothing = failInvalidRawReport(
    message = "Cannot list raw V8 coverage reports: ${cause.message}",
    path = rawDirectory,
    cause = cause,
)

private fun failMissingRawReports(message: String, path: Path): Nothing = throw CoverageArtifactException.create(
    code = PbtDiagnosticCode.COVERAGE_REPORT_MISSING,
    message = message,
    path = path.toString(),
)

private fun failInvalidRawReport(
    message: String,
    path: Path,
    cause: Throwable? = null,
): Nothing = failInvalidRawReport(
    message = message,
    path = path.toString(),
    cause = cause,
)

private fun failInvalidRawReport(
    message: String,
    path: String,
    cause: Throwable? = null,
): Nothing = throw CoverageArtifactException.create(
    code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
    message = message,
    path = path,
    cause = cause,
)

private const val MAX_RAW_V8_REPORT_FILES = 1_024
private const val LIMIT_OVERFLOW_SENTINEL_BYTES = 1
private const val LIMIT_OVERFLOW_SENTINEL_FILES = 1
private val GENERATED_JAVASCRIPT_EXTENSIONS = hashSetOf("js", "mjs", "cjs")
