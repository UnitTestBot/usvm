package org.usvm.ts.pbt.mapping

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsType
import org.usvm.ts.pbt.backend.BranchArmCoverage
import org.usvm.ts.pbt.backend.BranchCoverage
import org.usvm.ts.pbt.backend.CoverageProvenance
import org.usvm.ts.pbt.backend.StatementCoverage
import org.usvm.ts.pbt.model.PropertyId

/** Classification shared by entry-point and source-coverage mapping results. */
enum class EtsMappingStatus {
    EXACT,
    AMBIGUOUS,
    UNMAPPED,
    UNSUPPORTED,
}

/** Source coordinate convention shared by TypeScript and native EtsIR origins. */
enum class EtsSourceCoordinateSystem {
    TYPESCRIPT_UTF16_ZERO_BASED_HALF_OPEN,
}

/** Ordered-successor convention used to bind binary backend branch arms. */
enum class EtsBranchSuccessorOrder {
    TRUE_FALSE,
}

/** Mapping-layer assumptions needed to interpret every target in one property artifact. */
data class EtsMappingProvenance(
    val sourceRoots: List<String>,
    val coordinates: EtsSourceCoordinateSystem,
    val branchSuccessorOrder: EtsBranchSuccessorOrder,
)

/** Stable reason explaining why a mapping could not produce one exact target. */
data class EtsMappingDiagnostic(
    val code: String,
    val message: String,
    val sourcePath: String? = null,
)

/** One mapping decision together with every EtsIR target selected by that decision. */
data class EtsMappingResult<T>(
    val status: EtsMappingStatus,
    val targets: List<T>,
    val diagnostics: List<EtsMappingDiagnostic> = emptyList(),
)

/** Explicit stack binding for the receiver reserved by the TypeScript interpreter. */
data class EtsReceiverBinding(
    val stackSlot: Int,
    val type: EtsType,
)

/** Connects one ordered property input to the corresponding EtsIR parameter and stack slot. */
data class EtsInputBinding(
    val propertyInputName: String,
    val parameter: EtsMethodParameter,
    val stackSlot: Int,
)

/** Identifies the value produced when the mapped EtsIR method returns. */
data class EtsResultBinding(
    val type: EtsType,
)

/** EtsIR value bindings required to execute one property entry point symbolically. */
data class EtsEntryPointBindings(
    val receiver: EtsReceiverBinding,
    val inputs: List<EtsInputBinding>,
    val result: EtsResultBinding,
)

/** Resolved EtsIR method and its property-facing symbolic bindings. */
data class EtsEntryPointTarget(
    val method: EtsMethod,
    val bindings: EtsEntryPointBindings,
)

/** Zero-based TypeScript position with its UTF-16 source-file offset. */
data class NormalizedSourcePosition(
    val line: Int,
    val column: Int,
    val offset: Int,
)

/** Canonical source path and half-open zero-based UTF-16 range. */
data class NormalizedSourceRange(
    val path: String,
    val start: NormalizedSourcePosition,
    val end: NormalizedSourcePosition,
)

/** One EtsIR statement selected for a backend-neutral statement coverage location. */
data class EtsStatementTarget(
    val statement: EtsStmt,
)

/** Source statement coverage paired with its normalized location and EtsIR mapping decision. */
data class EtsStatementCoverageMapping(
    val coverage: StatementCoverage,
    val location: NormalizedSourceRange?,
    val mapping: EtsMappingResult<EtsStatementTarget>,
)

/** EtsIR conditional selected for one backend-neutral branch location. */
data class EtsBranchTarget(
    val statement: EtsIfStmt,
)

/** One explicit EtsIR control-flow edge associated with a covered branch arm. */
data class EtsBranchArmTarget(
    val condition: EtsIfStmt,
    val outcome: Boolean,
    val successor: EtsStmt,
)

/** One backend branch arm paired with its normalized location and EtsIR edge mapping. */
data class EtsBranchArmCoverageMapping(
    val coverage: BranchArmCoverage,
    val location: NormalizedSourceRange?,
    val mapping: EtsMappingResult<EtsBranchArmTarget>,
)

/** Backend branch coverage paired with its EtsIR condition and ordered arm mappings. */
data class EtsBranchCoverageMapping(
    val coverage: BranchCoverage,
    val location: NormalizedSourceRange?,
    val mapping: EtsMappingResult<EtsBranchTarget>,
    val arms: List<EtsBranchArmCoverageMapping>,
)

/** Mapping state for optional backend-neutral source coverage. */
data class EtsCoverageMapping(
    val status: EtsMappingStatus,
    val backendProvenance: CoverageProvenance?,
    val statements: List<EtsStatementCoverageMapping> = emptyList(),
    val branches: List<EtsBranchCoverageMapping> = emptyList(),
    val diagnostics: List<EtsMappingDiagnostic>,
)

/** Kotlin-owned mapping artifact for one analyzed property. */
data class PropertyEtsMappingArtifact(
    val propertyId: PropertyId,
    val provenance: EtsMappingProvenance,
    val predicate: EtsMappingResult<EtsEntryPointTarget>,
    val precondition: EtsMappingResult<EtsEntryPointTarget>?,
    val coverage: EtsCoverageMapping,
)
