package org.usvm.ts.pbt.coverage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.usvm.ts.pbt.PbtDiagnosticCode
import org.usvm.ts.pbt.backend.BranchArmCoverage
import org.usvm.ts.pbt.backend.BranchCoverage
import org.usvm.ts.pbt.backend.CoverageArtifactKind
import org.usvm.ts.pbt.backend.CoverageCollectorIdentity
import org.usvm.ts.pbt.backend.CoverageDiagnostic
import org.usvm.ts.pbt.backend.CoverageProvenance
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.FunctionCoverage
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.SourceFileCoverage
import org.usvm.ts.pbt.backend.SourcePosition
import org.usvm.ts.pbt.backend.SourceRange
import org.usvm.ts.pbt.backend.StatementCoverage
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.PropertyId
import java.io.IOException
import java.nio.file.Files
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
) : IllegalArgumentException(diagnostic.message, cause)

/** Decodes and filters one source-mapped Istanbul JSON report produced by an isolated c8 run. */
fun decodeIstanbulCoverageReport(
    reportPath: Path,
    context: IstanbulCoverageContext,
): PropertyCoverageArtifact = decodeReport(readCoverageReport(reportPath), context)

private fun readCoverageReport(reportPath: Path): JsonObject {
    requireCoverageReport(reportPath)
    return parseCoverageReport(readCoverageReportText(reportPath), reportPath)
}

private fun requireCoverageReport(reportPath: Path) {
    if (!Files.isRegularFile(reportPath)) {
        throw coverageError(
            code = PbtDiagnosticCode.COVERAGE_REPORT_MISSING,
            message = "c8 did not produce the expected Istanbul report: $reportPath",
            path = reportPath.toString(),
        )
    }
}

private fun readCoverageReportText(reportPath: Path): String {
    requireCoverageReportSize(reportPath)

    return try {
        Files.readString(reportPath)
    } catch (error: IOException) {
        throw unreadableCoverageReport(reportPath, error)
    }
}

private fun requireCoverageReportSize(reportPath: Path) {
    val reportSize = try {
        Files.size(reportPath)
    } catch (error: IOException) {
        throw unreadableCoverageReport(reportPath, error)
    }
    if (reportSize > MAX_COVERAGE_REPORT_BYTES) {
        throw coverageError(
            code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
            message = "Istanbul coverage report exceeds $MAX_COVERAGE_REPORT_BYTES bytes",
            path = reportPath.toString(),
        )
    }
}

private fun unreadableCoverageReport(
    reportPath: Path,
    error: IOException,
): CoverageArtifactException = coverageError(
    code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
    message = "Cannot read Istanbul coverage report: ${error.message}",
    path = reportPath.toString(),
    cause = error,
)

private fun parseCoverageReport(text: String, reportPath: Path): JsonObject = try {
    PropertyManifestJson.json.parseToJsonElement(text).jsonObject
} catch (error: IllegalArgumentException) {
    throw coverageError(
        code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
        message = "Istanbul coverage report is not valid JSON: ${error.message}",
        path = reportPath.toString(),
        cause = error,
    )
}

private fun decodeReport(
    report: JsonObject,
    context: IstanbulCoverageContext,
): PropertyCoverageArtifact {
    val normalizedRoots = context.sourceRoots.map(::normalizeCoveragePath)
    val normalizedEntryPoints = context.propertyEntryPointPaths.mapTo(hashSetOf(), ::normalizeCoveragePath)
    val normalizedAdapterRoot = normalizeCoveragePath(context.adapterRoot)
    val diagnostics = mutableListOf<CoverageDiagnostic>()
    val files = report.entries.mapNotNull { (reportKey, element) ->
        val fileObject = element.asObject("coverage[$reportKey]")
        val path = normalizeCoveragePath(
            fileObject.optionalString("path") ?: reportKey,
        )
        if (isGeneratedJavaScriptBelowSourceRoot(path, normalizedRoots)) {
            val sourceMapExists = Files.exists(Path.of("$path.map"))
            diagnostics += CoverageDiagnostic(
                code = if (sourceMapExists) {
                    PbtDiagnosticCode.COVERAGE_SOURCE_MAP_INVALID
                } else {
                    PbtDiagnosticCode.COVERAGE_SOURCE_MAP_MISSING
                },
                message = if (sourceMapExists) {
                    "Executed JavaScript has a source map that c8 could not remap to its original source"
                } else {
                    "Executed JavaScript below a TypeScript source root has no source map"
                },
                path = path,
            )
            return@mapNotNull null
        }
        val scope = classifyCoverageScope(
            path = path,
            sourceRoots = normalizedRoots,
            propertyEntryPoints = normalizedEntryPoints,
            adapterRoot = normalizedAdapterRoot,
        ) ?: return@mapNotNull null
        if (!shouldRetainCoverageFile(path, scope, normalizedRoots, context.request)) {
            return@mapNotNull null
        }
        decodeSourceFileCoverage(fileObject, path, reportKey)
    }.sortedBy(SourceFileCoverage::path)

    return PropertyCoverageArtifact(
        kind = CoverageArtifactKind.NODE_SOURCE,
        backendId = context.backendId,
        backendVersion = context.backendVersion,
        propertyId = context.propertyId,
        provenance = CoverageProvenance(
            collector = context.collector,
            runtimeId = "node",
            runtimeVersion = context.runtimeVersion,
            sourceRoots = normalizedRoots,
            request = context.request,
        ),
        files = files,
        diagnostics = diagnostics.sortedWith(compareBy(CoverageDiagnostic::path, CoverageDiagnostic::code)),
    )
}

