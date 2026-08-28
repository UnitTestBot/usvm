package org.usvm.ts.pbt.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.PropertyId

const val PROPERTY_COVERAGE_ARTIFACT_VERSION = 1

/** Distinguishes Node source coverage from future replay coverage over another representation. */
@Serializable
enum class CoverageArtifactKind {
    @SerialName("node_source")
    NODE_SOURCE,
}

/** Executed-file categories that may be retained in one coverage artifact. */
@Serializable
enum class CoverageScope {
    @SerialName("source_under_test")
    SOURCE_UNDER_TEST,

    @SerialName("property_entry_points")
    PROPERTY_ENTRY_POINTS,

    @SerialName("generated_backend_wrappers")
    GENERATED_BACKEND_WRAPPERS,

    @SerialName("dependencies")
    DEPENDENCIES,
}

/** Whether a concrete backend can produce the common property coverage artifact. */
@Serializable
enum class CoverageCapabilityLevel {
    @SerialName("supported")
    SUPPORTED,

    @SerialName("unsupported")
    UNSUPPORTED,
}

/** Stable diagnostic emitted while negotiating or collecting coverage. */
@Serializable
data class CoverageDiagnostic(
    val code: String,
    val message: String,
    val path: String? = null,
) {
    init {
        require(code.isNotBlank()) { "Coverage diagnostic code must not be blank" }
        require(message.isNotBlank()) { "Coverage diagnostic message must not be blank" }
    }
}

/** Identifies the concrete source coverage collector and its behavior-defining version. */
@Serializable
data class CoverageCollectorIdentity(
    val id: String,
    val version: String,
) {
    init {
        require(id.isNotBlank()) { "Coverage collector ID must not be blank" }
        require(version.isNotBlank()) { "Coverage collector version must not be blank" }
    }
}

