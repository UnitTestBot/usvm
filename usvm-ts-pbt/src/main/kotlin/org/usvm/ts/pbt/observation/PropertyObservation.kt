package org.usvm.ts.pbt.observation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertySourcePoint

/** One explicit point tied to an original assertion and operand. */
@Serializable
data class PropertyObservationPoint(
    val id: String,
    val assertionId: String,
    val operandId: String,
    val source: PropertySourcePoint,
    val callSite: PropertySourcePoint,
    val kind: ObservationPointKind,
)

@Serializable
enum class ObservationPointKind {
    @SerialName("argument") ARGUMENT,
    @SerialName("return") RETURN,
    @SerialName("intermediate") INTERMEDIATE,
    @SerialName("pre") PRE,
    @SerialName("post") POST,
}

@Serializable
enum class ObservationValueStatus {
    @SerialName("captured") CAPTURED,
    @SerialName("unsupported") UNSUPPORTED,
    @SerialName("truncated") TRUNCATED,
}

@Serializable
enum class ObservationOrigin {
    @SerialName("fast-check") FAST_CHECK,
}

@Serializable
enum class ObservationPhase {
    @SerialName("generation") GENERATION,
    @SerialName("explicit") EXPLICIT,
    @SerialName("shrink") SHRINK,
    @SerialName("replay") REPLAY,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
enum class ObservationAdmission {
    @SerialName("admitted") ADMITTED,
    @SerialName("rejected") REJECTED,
    @SerialName("threw") THREW,
}

@Serializable
enum class ObservationOutcome {
    @SerialName("holds") HOLDS,
    @SerialName("false") FALSE,
    @SerialName("threw") THREW,
    @SerialName("skipped") SKIPPED,
}

@Serializable
enum class ObservationBuildStatus {
    @SerialName("unverified") UNVERIFIED,
    @SerialName("verified") VERIFIED,
}

/** Exact bytes of a selected module, not a claim about transitive imports or transpilation. */
@Serializable
data class PropertyObservationSource(
    val module: String,
    val sha256: String,
)

/** Bounded opt-in capture for explicit points inside the original TypeScript callback. */
@Serializable
data class PropertyObservationRequest(
    val points: List<PropertyObservationPoint>,
    val sources: List<PropertyObservationSource>,
    val maxInvocations: Int = 64,
    val maxPointsPerInvocation: Int = 8,
    val maxArrayElements: Int = 16,
    val maxBytes: Int = 65_536,
)

/** A value unavailable to observation remains distinct from JavaScript null or undefined. */
@Serializable
data class PropertyObservationValue(
    val status: ObservationValueStatus,
    val value: JsConcreteValue? = null,
    val reason: String? = null,
)

/** One captured point, bound to the declared original assertion. */
@Serializable
data class PropertyPointEvent(
    val pointId: String,
    val assertionId: String,
    val operandId: String,
    val source: PropertySourcePoint,
    val callSite: PropertySourcePoint,
    val kind: ObservationPointKind,
    val occurrence: Int,
    val eventOrdinal: Int,
    val value: PropertyObservationValue,
)

/** One original callback invocation and its bounded point events. */
@Serializable
data class PropertyInvocationObservation(
    val invocationId: Int,
    val parentInvocationId: Int? = null,
    val callSite: PropertySourcePoint? = null,
    val origin: ObservationOrigin,
    val phase: ObservationPhase,
    val input: PropertyObservationValue,
    val admission: ObservationAdmission,
    val outcome: ObservationOutcome,
    val points: List<PropertyPointEvent>,
)

/** Optional artifact: unknown phase is not independent random evidence. */
@Serializable
data class PropertyObservationArtifact(
    val propertyId: PropertyId,
    val runId: String,
    val verifiedSources: List<PropertyObservationSource>,
    val buildStatus: ObservationBuildStatus,
    val invocations: List<PropertyInvocationObservation>,
    val droppedInvocations: Int,
    val droppedPoints: Int,
    val captureTimeMillis: Long,
)