private fun shouldRetainCoverageFile(
    path: String,
    scope: CoverageScope,
    sourceRoots: List<String>,
    request: PropertyCoverageRequest,
): Boolean {
    if (scope !in request.scopes) return false
    if (!matchesCoveragePath(path, request.includePatterns, sourceRoots)) return false
    return request.excludePatterns.isEmpty() || !matchesCoveragePath(path, request.excludePatterns, sourceRoots)
}

private fun classifyCoverageScope(
    path: String,
    sourceRoots: List<String>,
    propertyEntryPoints: Set<String>,
    adapterRoot: String,
): CoverageScope? = when {
    "/node_modules/" in path -> CoverageScope.DEPENDENCIES
    isWithin(path, adapterRoot) -> CoverageScope.GENERATED_BACKEND_WRAPPERS
    path in propertyEntryPoints -> CoverageScope.PROPERTY_ENTRY_POINTS
    sourceRoots.any { root -> isWithin(path, root) } -> CoverageScope.SOURCE_UNDER_TEST
    else -> null
}

private fun isGeneratedJavaScriptBelowSourceRoot(path: String, sourceRoots: List<String>): Boolean {
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in GENERATED_JAVASCRIPT_EXTENSIONS && sourceRoots.any { root -> isWithin(path, root) }
}

private fun decodeSourceFileCoverage(
    file: JsonObject,
    path: String,
    reportKey: String,
): SourceFileCoverage = try {
    SourceFileCoverage(
        path = path,
        statements = decodeStatements(file, reportKey),
        functions = decodeFunctions(file, reportKey),
        branches = decodeBranches(file, reportKey),
    )
} catch (error: CoverageArtifactException) {
    throw error
} catch (error: IllegalArgumentException) {
    throw coverageError(
        code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
        message = "Invalid Istanbul coverage for $path: ${error.message}",
        path = "coverage[$reportKey]",
        cause = error,
    )
}

private fun decodeStatements(file: JsonObject, reportKey: String): List<StatementCoverage> {
    val locations = file.requiredObject("statementMap", "coverage[$reportKey].statementMap")
    val hits = file.requiredObject("s", "coverage[$reportKey].s")
    requireMatchingKeys(locations, hits, "coverage[$reportKey].statementMap", "coverage[$reportKey].s")
    return locations.keys.map(::parseCoverageId).sorted().map { statementId ->
        val id = statementId.toString()
        StatementCoverage(
            statementId = statementId,
            location = locations.getValue(id).decodeRange("coverage[$reportKey].statementMap[$id]"),
            hits = hits.getValue(id).decodeHitCount("coverage[$reportKey].s[$id]"),
        )
    }
}

private fun decodeFunctions(file: JsonObject, reportKey: String): List<FunctionCoverage> {
    val locations = file.requiredObject("fnMap", "coverage[$reportKey].fnMap")
    val hits = file.requiredObject("f", "coverage[$reportKey].f")
    requireMatchingKeys(locations, hits, "coverage[$reportKey].fnMap", "coverage[$reportKey].f")
    return locations.keys.map(::parseCoverageId).sorted().map { functionId ->
        val id = functionId.toString()
        val function = locations.getValue(id).asObject("coverage[$reportKey].fnMap[$id]")
        FunctionCoverage(
            functionId = functionId,
            name = function.optionalString("name")?.ifBlank { ANONYMOUS_FUNCTION_NAME } ?: ANONYMOUS_FUNCTION_NAME,
            declaration = function.requiredElement("decl", "coverage[$reportKey].fnMap[$id].decl")
                .decodeRange("coverage[$reportKey].fnMap[$id].decl"),
            body = function.requiredElement("loc", "coverage[$reportKey].fnMap[$id].loc")
                .decodeRange("coverage[$reportKey].fnMap[$id].loc"),
            hits = hits.getValue(id).decodeHitCount("coverage[$reportKey].f[$id]"),
        )
    }
}

