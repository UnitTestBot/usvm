package org.usvm.ts.pbt.inference

import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.observation.ObservationAdmission
import org.usvm.ts.pbt.observation.ObservationOrigin
import org.usvm.ts.pbt.observation.ObservationOutcome
import org.usvm.ts.pbt.observation.ObservationPhase
import org.usvm.ts.pbt.observation.ObservationValueStatus
import org.usvm.ts.pbt.observation.PropertyInvocationObservation
import org.usvm.ts.pbt.observation.PropertyObservationArtifact
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyPointEvent

/**
 * Evaluates only explicitly selected assertion operand pairs, in selection order.
 *
 * At least two distinct original admitted generation inputs and two observed value pairs are
 * required for sample agreement. Replay, shrinking and solver evidence can refute a relation,
 * but cannot increase independent support. Repeated points and nested contexts are unsupported.
 * Recording limits bound the sample, not the PBT executions: only complete recorded pairs supply
 * evidence. Omission and truncation warnings remain visible without claiming agreement on lost executions.
 *
 * Only selected module hashes are available for provenance; a declared predicate source hash
 * is checked when present. Executable build status is carried through without promotion.
 *
 * Sampled relations already implied by declared numeric input domains are separated only for
 * adapter-owned entry arguments. Arbitrary point labels never establish original-input identity.
 * Original formulas and preconditions are not inspected; retained relations remain empirical.
 */
fun inferScalarRelations(
    property: PropertyDefinition,
    observations: PropertyObservationArtifact,
    pairs: List<ScalarObservationPair>,
    maxPairs: Int = 16,
    maxInvocations: Int = 64,
): ScalarRelationInference {
    require(maxPairs > 0) { "maxPairs must be positive" }
    require(maxInvocations > 0) { "maxInvocations must be positive" }
    require(property.id == observations.propertyId) { "Observation property does not match the selected property" }

    val distinctPairs = pairs.distinct()
    val selectedPairs = distinctPairs.take(maxPairs)
    val invocations = observations.invocations.take(maxInvocations)
    val omittedInvocations = observations.invocations.size - invocations.size
    val relations = selectedPairs.flatMap { pair ->
        evaluatePair(property, observations, pair, invocations, omittedInvocations)
    }

    val (domainImplied, retained) = relations.partition { relation -> relation.isDomainImplied(property) }

    return ScalarRelationInference(
        propertyId = property.id,
        runId = observations.runId,
        sources = observations.verifiedSources,
        buildStatus = observations.buildStatus,
        relations = retained,
        omittedPairs = distinctPairs.size - selectedPairs.size,
        omittedInvocations = omittedInvocations,
        droppedInvocations = observations.droppedInvocations,
        droppedPoints = observations.droppedPoints,
        property = property,
        domainImpliedRelations = domainImplied,
    )
}

private fun evaluatePair(
    property: PropertyDefinition,
    observations: PropertyObservationArtifact,
    pair: ScalarObservationPair,
    invocations: List<PropertyInvocationObservation>,
    omittedInvocations: Int,
): List<ScalarRelation> {
    val limitations = linkedSetOf<ScalarRelationLimitation>()
    val validPair = pair.isAssertionPair(property)
    val verifiedModules = observations.verifiedSources.map { it.module }.toSet()
    val selectedModules = setOf(pair.left.source.module, pair.right.source.module, pair.left.callSite.module)
    val expectedSourceHash = property.sourceIdentity?.sourceSha256
    val predicateSource = observations.verifiedSources.singleOrNull { it.module == property.predicate.module }
    val propertySourceMatches = expectedSourceHash == null || predicateSource?.sha256 == expectedSourceHash
    val sourcesPresent = verifiedModules.containsAll(selectedModules) && propertySourceMatches

    if (!validPair) limitations += ScalarRelationLimitation.INVALID_PAIR
    if (!sourcesPresent) limitations += ScalarRelationLimitation.SOURCE_MISMATCH
    if (omittedInvocations > 0) limitations += ScalarRelationLimitation.INVOCATION_LIMIT
    if (observations.droppedInvocations > 0 || observations.droppedPoints > 0) {
        limitations += ScalarRelationLimitation.INCOMPLETE_CAPTURE
    }

    val unmarkedInvocation = observations.invocations.any { it.pointsTruncated == null }
    val markedTruncation = observations.invocations.any { it.pointsTruncated == true }
    val unattributedPointDrops = observations.droppedPoints > 0 && (unmarkedInvocation || !markedTruncation)
    if (unattributedPointDrops) limitations += ScalarRelationLimitation.UNATTRIBUTED_POINT_DROPS

    val matched = if (validPair && sourcesPresent) {
        invocations.mapNotNull { invocation -> numericPair(invocation, pair, limitations) }
    } else {
        emptyList()
    }
    val generationEvidence = matched.filter { (invocation, _, _) -> invocation.isIndependentGeneration() }
    val distinctInputs = generationEvidence.mapNotNull { (invocation, _, _) -> invocation.input.value }.toSet()
    val distinctValues = generationEvidence.map { (_, left, right) ->
        left.numericSupportValue() to right.numericSupportValue()
    }.toSet()

    if (distinctInputs.size < MINIMUM_DISTINCT_SUPPORT) {
        limitations += ScalarRelationLimitation.INSUFFICIENT_DISTINCT_INPUTS
    }
    if (generationEvidence.isNotEmpty() && distinctValues.size < MINIMUM_DISTINCT_SUPPORT) {
        limitations += ScalarRelationLimitation.CONSTANT_OBSERVED_PAIR
    }

    return ScalarRelationTemplate.entries.map { template ->
        val evidence = matched.map { (invocation, left, right) ->
            val leftNumber = (left.value.value as JsConcreteValue.Number).toDouble()
            val rightNumber = (right.value.value as JsConcreteValue.Number).toDouble()
            val holds = when (template) {
                ScalarRelationTemplate.STRICT_EQUAL -> leftNumber == rightNumber
                ScalarRelationTemplate.LESS_OR_EQUAL -> leftNumber <= rightNumber
            }

            ScalarRelationEvidence(invocation, left, right, holds)
        }
        val refuted = evidence.any { !it.holds }
        val unsupported = limitations.any { it in UNSUPPORTED_LIMITATIONS }
        val status = when {
            refuted -> ScalarRelationStatus.REFUTED
            matched.isEmpty() && unsupported -> ScalarRelationStatus.UNSUPPORTED
            limitations.any { !it.isSamplingWarning } -> ScalarRelationStatus.INSUFFICIENT
            else -> ScalarRelationStatus.SAMPLED
        }

        ScalarRelation(pair, template, status, evidence, distinctInputs, limitations.toSet())
    }
}

