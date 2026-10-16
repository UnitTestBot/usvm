package org.usvm.ts.pbt.inference

import kotlinx.serialization.Serializable
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.PropertyInvocationObservation
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.observation.PropertyPointEvent

/** An explicitly selected pair of assertion operands, each evaluated once per invocation. */
data class ScalarObservationPair(
    val left: PropertyObservationPoint,
    val right: PropertyObservationPoint,
)

/** Numeric JavaScript comparisons; no mathematical-real or coercing comparison is inferred. */
@Serializable
enum class ScalarRelationTemplate {
    STRICT_EQUAL,
    LESS_OR_EQUAL,
}

/** Sample agreement is empirical evidence, never an invariant or permission to prune a path. */
enum class ScalarRelationStatus {
    SAMPLED,
    REFUTED,
    INSUFFICIENT,
    UNSUPPORTED,
}

/** Reasons an invocation or selection cannot supply complete numeric evidence. */
enum class ScalarRelationLimitation {
    INVALID_PAIR,
    SOURCE_MISMATCH,
    MISSING_POINT,
    REPEATED_POINT,
    POINT_IDENTITY_MISMATCH,
    UNSUPPORTED_VALUE,
    UNSUPPORTED_CONTEXT,
    INCOMPLETE_INVOCATION,
    INSUFFICIENT_DISTINCT_INPUTS,
    CONSTANT_OBSERVED_PAIR,
    INVOCATION_LIMIT,
    INCOMPLETE_CAPTURE,
}

/** Exact same-invocation point values, retaining origin, phase, input and event identities. */
data class ScalarRelationEvidence(
    val invocation: PropertyInvocationObservation,
    val left: PropertyPointEvent,
    val right: PropertyPointEvent,
    val holds: Boolean,
)

/** Evaluation of one template for one selected pair, including every observed contradiction. */
data class ScalarRelation(
    val pair: ScalarObservationPair,
    val template: ScalarRelationTemplate,
    val status: ScalarRelationStatus,
    val evidence: List<ScalarRelationEvidence>,
    val distinctGenerationInputs: Set<JsConcreteValue>,
    val limitations: Set<ScalarRelationLimitation>,
)

/** Bounded, property-scoped results preserving selected-source and executable-build provenance. */
data class ScalarRelationInference(
    val propertyId: PropertyId,
    val runId: String,
    val sources: List<PropertyObservationSource>,
    val buildStatus: ObservationBuildStatus,
    val relations: List<ScalarRelation>,
    val omittedPairs: Int,
    val omittedInvocations: Int,
    val droppedInvocations: Int,
    val droppedPoints: Int,
    val property: PropertyDefinition,
    /** Sampled entry relations already implied by declared numeric input support, retained for audit. */
    val domainImpliedRelations: List<ScalarRelation> = emptyList(),
)
