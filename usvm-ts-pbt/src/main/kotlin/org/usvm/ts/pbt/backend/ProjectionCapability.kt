package org.usvm.ts.pbt.backend

enum class ProjectionLevel {
    EXACT,
    APPROXIMATE,
    UNSUPPORTED,
}

enum class PropertyCapabilityLevel {
    EXACT,
    APPROXIMATE,
    CONCRETE_ONLY,
    UNSUPPORTED,
}

data class CapabilityDiagnostic(
    val code: String,
    val message: String,
    val path: String,
)

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
