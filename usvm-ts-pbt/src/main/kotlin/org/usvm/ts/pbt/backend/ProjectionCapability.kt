package org.usvm.ts.pbt.backend

/** Describes how faithfully a backend can project a Kotlin property domain. */
enum class ProjectionLevel {
    /** Every value produced by the backend has the declared Kotlin domain semantics. */
    EXACT,

    /** The backend can run the domain, but its values differ from the declared semantics. */
    APPROXIMATE,

    /** The backend cannot project the domain. */
    UNSUPPORTED,
}

/** Describes which execution modes remain available for a complete property. */
enum class PropertyCapabilityLevel {
    /** Both concrete and symbolic projections preserve the declared property semantics. */
    EXACT,

    /** Both projections are available, but at least one is approximate. */
    APPROXIMATE,

    /** Concrete PBT execution is available, but symbolic execution is not. */
    CONCRETE_ONLY,

    /** Concrete PBT execution is unavailable, so the property cannot be executed. */
    UNSUPPORTED,
}

/**
 * Explains why a projection is not exact.
 *
 * @property code stable machine-readable diagnostic code
 * @property message human-readable description of the limitation
 * @property path location of the affected value in the property model
 */
data class CapabilityDiagnostic(
    val code: String,
    val message: String,
    val path: String,
)

/**
 * Reports whether one backend version can represent a property domain.
 *
 * @property backendId stable backend identifier
 * @property backendVersion backend version used to evaluate support
 * @property level semantic fidelity of the projection
 * @property diagnostics limitations that explain a non-exact [level]
 */
data class ProjectionCapability(
    val backendId: String,
    val backendVersion: String,
    val level: ProjectionLevel,
    val diagnostics: List<CapabilityDiagnostic> = emptyList(),
) {
    init {
        require(backendId.isNotBlank()) { "Backend ID must not be blank" }
        require(backendVersion.isNotBlank()) { "Backend version must not be blank" }
        require(level == ProjectionLevel.EXACT || diagnostics.isNotEmpty()) {
            "A non-exact projection requires at least one diagnostic"
        }
    }
}

/** Combines domain-level [capabilities] into one deterministic backend capability report. */
fun aggregateProjectionCapabilities(
    backendId: String,
    backendVersion: String,
    capabilities: List<ProjectionCapability>,
): ProjectionCapability {
    require(capabilities.all { it.backendId == backendId && it.backendVersion == backendVersion }) {
        "All projection capabilities must belong to $backendId $backendVersion"
    }
    val level = capabilities.maxOfOrNull { it.level } ?: ProjectionLevel.EXACT
    val diagnostics = capabilities
        .flatMap(ProjectionCapability::diagnostics)
        .sortedWith(compareBy(CapabilityDiagnostic::path, CapabilityDiagnostic::code))
    return ProjectionCapability(
        backendId = backendId,
        backendVersion = backendVersion,
        level = level,
        diagnostics = diagnostics,
    )
}

/** Derives the execution modes available when concrete and symbolic projections are considered together. */
fun classifyPropertyCapability(
    concrete: ProjectionCapability,
    symbolic: ProjectionCapability,
): PropertyCapabilityLevel = when {
    concrete.level == ProjectionLevel.UNSUPPORTED -> PropertyCapabilityLevel.UNSUPPORTED
    symbolic.level == ProjectionLevel.UNSUPPORTED -> PropertyCapabilityLevel.CONCRETE_ONLY
    concrete.level == ProjectionLevel.APPROXIMATE || symbolic.level == ProjectionLevel.APPROXIMATE ->
        PropertyCapabilityLevel.APPROXIMATE

    else -> PropertyCapabilityLevel.EXACT
}