/** Reports whether a backend version implements property source coverage. */
@Serializable
data class PropertyCoverageCapability(
    val backendId: String,
    val backendVersion: String,
    val level: CoverageCapabilityLevel,
    val collector: CoverageCollectorIdentity? = null,
    val artifactVersion: Int? = null,
    val diagnostics: List<CoverageDiagnostic> = emptyList(),
) {
    init {
        require(backendId.isNotBlank()) { "Backend ID must not be blank" }
        require(backendVersion.isNotBlank()) { "Backend version must not be blank" }
        when (level) {
            CoverageCapabilityLevel.SUPPORTED -> {
                requireNotNull(collector) { "Supported coverage requires a collector identity" }
                require(artifactVersion == PROPERTY_COVERAGE_ARTIFACT_VERSION) {
                    "Supported coverage requires artifact version $PROPERTY_COVERAGE_ARTIFACT_VERSION"
                }
                require(diagnostics.isEmpty()) { "Supported coverage must not contain capability diagnostics" }
            }

            CoverageCapabilityLevel.UNSUPPORTED -> {
                require(collector == null) { "Unsupported coverage must not name a collector" }
                require(artifactVersion == null) { "Unsupported coverage must not name an artifact version" }
                require(diagnostics.isNotEmpty()) { "Unsupported coverage requires an actionable diagnostic" }
            }
        }
    }

    companion object {
        fun supported(
            backendId: String,
            backendVersion: String,
            collector: CoverageCollectorIdentity,
            artifactVersion: Int = PROPERTY_COVERAGE_ARTIFACT_VERSION,
        ) = PropertyCoverageCapability(
            backendId = backendId,
            backendVersion = backendVersion,
            level = CoverageCapabilityLevel.SUPPORTED,
            collector = collector,
            artifactVersion = artifactVersion,
        )

        fun unsupported(
            backendId: String,
            backendVersion: String,
            diagnostic: CoverageDiagnostic,
        ) = PropertyCoverageCapability(
            backendId = backendId,
            backendVersion = backendVersion,
            level = CoverageCapabilityLevel.UNSUPPORTED,
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Backend-neutral controls for source coverage collected during one property run. */
@Serializable
data class PropertyCoverageRequest(
    val scopes: Set<CoverageScope> = setOf(CoverageScope.SOURCE_UNDER_TEST),
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
) {
    init {
        require(scopes.isNotEmpty()) { "At least one coverage scope is required" }
        require(includePatterns.all(String::isNotBlank)) { "Coverage include patterns must not be blank" }
        require(excludePatterns.all(String::isNotBlank)) { "Coverage exclude patterns must not be blank" }
    }
}

/** One-based line and zero-based column in an original source file. */
@Serializable
data class SourcePosition(
    val line: Int,
    val column: Int,
) {
    init {
        require(line > 0) { "Source line must be positive" }
        require(column >= 0) { "Source column must not be negative" }
    }
}

/** Half-open source range as represented by Istanbul. */
@Serializable
data class SourceRange(
    val start: SourcePosition,
    val end: SourcePosition,
) {
    init {
        require(end.line > start.line || end.line == start.line && end.column >= start.column) {
            "Source range end must not precede its start"
        }
    }
}

/** Hit count for one Istanbul statement. */
@Serializable
data class StatementCoverage(
    val statementId: Int,
    val location: SourceRange,
    val hits: Long,
) {
    init {
        require(statementId >= 0) { "Statement ID must not be negative" }
        require(hits >= 0) { "Statement hits must not be negative" }
    }
}

/** Hit count for one Istanbul function. */
@Serializable
data class FunctionCoverage(
    val functionId: Int,
    val name: String,
    val declaration: SourceRange,
    val body: SourceRange,
    val hits: Long,
) {
    init {
        require(functionId >= 0) { "Function ID must not be negative" }
        require(name.isNotBlank()) { "Function name must not be blank" }
        require(hits >= 0) { "Function hits must not be negative" }
    }
}

/** Hit count for one arm of an Istanbul branch. */
@Serializable
data class BranchArmCoverage(
    val location: SourceRange,
    val hits: Long,
) {
    init {
        require(hits >= 0) { "Branch arm hits must not be negative" }
    }
}

/** Hit counts for all arms belonging to one Istanbul branch. */
@Serializable
data class BranchCoverage(
    val branchId: Int,
    val type: String,
    val location: SourceRange,
    val arms: List<BranchArmCoverage>,
) {
    init {
        require(branchId >= 0) { "Branch ID must not be negative" }
        require(type.isNotBlank()) { "Branch type must not be blank" }
        require(arms.isNotEmpty()) { "Branch coverage requires at least one arm" }
    }
}

/** Source-mapped coverage for one original source file. */
@Serializable
data class SourceFileCoverage(
    val path: String,
    val statements: List<StatementCoverage>,
    val functions: List<FunctionCoverage>,
    val branches: List<BranchCoverage>,
) {
    init {
        require(path.isNotBlank()) { "Coverage source path must not be blank" }
    }
}

/** Records the collector inputs needed to interpret or reproduce one artifact. */
@Serializable
data class CoverageProvenance(
    val collector: CoverageCollectorIdentity,
    val runtimeId: String,
    val runtimeVersion: String,
    val sourceRoots: List<String>,
    val request: PropertyCoverageRequest,
) {
    init {
        require(runtimeId.isNotBlank()) { "Coverage runtime ID must not be blank" }
        require(runtimeVersion.isNotBlank()) { "Coverage runtime version must not be blank" }
        require(sourceRoots.isNotEmpty()) { "Coverage provenance requires source roots" }
        require(sourceRoots.all(String::isNotBlank)) { "Coverage source roots must not be blank" }
    }
}

/** Versioned per-property Node source coverage returned by a concrete backend. */
@Serializable
data class PropertyCoverageArtifact(
    val schemaVersion: Int = PROPERTY_COVERAGE_ARTIFACT_VERSION,
    val kind: CoverageArtifactKind = CoverageArtifactKind.NODE_SOURCE,
    val backendId: String,
    val backendVersion: String,
    val propertyId: PropertyId,
    val provenance: CoverageProvenance,
    val files: List<SourceFileCoverage>,
    val diagnostics: List<CoverageDiagnostic> = emptyList(),
) {
    init {
        require(schemaVersion == PROPERTY_COVERAGE_ARTIFACT_VERSION) {
            "Unsupported property coverage artifact version: $schemaVersion"
        }
        require(backendId.isNotBlank()) { "Coverage backend ID must not be blank" }
        require(backendVersion.isNotBlank()) { "Coverage backend version must not be blank" }
        require(files.map(SourceFileCoverage::path).distinct().size == files.size) {
            "Coverage artifact contains duplicate source paths"
        }
    }
}
