package org.usvm.ts.pbt.observation

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
)

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
    val status: String,
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
    val value: PropertyObservationValue,
)

/** One original callback invocation and its bounded point events. */
@Serializable
data class PropertyInvocationObservation(
    val invocationId: Int,
    val parentInvocationId: Int? = null,
    val callSite: PropertySourcePoint? = null,
    val origin: String,
    val phase: String,
    val input: PropertyObservationValue,
    val admission: String,
    val outcome: String,
    val points: List<PropertyPointEvent>,
)

/** Optional artifact: unknown phase is not independent random evidence. */
@Serializable
data class PropertyObservationArtifact(
    val propertyId: PropertyId,
    val runId: String,
    val verifiedSources: List<PropertyObservationSource>,
    val buildStatus: String,
    val invocations: List<PropertyInvocationObservation>,
    val droppedInvocations: Int,
    val droppedPoints: Int,
    val captureTimeMillis: Long,
)
