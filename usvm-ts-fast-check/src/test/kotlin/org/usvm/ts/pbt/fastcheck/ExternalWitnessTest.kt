package org.usvm.ts.pbt.fastcheck

import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.WitnessBinding
import org.usvm.ts.pbt.backend.WitnessEvidenceDelta
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReductionStatus
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessRole
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ArrayIndexGenerator
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalWitnessTest {
    private val root = testResourcesRoot()
    private val backend = FastCheckBackend(sourceRoots = listOf(root))
    private val configuration = WitnessReplayConfiguration(timeoutMillis = 15_000, validationReserveMillis = 3_000)

    @Test
    fun `external witness shrinks and serializes original final inputs and backend provenance`() {
        val property = property(predicate = "threshold")
        val candidate = candidate(property = property)

        val result = backend.confirmWitness(property = property, candidate = candidate, configuration = configuration)
        val json = PropertyManifestJson.json.encodeToUtf8SafeString(result)
        val decoded = PropertyManifestJson.json.decodeFromString<WitnessEvidenceDelta>(json)

        assertEquals(result, decoded)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
        assertEquals(listOf(JsConcreteValue.number(80.0)), result.candidate.inputs)
        assertEquals(listOf(JsConcreteValue.number(10.0)), result.reduction.inputs)
        assertEquals(WitnessReductionStatus.EXHAUSTED, result.reduction.status)
        assertEquals(WitnessOracleOutcome.FALSE, result.reducedReplay?.outcome)
        assertTrue(result.reduction.acceptedShrinks > 0)
        assertTrue(result.reduction.attempts > result.reduction.acceptedShrinks)
        assertNotNull(result.reduction.reducedEncodedBytes)
        assertEquals("fast-check", result.backendId)
        assertEquals("4.9.0", result.backendVersion)
    }

    @Test
    fun `a different named failed check cannot replace the original check`() {
        val property = property(predicate = "namedChecks").copy(
            assertions = listOf(PropertyAssertion(id = "threshold-check", source = sourcePoint)),
        )
        val target = WitnessTarget(
            id = "threshold-check",
            binding = binding(pointId = "threshold-check"),
        )

        val result = backend.confirmWitness(
            property = property,
            candidate = candidate(property = property, target = target),
            configuration = configuration,
        )

        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
        assertEquals(listOf(JsConcreteValue.number(15.0)), result.reduction.inputs)
        assertTrue(assertNotNull(result.reducedReplay).targetMatched)
    }

    @Test
    fun `guarded hypothesis refutation holds the original oracle and reduces the same contextual target`() {
        val property = property(predicate = "hypothesis")
        val target = hypothesisTarget

        val result = backend.confirmWitness(
            property = property,
            candidate = candidate(property = property, target = target),
            configuration = configuration,
        )

        assertEquals(WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION, result.status)
        assertEquals(WitnessOracleOutcome.HOLDS, result.originalReplay?.outcome)
        assertEquals(WitnessOracleOutcome.HOLDS, result.reducedReplay?.outcome)
        assertEquals(listOf(JsConcreteValue.number(5.0)), result.reduction.inputs)
        assertEquals(1, result.contextualRefutations.size)
        assertEquals(target.binding, result.contextualRefutations.single().binding)
    }

    @Test
    fun `wrong context revision occurrence and inactive guard do not confirm hypotheses`() {
        val cases = listOf(
            MismatchCase(predicate = "differentContext", target = hypothesisTarget, value = 80.0),
            MismatchCase(
                predicate = "hypothesis",
                target = hypothesisTarget.copy(binding = binding(pointId = "hypothesis").copy(revision = "v2")),
                value = 80.0,
            ),
            MismatchCase(predicate = "repeatedHypothesis", target = hypothesisTarget, value = 80.0),
            MismatchCase(predicate = "hypothesis", target = hypothesisTarget, value = 0.0),
        )

        for ((predicate, target, value) in cases) {
            val property = property(predicate = predicate)
            val result = backend.confirmWitness(
                property = property,
                candidate = candidate(
                    property = property,
                    target = target,
                    inputs = listOf(JsConcreteValue.number(value))
                ),
                configuration = configuration,
            )

            assertEquals(WitnessReplayStatus.TARGET_MISMATCH, result.status)
            assertTrue(result.contextualRefutations.isEmpty())
            assertTrue(result.confirmedEdgeAdditions.isEmpty())
            assertEquals(WitnessReductionStatus.NOT_ELIGIBLE, result.reduction.status)
        }
    }

    @Test
    fun `passing rejected unrepresentable and erroneous candidates are not faults`() {
        val property = property(predicate = "threshold")
        val base = candidate(property = property)
        val cases = listOf(
            base.copy(inputs = listOf(JsConcreteValue.number(1.0))) to WitnessReplayStatus.PROPERTY_HOLDS,
            base.copy(inputs = listOf(JsConcreteValue.number(101.0))) to WitnessReplayStatus.DOMAIN_REJECTED,
            base.copy(inputs = null) to WitnessReplayStatus.UNREPRESENTABLE_INPUT,
            base.copy(inputs = listOf(JsConcreteValue.String("wrong"))) to WitnessReplayStatus.DOMAIN_REJECTED,
        )
        for ((candidate, status) in cases) {
            val result = backend.confirmWitness(
                property = property,
                candidate = candidate,
                configuration = configuration
            )

            assertEquals(status, result.status)
            assertEquals(candidate, result.candidate)
            assertNull(result.reduction.inputs)
            assertTrue(result.confirmedEdgeAdditions.isEmpty())
        }
        for ((precondition, status) in listOf(
            "rejected" to WitnessReplayStatus.PRECONDITION_REJECTED,
            "preconditionError" to WitnessReplayStatus.PROPERTY_ERROR,
        )) {
            val guarded = property.copy(precondition = TypeScriptEntryPoint(module = MODULE, exportName = precondition))
            val result = backend.confirmWitness(
                property = guarded,
                candidate = candidate(property = guarded),
                configuration = configuration,
            )

            assertEquals(status, result.status)
        }
    }

    @Test
    fun `predicate exception is a fault and nonboolean result is an error`() {
        for ((predicate, status) in listOf(
            "predicateError" to WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION,
            "contractError" to WitnessReplayStatus.PROPERTY_ERROR,
            "nondeterministic" to WitnessReplayStatus.NONDETERMINISTIC,
        )) {
            val property = property(predicate = predicate)
            val result = backend.confirmWitness(
                property = property,
                candidate = candidate(property = property),
                configuration = configuration.copy(shrink = false),
            )

            assertEquals(status, result.status)
        }
    }

    @Test
    fun `source and generator registration drift invalidate replay`() {
        val property = property(predicate = "threshold")
        val base = candidate(property = property)
        val changedSource = base.copy(
            sources = listOf(PropertyObservationSource(module = MODULE, sha256 = "0".repeat(64)))
        )
        val changedProperty = property.copy(
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(max = 200)))
        )

        val sourceResult = backend.confirmWitness(
            property = property,
            candidate = changedSource,
            configuration = configuration
        )
        val registrationResult = backend.confirmWitness(
            property = changedProperty,
            candidate = base,
            configuration = configuration
        )

        assertEquals(WitnessReplayStatus.SOURCE_MISMATCH, sourceResult.status)
        assertEquals(WitnessReplayStatus.SOURCE_MISMATCH, registrationResult.status)
        assertEquals(changedSource, sourceResult.candidate)
        assertNull(sourceResult.reduction.inputs)
    }

    @Test
    fun `precondition admission is rechecked while shrinking`() {
        val property = property(predicate = "threshold").copy(
            precondition = TypeScriptEntryPoint(module = MODULE, exportName = "admitted"),
        )

        val result = backend.confirmWitness(
            property = property,
            candidate = candidate(property = property),
            configuration = configuration
        )

        assertEquals(listOf(JsConcreteValue.number(20.0)), result.reduction.inputs)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
    }

    @Test
    fun `mutating predicate inputs are isolated during replay and reduction`() {
        val property =
            property(predicate = "mutation", domains = listOf(ArrayDomain(element = IntegerDomain(min = 0, max = 100))))
        val inputs =
            listOf(JsConcreteValue.Array(elements = listOf(80.0, 90.0, 100.0, 70.0).map(JsConcreteValue::number)))

        val result = backend.confirmWitness(
            property = property,
            candidate = candidate(property = property, inputs = inputs),
            configuration = configuration,
        )

        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
        assertEquals(inputs, result.candidate.inputs)
        assertEquals(
            listOf(JsConcreteValue.Array(elements = List(3) { JsConcreteValue.number(0.0) })),
            result.reduction.inputs
        )
    }

    @Test
    fun `special values and argument order survive native witness replay`() {
        val inputs = listOf(
            JsConcreteValue.number(-0.0),
            JsConcreteValue.number(Double.NaN),
            JsConcreteValue.String("\uD800"),
            JsConcreteValue.Undefined,
            JsConcreteValue.number(Double.POSITIVE_INFINITY),
        )
        val property =
            property(predicate = "specialValues", domains = inputs.map { value -> ConstantDomain(value = value) })

        val result = backend.confirmWitness(
            property = property,
            candidate = candidate(property = property, inputs = inputs),
            configuration = configuration,
        )

        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
        assertEquals(inputs, result.candidate.inputs)
        assertEquals(inputs, result.reduction.inputs)
        assertEquals(0, result.reduction.acceptedShrinks)
    }

    @Test
    fun `contextless dependent generator retains original confirmed witness`() {
        val property = property(
            predicate = "supportedIndex",
            domains = listOf(
                ArrayDomain(element = IntegerDomain(min = 0, max = 100), minLength = 1, maxLength = 3),
                IntegerDomain(min = 0, max = 2),
            ),
        ).copy(generator = ArrayIndexGenerator(id = "index", arrayInputIndex = 0, indexInputIndex = 1))
        val inputs = listOf(
            JsConcreteValue.Array(elements = listOf(JsConcreteValue.number(80.0), JsConcreteValue.number(90.0))),
            JsConcreteValue.number(1.0),
        )

        val result = backend.confirmWitness(
            property = property,
            candidate = candidate(property = property, inputs = inputs),
            configuration = configuration,
        )

        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
        assertEquals(WitnessReductionStatus.UNSUPPORTED, result.reduction.status)
        assertEquals(inputs, result.candidate.inputs)
        assertNull(result.reduction.inputs)
    }

    @Test
    fun `supervised reduction timeout retains original fault and shared expired deadline retains candidate`() {
        val property = property(predicate = "slowShrink")
        val candidate = candidate(property = property)

        val result = backend.confirmWitness(
            property = property,
            candidate = candidate,
            configuration = configuration.copy(timeoutMillis = 3_000, validationReserveMillis = 2_000),
        )
        val expired = WitnessReplayDeadline(timeoutMillis = 1)
        Thread.sleep(5)
        val timedOut = backend.confirmWitness(
            property = property,
            candidate = candidate,
            configuration = configuration,
            deadline = expired
        )

        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
        assertEquals(WitnessReductionStatus.TIMEOUT, result.reduction.status)
        assertEquals(candidate, result.candidate)
        assertNull(result.reduction.inputs)
        assertEquals(WitnessReplayStatus.TIMEOUT, timedOut.status)
        assertEquals(candidate, timedOut.candidate)
    }

    @Test
    fun `reducer error and failed fresh final replay preserve original confirmed inputs`() {
        for (predicate in listOf("shrinkError", "requiresInitial")) {
            val property = property(predicate = predicate)
            val candidate = candidate(property = property)

            val result = backend.confirmWitness(
                property = property,
                candidate = candidate,
                configuration = configuration,
            )

            assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, result.status)
            assertEquals(candidate, result.candidate)
            assertEquals(WitnessReductionStatus.FAILED, result.reduction.status)
            assertNull(result.reduction.inputs)
            assertNull(result.reducedReplay)
        }
    }

    @Test
    fun `symbolic false target is not confirmed by a different native exception`() {
        val property = property(predicate = "predicateError")
        val candidate = candidate(
            property = property,
            target = WitnessTarget(id = "predicateError", expectedOutcome = WitnessOracleOutcome.FALSE),
        )

        val result = backend.confirmWitness(property = property, candidate = candidate, configuration = configuration)

        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, result.status)
        assertEquals(WitnessOracleOutcome.THREW, result.originalReplay?.outcome)
        assertEquals(WitnessReductionStatus.NOT_ELIGIBLE, result.reduction.status)
    }

    @Test
    fun `coverage witness contributes only confirmed exact edges and removes known additions`() {
        val module = "properties/witness/CoverageWitness.ts"
        val property = property(
            predicate = "coverage"
        ).copy(predicate = TypeScriptEntryPoint(module = module, exportName = "coverage"))
        val candidate = candidate(
            property = property,
            target = WitnessTarget(
                id = "positive-edge",
                role = WitnessRole.COVERAGE,
                binding = binding(pointId = "positive-edge")
            ),
        )
        val withCoverage = configuration.copy(
            shrink = false,
            coverageRequest = PropertyCoverageRequest(scopes = setOf(CoverageScope.PROPERTY_ENTRY_POINTS)),
        )

        val result = backend.confirmWitness(property = property, candidate = candidate, configuration = withCoverage)
        val repeated = backend.confirmWitness(
            property = property,
            candidate = candidate,
            configuration = withCoverage.copy(knownEdges = result.confirmedEdgeAdditions),
        )

        assertEquals(WitnessReplayStatus.CONFIRMED_COVERAGE, result.status)
        assertEquals(WitnessOracleOutcome.HOLDS, result.originalReplay?.outcome)
        assertEquals(1, result.confirmedEdgeAdditions.size, result.originalReplay?.coverage.toString())
        assertTrue(result.confirmedEdgeAdditions.single().outcome)
        assertTrue(repeated.confirmedEdgeAdditions.isEmpty())
    }

    private fun property(
        predicate: String,
        domains: List<PropertyDomain> = listOf(IntegerDomain(min = 0, max = 100)),
    ) = PropertyDefinition(
        id = PropertyId(value = "witness.$predicate"),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = predicate),
        inputs = domains.mapIndexed { index, domain -> PropertyInput(name = "value$index", domain = domain) },
    )

    private fun candidate(
        property: PropertyDefinition,
        target: WitnessTarget = WitnessTarget(id = property.predicate.exportName),
        inputs: List<JsConcreteValue>? = listOf(JsConcreteValue.number(80.0)),
    ) = property.captureWitness(
        inputs = inputs,
        target = target,
        sources = listOf(source(module = property.predicate.module)),
        provenance = "symbolic-fixture",
    )

    private fun source(module: String): PropertyObservationSource {
        val bytes = Files.readAllBytes(root.resolve(module))
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return PropertyObservationSource(module = module, sha256 = hash)
    }

    private fun binding(pointId: String) = WitnessBinding(pointId = pointId, contextId = "original", revision = "v1")

    private val hypothesisTarget =
        WitnessTarget(id = "hypothesis", role = WitnessRole.HYPOTHESIS, binding = binding(pointId = "hypothesis"))
    private val sourcePoint = PropertySourcePoint(module = MODULE, line = 1, column = 1)

    private data class MismatchCase(
        val predicate: String,
        val target: WitnessTarget,
        val value: Double,
    )

    private companion object {
        const val MODULE = "properties/witness/ExternalWitness.ts"
    }
}
