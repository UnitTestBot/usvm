package org.usvm.ts.pbt.inference

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.ObservationAdmission
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.ObservationOrigin
import org.usvm.ts.pbt.observation.ObservationOutcome
import org.usvm.ts.pbt.observation.ObservationPhase
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.ObservationValueStatus
import org.usvm.ts.pbt.observation.PropertyInvocationObservation
import org.usvm.ts.pbt.observation.PropertyObservationArtifact
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.observation.PropertyObservationValue
import org.usvm.ts.pbt.observation.PropertyPointEvent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainImpliedScalarRelationTest {
    @Test
    fun `separated inclusive integer domains remove only the already guaranteed order`() {
        val property = property(IntegerDomain(min = 0, max = 1), IntegerDomain(min = 2, max = 3))
        val samples = listOf(sample(id = 0, left = 0.0, right = 2.0), sample(id = 1, left = 1.0, right = 3.0))

        val result = infer(property, samples)

        val implied = result.domainImpliedRelations.single()
        assertEquals(ScalarRelationTemplate.LESS_OR_EQUAL, implied.template)
        assertEquals(ScalarRelationStatus.SAMPLED, implied.status)
        assertEquals(samples, implied.evidence.map { it.invocation })
        assertEquals(2, implied.distinctGenerationInputs.size)
        assertEquals(ScalarRelationStatus.REFUTED, result.relations.single().status)
        assertEquals(SOURCES, result.sources)
        assertEquals(property, result.property)
        val selected = selectScalarGuidance(result)
        val excludedOrder = selected.excluded.single { it.relation == implied }
        assertEquals(ScalarGuidanceExclusionReason.DOMAIN_IMPLIED, excludedOrder.reason)
    }

    @Test
    fun `touching interval endpoints imply order but overlapping intervals do not`() {
        val samples = listOf(sample(id = 0, left = 0.0, right = 2.0), sample(id = 1, left = 1.0, right = 3.0))
        val touching = property(IntegerDomain(min = 0, max = 2), IntegerDomain(min = 2, max = 3))
        val overlapping = property(IntegerDomain(min = 0, max = 3), IntegerDomain(min = 2, max = 3))

        val touchingResult = infer(touching, samples)
        val overlappingResult = infer(overlapping, samples)

        assertEquals(1, touchingResult.domainImpliedRelations.size)
        assertTrue(overlappingResult.domainImpliedRelations.isEmpty())
        assertEquals(ScalarRelationStatus.SAMPLED, overlappingResult.order().status)
    }

    @Test
    fun `same original numeric input makes both templates redundant when NaN is excluded`() {
        val right = RIGHT.copy(inputIndex = 0)
        val samples = listOf(sample(id = 0, left = 0.0, right = 0.0), sample(id = 1, left = 1.0, right = 1.0))
        val aliasSamples = samples.map { invocation ->
            invocation.copy(points = listOf(invocation.points.first(), invocation.points.last().copy(inputIndex = 0)))
        }
        val property = property(IntegerDomain(min = 0, max = 1), IntegerDomain(min = 0, max = 1))

        val result = infer(property, aliasSamples, pair = ScalarObservationPair(LEFT, right))

        assertTrue(result.relations.isEmpty())
        assertEquals(ScalarRelationTemplate.entries.toSet(), result.domainImpliedRelations.map { it.template }.toSet())
        assertTrue(result.domainImpliedRelations.all { it.status == ScalarRelationStatus.SAMPLED })
    }

    @Test
    fun `guidance reuses domain facts and keeps NaN capable reflexive comparisons as unknown candidates`() {
        val pair = ScalarObservationPair(LEFT, RIGHT.copy(inputIndex = 0))
        val samples = listOf(sample(id = 0, left = 0.0, right = 0.0), sample(id = 1, left = 1.0, right = 1.0))
        val snapshots = samples.map { invocation ->
            invocation.copy(points = listOf(invocation.points.first(), invocation.points.last().copy(inputIndex = 0)))
        }
        val integers = property(IntegerDomain(min = 0, max = 1), IntegerDomain(min = 0, max = 1))
        val numbers = property(NumberDomain(), IntegerDomain(min = 0, max = 1))
        val tautologies = infer(integers, snapshots, pair)
        val nanCapable = infer(numbers, snapshots, pair)

        val excluded = selectScalarGuidance(tautologies)
        val retained = selectScalarGuidance(nanCapable)

        assertTrue(excluded.candidates.isEmpty())
        assertEquals(tautologies.domainImpliedRelations, excluded.excluded.map { it.relation })
        assertTrue(excluded.excluded.all { it.reason == ScalarGuidanceExclusionReason.TAUTOLOGY })
        assertEquals(nanCapable.relations, retained.candidates.map { it.relation })
        assertTrue(retained.candidates.all { it.relevance == ScalarGuidanceRelevance.UNKNOWN })
        assertTrue(retained.excluded.isEmpty())
    }

    @Test
    fun `ordinary argument labels never establish input identity even when every sample matches`() {
        val left = LEFT.copy(kind = ObservationPointKind.ARGUMENT)
        val right = RIGHT.copy(kind = ObservationPointKind.ARGUMENT)
        val samples = listOf(sample(id = 0, left = 0.0, right = 2.0), sample(id = 1, left = 1.0, right = 3.0))
        val ordinary = samples.map { invocation ->
            invocation.copy(points = invocation.points.map { it.copy(kind = ObservationPointKind.ARGUMENT) })
        }
        val property = property(IntegerDomain(min = 0, max = 1), IntegerDomain(min = 2, max = 3))

        val result = infer(property, ordinary, pair = ScalarObservationPair(left, right))

        assertTrue(result.domainImpliedRelations.isEmpty())
        assertEquals(ScalarRelationStatus.SAMPLED, result.order().status)
    }

    @Test
    fun `changed snapshots invalid indices and observations outside domains are retained`() {
        val first = sample(id = 0, left = 0.0, right = 2.0)
        val second = sample(id = 1, left = 1.0, right = 3.0)
        val property = property(IntegerDomain(min = 0, max = 1), IntegerDomain(min = 2, max = 3))
        val differentInput = first.copy(input = captured(inputs(left = 1.0, right = 2.0)))
        val snapshotResult = infer(property, listOf(differentInput, second))
        val outsideDomain = infer(property, listOf(first, sample(id = 1, left = 2.0, right = 3.0)))
        val invalidPoint = LEFT.copy(inputIndex = 99)
        val invalidSamples = listOf(first, second).map { invocation ->
            invocation.copy(points = listOf(invocation.points.first().copy(inputIndex = 99), invocation.points.last()))
        }
        val invalidIndex = infer(property, invalidSamples, pair = ScalarObservationPair(invalidPoint, RIGHT))

        for (result in listOf(snapshotResult, outsideDomain, invalidIndex)) {
            assertTrue(result.domainImpliedRelations.isEmpty())
            assertEquals(ScalarRelationStatus.SAMPLED, result.order().status)
        }
    }

    @Test
    fun `NaN enabled domains cannot guarantee either comparison`() {
        val property = property(NumberDomain(), IntegerDomain(min = 2, max = 3))
        val samples = listOf(sample(id = 0, left = 0.0, right = 2.0), sample(id = 1, left = 1.0, right = 3.0))

        val result = infer(property, samples)

        assertTrue(result.domainImpliedRelations.isEmpty())
        assertEquals(ScalarRelationStatus.SAMPLED, result.order().status)
    }

    @Test
    fun `NaN allowed on an aliased original input prevents reflexivity filtering`() {
        val property = property(NumberDomain(), NumberDomain())
        val right = RIGHT.copy(inputIndex = 0)
        val samples = listOf(sample(id = 0, left = 0.0, right = 0.0), sample(id = 1, left = 1.0, right = 1.0))
        val aliases = samples.map { invocation ->
            invocation.copy(points = listOf(invocation.points.first(), invocation.points.last().copy(inputIndex = 0)))
        }

        val result = infer(property, aliases, pair = ScalarObservationPair(LEFT, right))

        assertTrue(result.domainImpliedRelations.isEmpty())
        assertTrue(result.relations.all { it.status == ScalarRelationStatus.SAMPLED })
    }

    @Test
    fun `binary64 bounds signed zero and infinities use numeric JavaScript ordering`() {
        val negativeInfinity = Double.NEGATIVE_INFINITY
        val positiveInfinity = Double.POSITIVE_INFINITY
        val exactBoundary = 9_007_199_254_740_992.0
        val roundedBoundary = 9_007_199_254_740_993.0
        val cases = listOf(
            property(numberDomain(min = -0.0, max = 0.0), numberDomain(min = 0.0, max = 1.0)) to listOf(
                sample(id = 0, left = -0.0, right = 0.0),
                sample(id = 1, left = 0.0, right = 1.0),
            ),
            property(
                numberDomain(min = negativeInfinity, max = -1.0),
                numberDomain(min = 0.0, max = positiveInfinity),
            ) to listOf(
                sample(id = 0, left = negativeInfinity, right = 0.0),
                sample(id = 1, left = -1.0, right = positiveInfinity),
            ),
            property(
                numberDomain(min = exactBoundary, max = roundedBoundary),
                numberDomain(min = exactBoundary, max = positiveInfinity),
            ) to listOf(
                sample(id = 0, left = exactBoundary, right = exactBoundary),
                sample(id = 1, left = exactBoundary, right = positiveInfinity),
            ),
        )

        for ((property, samples) in cases) {
            val result = infer(property, samples)

            assertEquals(ScalarRelationTemplate.LESS_OR_EQUAL, result.domainImpliedRelations.single().template)
        }
    }

    @Test
    fun `numeric constants participate in ordering but optional and malformed supports do not`() {
        val samples = listOf(sample(id = 0, left = 0.0, right = 0.0), sample(id = 1, left = 0.0, right = 1.0))
        val constant = ConstantDomain(JsConcreteValue.number(0.0))
        val right = IntegerDomain(min = 0, max = 1)
        val numeric = property(constant, right)
        val optional = property(OptionalDomain(constant), right)
        val invalid = property(IntegerDomain(min = 1, max = 0), right)

        assertEquals(1, infer(numeric, samples).domainImpliedRelations.size)
        assertTrue(infer(optional, samples).domainImpliedRelations.isEmpty())
        assertTrue(infer(invalid, samples).domainImpliedRelations.isEmpty())
    }

    @Test
    fun `contradictions and insufficient or incomplete evidence never disappear into domain filtering`() {
        val property = property(IntegerDomain(min = 0, max = 1), IntegerDomain(min = 2, max = 3))
        val first = sample(id = 0, left = 0.0, right = 2.0)
        val second = sample(id = 1, left = 1.0, right = 3.0)
        val refuted = infer(property, listOf(first, second, sample(id = 2, left = 3.0, right = 2.0)))
        val insufficient = infer(property, listOf(first))
        val dropped = artifact(listOf(first, second)).copy(droppedInvocations = 1)
        val bounded = inferScalarRelations(property, dropped, listOf(PAIR))

        assertEquals(ScalarRelationStatus.REFUTED, refuted.order().status)
        assertEquals(ScalarRelationStatus.INSUFFICIENT, insufficient.order().status)
        assertTrue(bounded.domainImpliedRelations.isNotEmpty())
        val warningsPreserved = bounded.domainImpliedRelations.all {
            ScalarRelationLimitation.INCOMPLETE_CAPTURE in it.limitations
        }
        assertTrue(warningsPreserved)
        assertTrue(listOf(refuted, insufficient).all { it.domainImpliedRelations.isEmpty() })
    }

    @Test
    fun `unrepresented preconditions and original properties do not remove empirical relations`() {
        val property = property(IntegerDomain(min = 0, max = 3), IntegerDomain(min = 0, max = 3)).copy(
            precondition = TypeScriptEntryPoint(module = MODULE, exportName = "ordered"),
        )
        val samples = listOf(sample(id = 0, left = 0.0, right = 0.0), sample(id = 1, left = 1.0, right = 1.0))

        val result = infer(property, samples)

        assertTrue(result.domainImpliedRelations.isEmpty())
        assertTrue(result.relations.all { it.status == ScalarRelationStatus.SAMPLED })
    }

    private fun infer(
        property: PropertyDefinition,
        samples: List<PropertyInvocationObservation>,
        pair: ScalarObservationPair = PAIR,
    ): ScalarRelationInference = inferScalarRelations(property, artifact(samples), listOf(pair))

    private fun ScalarRelationInference.order(): ScalarRelation =
        relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }

    private fun property(left: PropertyDomain, right: PropertyDomain): PropertyDefinition = PropertyDefinition(
        id = ID,
        inputs = listOf(PropertyInput(name = "left", domain = left), PropertyInput(name = "right", domain = right)),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = "holds"),
        assertions = listOf(
            PropertyAssertion(
                id = "check",
                source = CALL,
                testedCall = CALL,
                operands = listOf(
                    PropertyOperand(id = LEFT.operandId, source = LEFT.source),
                    PropertyOperand(id = RIGHT.operandId, source = RIGHT.source),
                ),
            ),
        ),
    )

    private fun sample(id: Int, left: Double, right: Double): PropertyInvocationObservation =
        PropertyInvocationObservation(
            invocationId = id,
            callSite = CALL,
            origin = ObservationOrigin.FAST_CHECK,
            phase = ObservationPhase.GENERATION,
            input = captured(inputs(left, right)),
            admission = ObservationAdmission.ADMITTED,
            outcome = ObservationOutcome.HOLDS,
            points = listOf(event(LEFT, left, ordinal = 0), event(RIGHT, right, ordinal = 1)),
        )

    private fun event(point: PropertyObservationPoint, number: Double, ordinal: Int): PropertyPointEvent =
        PropertyPointEvent(
            pointId = point.id,
            assertionId = point.assertionId,
            operandId = point.operandId,
            source = point.source,
            callSite = point.callSite,
            kind = point.kind,
            inputIndex = point.inputIndex,
            occurrence = 0,
            eventOrdinal = ordinal,
            value = captured(JsConcreteValue.number(number)),
        )

    private fun inputs(left: Double, right: Double): JsConcreteValue =
        JsConcreteValue.Array(listOf(JsConcreteValue.number(left), JsConcreteValue.number(right)))

    private fun captured(value: JsConcreteValue): PropertyObservationValue =
        PropertyObservationValue(status = ObservationValueStatus.CAPTURED, value = value)

    private fun artifact(samples: List<PropertyInvocationObservation>) = PropertyObservationArtifact(
        propertyId = ID,
        runId = "domain-filter",
        verifiedSources = SOURCES,
        buildStatus = ObservationBuildStatus.UNVERIFIED,
        invocations = samples,
        droppedInvocations = 0,
        droppedPoints = 0,
        captureTimeMillis = 0,
    )

    private fun numberDomain(min: Double, max: Double) = NumberDomain(
        min = JsNumber.fromDouble(min),
        max = JsNumber.fromDouble(max),
        allowNaN = false,
    )

    private companion object {
        const val MODULE = "property.ts"
        val ID = PropertyId("test.domain-filter")
        val CALL = PropertySourcePoint(module = MODULE, line = 1, column = 1)
        val LEFT = PropertyObservationPoint(
            id = "left",
            assertionId = "check",
            operandId = "left",
            source = CALL,
            callSite = CALL,
            kind = ObservationPointKind.ENTRY_ARGUMENT,
            inputIndex = 0,
        )
        val RIGHT = LEFT.copy(id = "right", operandId = "right", inputIndex = 1)
        val PAIR = ScalarObservationPair(LEFT, RIGHT)
        val SOURCES = listOf(PropertyObservationSource(module = MODULE, sha256 = "a".repeat(64)))
    }
}
