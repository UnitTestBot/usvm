package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyInputExportRequest
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.WitnessNativeReplay
import org.usvm.ts.pbt.backend.WitnessReduction
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource

@Serializable
internal data class FastCheckExecutionRequest(
    val manifest: PropertyManifest,
    val sourceRoots: List<String>,
    val tsconfig: String? = null,
    val seed: Int? = null,
    val replayPath: String? = null,
    val numRuns: Int,
    val timeoutMillis: Long,
    val generationTimeoutMillis: Long? = null,
    val examples: List<List<JsConcreteValue>> = emptyList(),
    val observationRequest: PropertyObservationRequest? = null,
    val inputExportRequest: PropertyInputExportRequest? = null,
    val witness: FastCheckWitnessOperation? = null,
    val admittedCoverage: Boolean = false,
    val exactReplay: Boolean = false,
    @Transient
    val coverageRequest: PropertyCoverageRequest? = null,
)

@Serializable
internal data class FastCheckExecutionWireResponse(
    val status: String,
    val result: PropertyRunResult? = null,
    val witnessReplay: WitnessNativeReplay? = null,
    val witnessReduction: WitnessReduction? = null,
    val witnessPropertyId: String? = null,
    val witnessTarget: WitnessTarget? = null,
    val diagnostics: List<FastCheckProtocolDiagnostic> = emptyList(),
)

@Serializable
internal data class FastCheckWitnessOperation(
    val mode: String,
    val inputs: List<JsConcreteValue>,
    val target: WitnessTarget,
    val sources: List<PropertyObservationSource>,
    val expected: WitnessNativeReplay? = null,
)

/** Infrastructure categories that remain distinct from a falsified property result. */
@Serializable
enum class BackendErrorKind {
    @SerialName("invalid-request")
    INVALID_REQUEST,

    @SerialName("entry-point")
    ENTRY_POINT,

    @SerialName("process-failure")
    PROCESS_FAILURE,

    @SerialName("protocol-error")
    PROTOCOL_ERROR,

    @SerialName("timeout")
    TIMEOUT,

    @SerialName("coverage")
    COVERAGE,
}

/** Typed failure at the concrete backend boundary. */
class PbtBackendException(
    val kind: BackendErrorKind,
    val code: String,
    message: String,
    val propertyId: String? = null,
    val path: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