private fun decodeBranches(file: JsonObject, reportKey: String): List<BranchCoverage> {
    val locations = file.requiredObject("branchMap", "coverage[$reportKey].branchMap")
    val hits = file.requiredObject("b", "coverage[$reportKey].b")
    requireMatchingKeys(locations, hits, "coverage[$reportKey].branchMap", "coverage[$reportKey].b")
    return locations.keys.map(::parseCoverageId).sorted().map { branchId ->
        val id = branchId.toString()
        val branchPath = "coverage[$reportKey].branchMap[$id]"
        val branch = locations.getValue(id).asObject(branchPath)
        val (armLocations, armHits) = decodeBranchArrays(branch, hits, reportKey, id, branchPath)
        BranchCoverage(
            branchId = branchId,
            type = branch.requiredString("type", "$branchPath.type"),
            location = branch.requiredElement("loc", "$branchPath.loc").decodeRange("$branchPath.loc"),
            arms = armLocations.indices.map { armIndex ->
                BranchArmCoverage(
                    location = armLocations[armIndex].decodeRange("$branchPath.locations[$armIndex]"),
                    hits = armHits[armIndex].decodeHitCount("coverage[$reportKey].b[$id][$armIndex]"),
                )
            },
        )
    }
}

private fun decodeBranchArrays(
    branch: JsonObject,
    hits: JsonObject,
    reportKey: String,
    id: String,
    branchPath: String,
): Pair<JsonArray, JsonArray> {
    val armLocations = branch.requiredElement("locations", "$branchPath.locations") as? JsonArray
        ?: throw invalidReport("Expected an array", "$branchPath.locations")
    val armHitsPath = "coverage[$reportKey].b[$id]"
    val armHits = hits.getValue(id) as? JsonArray
        ?: throw invalidReport("Expected an array", armHitsPath)
    requireMatchingBranchArms(armLocations, armHits, branchPath)
    return armLocations to armHits
}

private fun requireMatchingBranchArms(
    armLocations: JsonArray,
    armHits: JsonArray,
    branchPath: String,
) {
    if (armLocations.size != armHits.size) {
        throw invalidReport("Branch location and hit counts have different sizes", branchPath)
    }
}

private fun JsonElement.decodeRange(path: String): SourceRange {
    val range = asObject(path)
    return SourceRange(
        start = range.requiredElement("start", "$path.start").decodePosition("$path.start"),
        end = range.requiredElement("end", "$path.end").decodePosition("$path.end"),
    )
}

private fun JsonElement.decodePosition(path: String): SourcePosition {
    val position = asObject(path)
    return SourcePosition(
        line = position.requiredInt("line", "$path.line"),
        column = position.requiredInt("column", "$path.column"),
    )
}

private fun JsonElement.decodeHitCount(path: String): Long {
    val primitive = this as? JsonPrimitive ?: throw invalidReport("Expected an integer", path)
    return primitive.longOrNull ?: throw invalidReport("Expected an integer", path)
}

private fun JsonElement.asObject(path: String): JsonObject = this as? JsonObject
    ?: throw invalidReport("Expected an object", path)

private fun JsonObject.requiredObject(
    name: String,
    path: String,
): JsonObject = requiredElement(name, path).asObject(path)

private fun JsonObject.requiredElement(name: String, path: String): JsonElement = this[name]
    ?: throw invalidReport("Missing required field $name", path)

private fun JsonObject.requiredString(name: String, path: String): String {
    val primitive = this[name] as? JsonPrimitive ?: throw invalidReport("Expected a string", path)
    return primitive.contentOrNull ?: throw invalidReport("Expected a string", path)
}

private fun JsonObject.optionalString(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.requiredInt(name: String, path: String): Int {
    val primitive = this[name] as? JsonPrimitive ?: throw invalidReport("Expected an integer", path)
    return primitive.intOrNull ?: throw invalidReport("Expected an integer", path)
}

private fun requireMatchingKeys(
    locations: JsonObject,
    hits: JsonObject,
    locationsPath: String,
    hitsPath: String,
) {
    if (locations.keys != hits.keys) {
        throw invalidReport("Coverage map keys do not match hit counter keys ($locationsPath)", hitsPath)
    }
}

private fun parseCoverageId(value: String): Int = value.toIntOrNull()?.takeIf { id -> id >= 0 }
    ?: throw invalidReport("Coverage ID must be a non-negative integer", value)

private fun normalizeCoveragePath(path: String): String = Path.of(path)
    .toAbsolutePath()
    .normalize()
    .toString()
    .replace('\\', '/')

private fun invalidReport(message: String, path: String): CoverageArtifactException = coverageError(
    code = PbtDiagnosticCode.COVERAGE_REPORT_INVALID,
    message = message,
    path = path,
)

private fun coverageError(
    code: String,
    message: String,
    path: String,
    cause: Throwable? = null,
) = CoverageArtifactException(
    diagnostic = CoverageDiagnostic(code = code, message = message, path = path),
    cause = cause,
)

private const val ANONYMOUS_FUNCTION_NAME = "<anonymous>"
private const val MAX_COVERAGE_REPORT_BYTES = 64L * 1024 * 1024
private val GENERATED_JAVASCRIPT_EXTENSIONS = setOf("js", "mjs", "cjs")
