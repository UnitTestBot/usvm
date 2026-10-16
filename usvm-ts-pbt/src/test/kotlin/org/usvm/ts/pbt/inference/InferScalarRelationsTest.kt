package org.usvm.ts.pbt.inference

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.JsNumberKind
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourceIdentity
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InferScalarRelationsTest {
    @Test
    fun `two and four value supports yield empirical relations without a large minimum`() {
        for (supportSize in listOf(2, 4)) {
            val samples = (0 until supportSize).map { index -> invocation(index, index.toDouble(), index.toDouble()) }

            val result = infer(samples)

            assertTrue(result.relations.all { it.status == ScalarRelationStatus.SAMPLED })
            assertEquals(supportSize, result.relations.first().distinctGenerationInputs.size)
            assertEquals(ObservationBuildStatus.UNVERIFIED, result.buildStatus)
            assertEquals(SOURCES, result.sources)
        }
    }

    @Test
    fun `duplicate generation inputs and replay shrink solver samples do not manufacture support`() {
        val sample = invocation(id = 0, left = 0.0, right = 0.0)
        val samples = listOf(
            sample,
            sample.copy(invocationId = 1),
            invocation(id = 2, left = 1.0, right = 1.0).copy(phase = ObservationPhase.REPLAY),
            invocation(id = 3, left = 2.0, right = 2.0).copy(phase = ObservationPhase.SHRINK),
            invocation(id = 4, left = 3.0, right = 3.0).copy(origin = ObservationOrigin.SOLVER),
            invocation(id = 5, left = 4.0, right = 4.0).copy(phase = ObservationPhase.EXPLICIT),
            invocation(id = 6, left = 5.0, right = 5.0).copy(phase = ObservationPhase.UNKNOWN),
        )

        val result = infer(samples).relations.first()

        assertEquals(ScalarRelationStatus.INSUFFICIENT, result.status)
        assertEquals(1, result.distinctGenerationInputs.size)
        assertEquals(7, result.evidence.size)
        assertTrue(ScalarRelationLimitation.INSUFFICIENT_DISTINCT_INPUTS in result.limitations)
    }

    @Test
    fun `later correlated witnesses retain the refuted relation and exact evidence`() {
        val original = listOf(invocation(id = 0, left = 0.0, right = 0.0), invocation(id = 1, left = 1.0, right = 1.0))

        for (phase in listOf(ObservationPhase.REPLAY, ObservationPhase.SHRINK)) {
            val witness = invocation(id = 2, left = 1.0, right = 0.0).copy(phase = phase)
            val result = infer(original + witness)

            assertTrue(result.relations.all { it.status == ScalarRelationStatus.REFUTED })
            assertEquals(2, result.relations.first().distinctGenerationInputs.size)
            val contradiction = result.relations.first().evidence.single { !it.holds }
            assertEquals(witness, contradiction.invocation)
            assertEquals(witness.points.first().eventOrdinal, contradiction.left.eventOrdinal)
        }

        val solver = invocation(id = 3, left = 1.0, right = 0.0).copy(origin = ObservationOrigin.SOLVER)
        assertTrue(infer(original + solver).relations.all { it.status == ScalarRelationStatus.REFUTED })
    }

    @Test
    fun `rejected inputs neither support nor refute a relation`() {
        val rejected = invocation(id = 2, left = 1.0, right = 0.0).copy(admission = ObservationAdmission.REJECTED)
        val original = listOf(invocation(id = 0, left = 0.0, right = 0.0), invocation(id = 1, left = 1.0, right = 1.0))

        val result = infer(original + rejected)

        assertTrue(result.relations.all { it.status == ScalarRelationStatus.SAMPLED })
        assertEquals(2, result.relations.first().evidence.size)
    }

    @Test
    fun `points from different invocations are never paired`() {
        val leftOnly = invocation(id = 0, left = 0.0, right = 0.0).let { it.copy(points = it.points.take(1)) }
        val rightOnly = invocation(id = 1, left = 1.0, right = 1.0).let { it.copy(points = it.points.drop(1)) }

        val result = infer(listOf(leftOnly, rightOnly)).relations.first()

        assertEquals(ScalarRelationStatus.UNSUPPORTED, result.status)
        assertTrue(result.evidence.isEmpty())
        assertTrue(ScalarRelationLimitation.MISSING_POINT in result.limitations)
    }

    @Test
    fun `rare contexts keep independent evidence separate`() {
        val first = invocation(id = 0, left = 0.0, right = 0.0)
        val otherSite = CALL.copy(line = 100)
        val elsewhere = invocation(id = 1, left = 1.0, right = 1.0).copy(callSite = otherSite)
        val nested = invocation(id = 2, left = 2.0, right = 2.0).copy(parentInvocationId = 0)

        val result = infer(listOf(first, elsewhere, nested)).relations.first()

        assertEquals(ScalarRelationStatus.INSUFFICIENT, result.status)
        assertEquals(1, result.distinctGenerationInputs.size)
        assertTrue(ScalarRelationLimitation.UNSUPPORTED_CONTEXT in result.limitations)
    }

    @Test
    fun `changed assertion operand source callsite and repeated occurrence cannot supply evidence`() {
        val sample = invocation(id = 0, left = 0.0, right = 0.0)
        val left = sample.points.first()
        val changed = listOf(
            left.copy(assertionId = "another"),
            left.copy(operandId = "another"),
            left.copy(source = left.source.copy(line = 99)),
            left.copy(callSite = CALL.copy(column = 99)),
            left.copy(kind = ObservationPointKind.POST),
            left.copy(inputIndex = 99),
            left.copy(occurrence = 1),
        )

        for (event in changed) {
            val result = infer(listOf(sample.copy(points = listOf(event, sample.points.last())))).relations.first()

            assertEquals(ScalarRelationStatus.UNSUPPORTED, result.status)
            assertTrue(ScalarRelationLimitation.POINT_IDENTITY_MISMATCH in result.limitations)
        }

        val repeated = sample.copy(points = sample.points + left.copy(occurrence = 1, eventOrdinal = 2))
        assertTrue(ScalarRelationLimitation.REPEATED_POINT in infer(listOf(repeated)).relations.first().limitations)
    }

    @Test
    fun `non numeric truncated and malformed numeric values remain unsupported`() {
        val sample = invocation(id = 0, left = 0.0, right = 0.0)
        val values = listOf(
            captured(JsConcreteValue.Null),
            captured(JsConcreteValue.Undefined),
            captured(JsConcreteValue.String("0")),
            captured(JsConcreteValue.Boolean(true)),
            PropertyObservationValue(status = ObservationValueStatus.TRUNCATED, reason = "byte-budget"),
            captured(JsConcreteValue.Number(JsNumber(value = JsNumberKind.FINITE, bits = "not-bits"))),
        )

        for (value in values) {
            val points = listOf(sample.points.first().copy(value = value), sample.points.last())
            val result = infer(listOf(sample.copy(points = points))).relations.first()

            assertEquals(ScalarRelationStatus.UNSUPPORTED, result.status)
            assertTrue(ScalarRelationLimitation.UNSUPPORTED_VALUE in result.limitations)
        }
    }

    @Test
    fun `numeric equality and order preserve NaN infinities signed zero and binary64 rounding`() {
        val cases = listOf(
            Triple(Double.NaN, Double.NaN, false),
            Triple(-0.0, 0.0, true),
            Triple(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true),
            Triple(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, true),
            Triple(9_007_199_254_740_992.0, 9_007_199_254_740_993.0, true),
        )

        for ((left, right, expected) in cases) {
            val relations = infer(listOf(invocation(id = 0, left = left, right = right))).relations

            assertTrue(relations.all { it.evidence.single().holds == expected })
        }

        val ordered = infer(listOf(invocation(id = 0, left = Double.NEGATIVE_INFINITY, right = 0.0))).relations
        assertEquals(false, ordered.first().evidence.single().holds)
        assertEquals(true, ordered.last().evidence.single().holds)
    }

    @Test
    fun `constant observed pairs are not promoted by unrelated changing inputs`() {
        val first = invocation(id = 0, left = 1.0, right = 1.0)
        val second = invocation(id = 1, left = 1.0, right = 1.0).copy(input = captured(input(2.0)))

        val result = infer(listOf(first, second)).relations.first()

        assertEquals(2, result.distinctGenerationInputs.size)
        assertEquals(ScalarRelationStatus.INSUFFICIENT, result.status)
        assertTrue(ScalarRelationLimitation.CONSTANT_OBSERVED_PAIR in result.limitations)
    }

    @Test
    fun `self pairs unrelated points and missing source hashes stay unsupported`() {
        val sample = invocation(id = 0, left = 0.0, right = 0.0)
        val unrelated = RIGHT.copy(assertionId = "other")
        val pairs = listOf(ScalarObservationPair(LEFT, LEFT), ScalarObservationPair(LEFT, unrelated))

        val result = inferScalarRelations(PROPERTY, artifact(listOf(sample)), pairs)

        assertTrue(result.relations.all { ScalarRelationLimitation.INVALID_PAIR in it.limitations })
        val noSources = artifact(listOf(sample)).copy(verifiedSources = emptyList())
        val sourceResult = inferScalarRelations(PROPERTY, noSources, listOf(PAIR)).relations.first()
        assertTrue(ScalarRelationLimitation.SOURCE_MISMATCH in sourceResult.limitations)
    }

    @Test
    fun `limits and deduplication are deterministic and preserve omitted evidence diagnostics`() {
        val samples = (0..3).map { invocation(it, it.toDouble(), it.toDouble()) }
        val observations = artifact(samples).copy(droppedInvocations = 2, droppedPoints = 3)

        val result = inferScalarRelations(
            property = PROPERTY,
            observations = observations,
            pairs = listOf(PAIR, PAIR, ScalarObservationPair(RIGHT, LEFT)),
            maxPairs = 1,
            maxInvocations = 2,
        )

        assertEquals(2, result.relations.size)
        assertEquals(1, result.omittedPairs)
        assertEquals(2, result.omittedInvocations)
        assertEquals(2, result.droppedInvocations)
        assertEquals(3, result.droppedPoints)
        assertTrue(result.relations.all { it.status == ScalarRelationStatus.INSUFFICIENT })
        assertTrue(result.relations.all { ScalarRelationLimitation.INVOCATION_LIMIT in it.limitations })
    }

    @Test
    fun `empty pending and dropped capture report insufficient evidence`() {
        val sample = invocation(id = 0, left = 0.0, right = 0.0)
        val samples = listOf(sample, invocation(id = 1, left = 1.0, right = 1.0))
        val incomplete = artifact(samples).copy(droppedPoints = 1)

        val emptyResult = infer(emptyList())
        val pendingResult = infer(listOf(sample.copy(outcome = ObservationOutcome.PENDING)))
        val droppedResult = inferScalarRelations(PROPERTY, incomplete, listOf(PAIR))

        assertTrue(emptyResult.relations.all { it.status == ScalarRelationStatus.INSUFFICIENT })
        assertTrue(pendingResult.relations.all { it.status == ScalarRelationStatus.INSUFFICIENT })
        assertTrue(droppedResult.relations.all { it.status == ScalarRelationStatus.INSUFFICIENT })
        assertTrue(droppedResult.relations.all { ScalarRelationLimitation.INCOMPLETE_CAPTURE in it.limitations })
    }

    @Test
    fun `bounded sampling warnings preserve agreement only over complete retained invocations`() {
        val samples = listOf(
            invocation(id = 0, left = 0.0, right = 0.0),
            invocation(id = 1, left = 1.0, right = 1.0),
            invocation(id = 2, left = 2.0, right = 0.0),
        )
        val observations = artifact(samples).copy(droppedInvocations = 97)

        val result = inferScalarRelations(PROPERTY, observations, listOf(PAIR), maxInvocations = 2)
        val selected = selectScalarGuidance(result)

        assertEquals(2, selected.candidates.size)
        assertTrue(result.relations.all { it.status == ScalarRelationStatus.SAMPLED })
        assertTrue(result.relations.all { it.evidence.map { evidence -> evidence.invocation } == samples.take(2) })
        assertTrue(result.relations.all { ScalarRelationLimitation.INVOCATION_LIMIT in it.limitations })
        assertTrue(result.relations.all { ScalarRelationLimitation.INCOMPLETE_CAPTURE in it.limitations })
        assertEquals(1, result.omittedInvocations)
        assertEquals(97, result.droppedInvocations)
        assertEquals(ObservationBuildStatus.UNVERIFIED, result.buildStatus)
        assertEquals(samples, observations.invocations)
    }

    @Test
    fun `truncated invocations retain raw points but cannot supply either independent support or binding evidence`() {
        val good = listOf(invocation(id = 0, left = 0.0, right = 0.0), invocation(id = 1, left = 1.0, right = 1.0))
        val partial = invocation(id = 2, left = 2.0, right = 2.0).copy(pointsTruncated = true)
        val observations = artifact(good + partial).copy(droppedPoints = 1)
        val partialPair = partial.copy(points = partial.points.take(1))

        for (truncated in listOf(partial, partialPair)) {
            val recorded = observations.copy(invocations = good + truncated)
            val result = inferScalarRelations(PROPERTY, recorded, listOf(PAIR))

            assertTrue(result.relations.all { it.status == ScalarRelationStatus.SAMPLED })
            assertTrue(result.relations.all { it.evidence.map { evidence -> evidence.invocation } == good })
            assertTrue(result.relations.all { ScalarRelationLimitation.TRUNCATED_INVOCATION in it.limitations })
            assertEquals(2, selectScalarGuidance(result).candidates.size)
            assertEquals(1, result.droppedPoints)
            assertEquals(good + truncated, recorded.invocations)
        }
        val oneGood = observations.copy(invocations = good.take(1) + partial)
        val insufficient = inferScalarRelations(PROPERTY, oneGood, listOf(PAIR))
        assertTrue(insufficient.relations.all { it.status == ScalarRelationStatus.INSUFFICIENT })
        assertTrue(selectScalarGuidance(insufficient).candidates.isEmpty())
    }

    @Test
    fun `older observation JSON keeps unknown point completeness while new receipts round trip their marker`() {
        val sample = invocation(id = 0, left = 0.0, right = 0.0)
        val json = PropertyManifestJson.json
        val serializer = PropertyInvocationObservation.serializer()
        val value = json.encodeToJsonElement(serializer, sample).jsonObject
        val legacy = JsonObject(value - "pointsTruncated")

        val decoded = json.decodeFromJsonElement(serializer, legacy)

        assertNull(decoded.pointsTruncated)
        for (marker in listOf(null, false, true)) {
            val changed = sample.copy(pointsTruncated = marker)
            val encoded = json.encodeToString(serializer, changed)
            assertEquals(changed, json.decodeFromString(serializer, encoded))
        }
    }

    @Test
    fun `recording truncation does not excuse repeated mismatched nonnumeric nested or incomplete observations`() {
        val good = listOf(invocation(id = 0, left = 0.0, right = 0.0), invocation(id = 1, left = 1.0, right = 1.0))
        val partial = invocation(id = 2, left = 2.0, right = 2.0).copy(pointsTruncated = true)
        val first = partial.points.first()
        val failures = listOf(
            partial.copy(points = partial.points + first.copy(occurrence = 1)),
            partial.copy(points = listOf(first.copy(inputIndex = 7), partial.points.last())),
            partial.copy(points = listOf(first.copy(value = captured(JsConcreteValue.String("bad"))))),
            partial.copy(parentInvocationId = 0),
            partial.copy(outcome = ObservationOutcome.PENDING),
        )
        for (invalid in failures) {
            val observations = artifact(good + invalid).copy(droppedPoints = 1)

            val result = inferScalarRelations(PROPERTY, observations, listOf(PAIR))

            assertTrue(result.relations.none { it.status == ScalarRelationStatus.SAMPLED })
            assertTrue(selectScalarGuidance(result).candidates.isEmpty())
        }
        val legacy = artifact(good.map { it.copy(pointsTruncated = null) }).copy(droppedPoints = 1)
        val result = inferScalarRelations(PROPERTY, legacy, listOf(PAIR))
        assertTrue(result.relations.all { ScalarRelationLimitation.UNATTRIBUTED_POINT_DROPS in it.limitations })
        assertTrue(selectScalarGuidance(result).candidates.isEmpty())
    }

    @Test
    fun `selected source hashes are checked against property identity when declared`() {
        val sourceIdentity = PropertySourceIdentity(
            sourceSha256 = "b".repeat(64),
            buildSha256 = "c".repeat(64),
            buildScope = "test",
        )
        val property = PROPERTY.copy(sourceIdentity = sourceIdentity)
        val observations = artifact(listOf(invocation(id = 0, left = 0.0, right = 0.0)))

        val result = inferScalarRelations(property, observations, listOf(PAIR))

        assertTrue(result.relations.all { it.status == ScalarRelationStatus.UNSUPPORTED })
        assertTrue(result.relations.all { ScalarRelationLimitation.SOURCE_MISMATCH in it.limitations })
    }

    @Test
    fun `signed zeros alone do not count as numeric diversity`() {
        val samples = listOf(invocation(id = 0, left = -0.0, right = -0.0), invocation(id = 1, left = 0.0, right = 0.0))

        val result = infer(samples)

        assertTrue(result.relations.all { it.status == ScalarRelationStatus.INSUFFICIENT })
        assertTrue(result.relations.all { ScalarRelationLimitation.CONSTANT_OBSERVED_PAIR in it.limitations })
        assertEquals(2, result.relations.first().distinctGenerationInputs.size)
    }

    @Test
    fun `property identity and positive bounds are required`() {
        val observations = artifact(emptyList())

        assertFailsWith<IllegalArgumentException> {
            inferScalarRelations(PROPERTY, observations.copy(propertyId = PropertyId("other")), listOf(PAIR))
        }
        assertFailsWith<IllegalArgumentException> {
            inferScalarRelations(PROPERTY, observations, listOf(PAIR), maxPairs = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            inferScalarRelations(PROPERTY, observations, listOf(PAIR), maxInvocations = 0)
        }
    }

    @Test
    fun `guidance selection removes symmetric equality duplicates without merging temporal identities`() {
        val samples = listOf(invocation(id = 0, left = 0.0, right = 0.0), invocation(id = 1, left = 1.0, right = 1.0))
        val observations = artifact(samples)
        val reversed = ScalarObservationPair(RIGHT, LEFT)
        val inference = inferScalarRelations(PROPERTY, observations, listOf(PAIR, reversed))

        val selected = selectScalarGuidance(inference)

        assertSame(inference, selected.inference)
        assertEquals(3, selected.candidates.size)
        assertTrue(selected.candidates.all { it.relevance == ScalarGuidanceRelevance.UNKNOWN })
        assertEquals(ScalarGuidanceExclusionReason.DUPLICATE, selected.excluded.single().reason)
        assertEquals(reversed, selected.excluded.single().relation.pair)
        assertEquals(ScalarRelationTemplate.STRICT_EQUAL, selected.excluded.single().relation.template)
        val duplicateEvidence = selected.excluded.single().relation.evidence
        assertEquals(samples, duplicateEvidence.map { it.invocation })

        val equality = selected.candidates.first().relation
        val later = equality.copy(pair = equality.pair.copy(right = RIGHT.copy(kind = ObservationPointKind.POST)))
        val differentTimes = inference.copy(relations = listOf(equality, later))
        assertEquals(2, selectScalarGuidance(differentTimes).candidates.size)
    }

    @Test
    fun `empty finite and contradicted evidence gives no guidance without losing the original artifact`() {
        val samples = listOf(invocation(id = 0, left = 1.0, right = 1.0))
        val insufficient = infer(samples)
        val contradicted = infer(samples + invocation(id = 1, left = 1.0, right = 0.0))

        for (inference in listOf(infer(emptyList()), insufficient, contradicted)) {
            val selected = selectScalarGuidance(inference)

            assertSame(inference, selected.inference)
            assertTrue(selected.candidates.isEmpty())
            assertEquals(inference.relations, selected.excluded.map { it.relation })
            assertTrue(selected.excluded.all { it.reason == ScalarGuidanceExclusionReason.NOT_SAMPLED })
        }
    }

    @Test
    fun `guidance candidates are bounded in original selection order and overflow evidence is retained`() {
        val samples = listOf(invocation(id = 0, left = 0.0, right = 0.0), invocation(id = 1, left = 1.0, right = 1.0))
        val inference = infer(samples)

        val selected = selectScalarGuidance(inference, maxCandidates = 1)

        assertEquals(inference.relations.first(), selected.candidates.single().relation)
        assertEquals(inference.relations.last(), selected.excluded.single().relation)
        assertEquals(ScalarGuidanceExclusionReason.CANDIDATE_LIMIT, selected.excluded.single().reason)
        assertFailsWith<IllegalArgumentException> { selectScalarGuidance(inference, maxCandidates = 0) }
    }

    private fun infer(samples: List<PropertyInvocationObservation>): ScalarRelationInference =
        inferScalarRelations(PROPERTY, artifact(samples), listOf(PAIR))

    private fun invocation(id: Int, left: Double, right: Double): PropertyInvocationObservation =
        PropertyInvocationObservation(
            invocationId = id,
            callSite = CALL,
            origin = ObservationOrigin.FAST_CHECK,
            phase = ObservationPhase.GENERATION,
            input = captured(input(left)),
            admission = ObservationAdmission.ADMITTED,
            outcome = ObservationOutcome.HOLDS,
            points = listOf(event(LEFT, left, ordinal = 0), event(RIGHT, right, ordinal = 1)),
            pointsTruncated = false,
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

    private fun artifact(samples: List<PropertyInvocationObservation>): PropertyObservationArtifact =
        PropertyObservationArtifact(
            propertyId = PROPERTY.id,
            runId = "sample-run",
            verifiedSources = SOURCES,
            buildStatus = ObservationBuildStatus.UNVERIFIED,
            invocations = samples,
            droppedInvocations = 0,
            droppedPoints = 0,
            captureTimeMillis = 0,
        )

    private fun input(number: Double): JsConcreteValue = JsConcreteValue.Array(listOf(JsConcreteValue.number(number)))

    private fun captured(value: JsConcreteValue): PropertyObservationValue =
        PropertyObservationValue(status = ObservationValueStatus.CAPTURED, value = value)

    private companion object {
        const val MODULE = "property.ts"
        val CALL = PropertySourcePoint(module = MODULE, line = 1, column = 1)
        val LEFT = PropertyObservationPoint(
            id = "input",
            assertionId = "check",
            operandId = "input",
            source = CALL,
            callSite = CALL,
            kind = ObservationPointKind.ARGUMENT,
            inputIndex = 0,
        )
        val RIGHT = PropertyObservationPoint(
            id = "result",
            assertionId = "check",
            operandId = "result",
            source = CALL.copy(column = 2),
            callSite = CALL,
            kind = ObservationPointKind.RETURN,
        )
        val PAIR = ScalarObservationPair(LEFT, RIGHT)
        val SOURCES = listOf(PropertyObservationSource(module = MODULE, sha256 = "a".repeat(64)))
        val PROPERTY = PropertyDefinition(
            id = PropertyId("test.numeric"),
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 0, max = 3))),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = "check"),
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
    }
}
