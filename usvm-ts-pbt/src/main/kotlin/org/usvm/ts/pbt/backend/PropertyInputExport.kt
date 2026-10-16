package org.usvm.ts.pbt.backend

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.observation.ObservationAdmission
import org.usvm.ts.pbt.observation.ObservationOutcome
import org.usvm.ts.pbt.observation.ObservationPhase
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.observation.PropertyObservationValue

/** Opt-in original inputs/outcomes without per-point events or execution trees. */
@Serializable
data class PropertyInputExportRequest(
    val sources: List<PropertyObservationSource>,
    val maxInvocations: Int = 20_000,
    val maxBytes: Int = DEFAULT_MAX_BYTES,
    val maxArrayElements: Int = MAX_INPUT_EXPORT_ARRAY_ELEMENTS,
) {
    init {
        require(maxInvocations in 1..MAX_INPUT_EXPORT_INVOCATIONS)
        require(maxBytes in MIN_INPUT_EXPORT_BYTES..MAX_BYTES)
        require(maxArrayElements in 1..MAX_INPUT_EXPORT_ARRAY_ELEMENTS)
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Int = 2 * 1024 * 1024
        const val MAX_BYTES: Int = 64 * 1024 * 1024
    }
}

/** The input snapshot precedes precondition and predicate mutations. */
@Serializable
data class PropertyInputRecord(
    val phase: ObservationPhase,
    val input: PropertyObservationValue,
    val admission: ObservationAdmission,
    val outcome: ObservationOutcome,
)

/** Bounded receipt: omissions, unsupported snapshots and pending outcomes never imply a complete trace. */
@Serializable
data class PropertyInputExportArtifact(
    val propertyId: PropertyId,
    val verifiedSources: List<PropertyObservationSource>,
    val records: List<PropertyInputRecord>,
    val droppedInvocations: Int,
    val unsupportedInputs: Int,
    val pendingInvocations: Int,
    val captureTimeMillis: Long,
) {
    init {
        require(droppedInvocations >= 0 && unsupportedInputs >= 0 && pendingInvocations >= 0)
        require(captureTimeMillis >= 0)
    }
}

internal const val MIN_INPUT_EXPORT_BYTES = 1024
internal const val MAX_INPUT_EXPORT_INVOCATIONS = 100_000
internal const val MAX_INPUT_EXPORT_ARRAY_ELEMENTS = 64