private fun numericPair(
    invocation: PropertyInvocationObservation,
    pair: ScalarObservationPair,
    limitations: MutableSet<ScalarRelationLimitation>,
): Triple<PropertyInvocationObservation, PropertyPointEvent, PropertyPointEvent>? {
    if (invocation.admission != ObservationAdmission.ADMITTED) return null

    val complete = invocation.outcome in COMPLETE_OUTCOMES
    if (!complete) {
        limitations += ScalarRelationLimitation.INCOMPLETE_INVOCATION
        return null
    }
    val supportedContext = invocation.parentInvocationId == null && invocation.callSite == pair.left.callSite
    if (!supportedContext) {
        limitations += ScalarRelationLimitation.UNSUPPORTED_CONTEXT
        return null
    }

    val truncated = invocation.pointsTruncated == true || invocation.input.status == ObservationValueStatus.TRUNCATED
    val leftEvents = invocation.points.filter { it.pointId == pair.left.id }
    val rightEvents = invocation.points.filter { it.pointId == pair.right.id }
    if (leftEvents.size > 1 || rightEvents.size > 1) {
        limitations += ScalarRelationLimitation.REPEATED_POINT
        return null
    }

    val left = leftEvents.singleOrNull()
    val right = rightEvents.singleOrNull()
    val identitiesMatch = (left == null || left.matches(pair.left)) && (right == null || right.matches(pair.right))
    if (!identitiesMatch) {
        limitations += ScalarRelationLimitation.POINT_IDENTITY_MISMATCH
        return null
    }
    val malformedValues = listOfNotNull(left, right).any { event ->
        !event.isNumber() && !(truncated && event.value.status == ObservationValueStatus.TRUNCATED)
    }
    if (malformedValues) {
        limitations += ScalarRelationLimitation.UNSUPPORTED_VALUE
        return null
    }
    if (truncated) {
        limitations += ScalarRelationLimitation.TRUNCATED_INVOCATION
        return null
    }
    if (left == null || right == null) {
        limitations += ScalarRelationLimitation.MISSING_POINT
        return null
    }
    val inputCaptured = invocation.input.status == ObservationValueStatus.CAPTURED &&
        invocation.input.value is JsConcreteValue.Array
    if (!inputCaptured) {
        limitations += ScalarRelationLimitation.UNSUPPORTED_VALUE
        return null
    }

    return Triple(invocation, left, right)
}

private fun ScalarObservationPair.isAssertionPair(property: PropertyDefinition): Boolean {
    if (left.id == right.id || left.operandId == right.operandId) return false
    if (left.assertionId != right.assertionId || left.callSite != right.callSite) return false

    val assertion = property.assertions.singleOrNull { it.id == left.assertionId } ?: return false
    val leftOperand = assertion.operands.singleOrNull { it.id == left.operandId }
    val rightOperand = assertion.operands.singleOrNull { it.id == right.operandId }

    return assertion.testedCall == left.callSite &&
        leftOperand?.source == left.source && rightOperand?.source == right.source
}

internal fun PropertyPointEvent.matches(point: PropertyObservationPoint): Boolean =
    assertionId == point.assertionId && operandId == point.operandId && source == point.source &&
        callSite == point.callSite && kind == point.kind && inputIndex == point.inputIndex && occurrence == 0 &&
        compilerLocal == point.compilerLocal

private fun PropertyPointEvent.isNumber(): Boolean {
    val number = value.value as? JsConcreteValue.Number ?: return false

    return value.status == ObservationValueStatus.CAPTURED && number.number.hasValidEncoding()
}

private fun PropertyPointEvent.numericSupportValue(): Double {
    val number = (value.value as JsConcreteValue.Number).toDouble()

    // Strict numeric equality treats both signed zeros as the same value.
    return if (number == 0.0) 0.0 else number
}

private fun PropertyInvocationObservation.isIndependentGeneration(): Boolean =
    origin == ObservationOrigin.FAST_CHECK && phase == ObservationPhase.GENERATION &&
        input.status == ObservationValueStatus.CAPTURED && input.value != null

private const val MINIMUM_DISTINCT_SUPPORT = 2
private val COMPLETE_OUTCOMES = setOf(ObservationOutcome.HOLDS, ObservationOutcome.FALSE, ObservationOutcome.THREW)

private val UNSUPPORTED_LIMITATIONS = setOf(
    ScalarRelationLimitation.INVALID_PAIR,
    ScalarRelationLimitation.SOURCE_MISMATCH,
    ScalarRelationLimitation.MISSING_POINT,
    ScalarRelationLimitation.REPEATED_POINT,
    ScalarRelationLimitation.POINT_IDENTITY_MISMATCH,
    ScalarRelationLimitation.UNSUPPORTED_VALUE,
    ScalarRelationLimitation.UNSUPPORTED_CONTEXT,
)
