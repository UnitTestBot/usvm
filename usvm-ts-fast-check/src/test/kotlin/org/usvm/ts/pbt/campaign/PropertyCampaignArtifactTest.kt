package org.usvm.ts.pbt.campaign

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessReductionStatus
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessRole
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.ObservationOrigin
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertyCampaignArtifactTest {
    @TempDir
    lateinit var directory: Path

    private val root = testResourcesRoot()
    private val configuration = PropertyRunConfiguration(seed = 354, numRuns = 1, timeoutMillis = 60_000)

    @Test
    fun `saved concrete violation retains actual source coverage and bounded observations`() {
        val module = "properties/real/ArrayArbitraryProperty.ts"
        val source = PropertySourcePoint(module = module, line = 16, column = 5)
        val call = PropertySourcePoint(module = module, line = 18, column = 22)
        val property = PropertyDefinition(
            id = PropertyId(value = "artifact.observed"),
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(IntegerDomain(min = 0, max = 10), minLength = 0, maxLength = 8),
                ),
            ),
            predicate = TypeScriptEntryPoint(module = module, exportName = "originalUniqueOracle"),
            assertions = listOf(
                PropertyAssertion(
                    id = "no-duplicates",
                    source = PropertySourcePoint(module = module, line = 20, column = 5),
                    testedCall = call,
                    operands = listOf(PropertyOperand(id = "input-array", source = source)),
                ),
            ),
        )
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(module)))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val duplicate = JsConcreteValue.Array(listOf(JsConcreteValue.number(1.0), JsConcreteValue.number(1.0)))
        val requested = configuration.copy(
            examples = listOf(listOf(duplicate)),
            coverageRequest = PropertyCoverageRequest(scopes = setOf(CoverageScope.PROPERTY_ENTRY_POINTS)),
            observationRequest = PropertyObservationRequest(
                sources = listOf(PropertyObservationSource(module = module, sha256 = hash)),
                points = listOf(
                    PropertyObservationPoint(
                        id = "input-array",
                        assertionId = "no-duplicates",
                        operandId = "input-array",
                        source = source,
                        callSite = call,
                        kind = ObservationPointKind.ARGUMENT,
                        inputIndex = 0,
                    ),
                ),
            ),
        )

        val artifact = PropertyCampaignRunner(sourceRoot = root).runArtifact(
            property = property,
            mode = PropertyCampaignMode.PBT_ONLY,
            configuration = requested,
        )
        val restored = saveAndRead(artifact)

        assertTrue(restored.result.confirmedViolation)
        assertEquals(PropertyFailureKind.PROPERTY, restored.result.concrete?.failure?.kind)
        assertEquals("no-duplicates", restored.property.assertions.single().id)
        assertEquals(requested, restored.configuration)
        assertTrue(assertNotNull(restored.result.concrete?.coverage).files.isNotEmpty())
        val observations = assertNotNull(restored.result.concrete?.observations)
        val invocation = observations.invocations.first()
        assertEquals(listOf(PropertyObservationSource(module = module, sha256 = hash)), observations.verifiedSources)
        assertEquals(ObservationOrigin.FAST_CHECK, invocation.origin)
        assertEquals(duplicate, invocation.points.single().value.value)
        assertNull(restored.result.symbolic)
        assertEquals(artifact, restored)
    }

    @Test
    fun `saved symbolic candidate keeps native confirmation reduction and available provenance`() {
        val artifact = PropertyCampaignRunner(sourceRoot = root.resolve("properties/campaign")).runArtifact(
            property = symbolicProperty(),
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
        )

        val restored = saveAndRead(artifact)

        assertNull(restored.result.concrete)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, restored.result.symbolic?.status)
        assertEquals(listOf(JsConcreteValue.number(42.0)), restored.result.symbolic?.inputs)
        val confirmation = assertNotNull(restored.result.confirmation)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, confirmation.status)
        assertEquals(WitnessReductionStatus.EXHAUSTED, confirmation.reduction.status)
        assertEquals(listOf(JsConcreteValue.number(42.0)), confirmation.reduction.inputs)
        assertTrue(confirmation.candidate.sources.isNotEmpty())
        assertNotNull(confirmation.originalReplay?.runtimeVersion)
        assertEquals("fast-check", confirmation.backendId)
        assertEquals(WitnessRole.PROPERTY, restored.targetRole)
        assertTrue(restored.result.confirmedViolation)
        assertNull(restored.property.sourceIdentity)
        assertEquals(artifact, restored)

        val mismatched = confirmation.copy(
            candidate = confirmation.candidate.copy(propertyId = PropertyId(value = "other.property")),
        )
        assertFailsWith<IllegalArgumentException> {
            artifact.copy(result = artifact.result.copy(confirmation = mismatched))
        }
        val symbolic = assertNotNull(artifact.result.symbolic)
        val otherSymbolic = symbolic.copy(propertyId = PropertyId(value = "other.property"))
        val mixedResult = artifact.result.copy(symbolic = otherSymbolic)
        assertFailsWith<IllegalArgumentException> {
            artifact.copy(result = mixedResult)
        }
    }

    @Test
    fun `saved unsupported native replay retains the original unconfirmed candidate`() {
        val property = symbolicProperty().copy(
            assertions = listOf(
                PropertyAssertion(
                    id = "not-forty-two",
                    source = PropertySourcePoint(module = "DirectControls.ts", line = 5, column = 1),
                ),
            ),
        )
        val artifact = PropertyCampaignRunner(sourceRoot = root.resolve("properties/campaign")).runArtifact(
            property = property,
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
        )

        val restored = saveAndRead(artifact)

        assertNotNull(restored.result.symbolic?.inputs)
        assertEquals(restored.result.symbolic?.inputs, restored.result.confirmation?.candidate?.inputs)
        assertEquals(WitnessReplayStatus.UNSUPPORTED_REPLAY, restored.result.confirmation?.status)
        assertFalse(restored.result.confirmedViolation)
        assertEquals(artifact, restored)
    }

    @Test
    fun `saved confirmation timeout retains its candidate and campaign stop phase`() {
        val executable = directory.resolve("waiting-node")
        executable.writeText("#!/bin/sh\nexec node -e 'setInterval(() => {}, 1000)'\n")
        check(executable.toFile().setExecutable(true))
        val runner = PropertyCampaignRunner(
            sourceRoot = root.resolve("properties/campaign"),
            nodeExecutable = executable.toString(),
        )
        val artifact = runner.runArtifact(
            property = symbolicProperty(),
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration.copy(timeoutMillis = 10_000),
            validationReserveMillis = 1_000,
        )

        val restored = saveAndRead(artifact)

        assertEquals(PropertyCampaignStop.DEADLINE, restored.result.stop)
        assertEquals(PropertyCampaignPhase.CONFIRMATION, restored.result.stoppedAt)
        assertEquals(WitnessReplayStatus.TIMEOUT, restored.result.confirmation?.status)
        assertEquals(listOf(JsConcreteValue.number(42.0)), restored.result.confirmation?.candidate?.inputs)
        assertEquals(10_000, restored.configuration.timeoutMillis)
        assertEquals(1_000, restored.validationReserveMillis)
        assertFalse(restored.result.confirmedViolation)
        assertEquals(artifact, restored)
    }

    @Test
    fun `UTF8 file preserves native special-value inputs and isolated UTF16 surrogates`() {
        val values = listOf(
            JsConcreteValue.number(-0.0),
            JsConcreteValue.number(Double.NaN),
            JsConcreteValue.String(value = "\ud800"),
            JsConcreteValue.Undefined,
            JsConcreteValue.number(Double.POSITIVE_INFINITY),
        )
        val property = PropertyDefinition(
            id = PropertyId(value = "artifact.special-values"),
            inputs = values.mapIndexed { index, value ->
                PropertyInput(name = "value$index", domain = ConstantDomain(value))
            },
            predicate = TypeScriptEntryPoint(
                module = "properties/witness/ExternalWitness.ts",
                exportName = "specialValues",
            ),
        )
        val artifact = PropertyCampaignRunner(sourceRoot = root).runArtifact(
            property = property,
            mode = PropertyCampaignMode.PBT_ONLY,
            configuration = configuration.copy(examples = listOf(values)),
        )

        val restored = saveAndRead(artifact)

        assertTrue(restored.result.confirmedViolation)
        assertEquals(values, restored.result.concrete?.counterexample)
        assertEquals(listOf(values), restored.configuration.examples)
        assertTrue(Files.readString(directory.resolve("campaign.json")).contains("\\ud800"))
        assertEquals(artifact, restored)
    }

    @Test
    fun `artifact rejects a result for a different property during creation and file reading`() {
        val artifact = PropertyCampaignRunner(sourceRoot = root.resolve("properties/campaign")).runArtifact(
            property = symbolicProperty(),
            mode = PropertyCampaignMode.PBT_ONLY,
            configuration = configuration,
        )
        val other = artifact.result.copy(propertyId = PropertyId(value = "other.property"))

        assertFailsWith<IllegalArgumentException> { artifact.copy(result = other) }
        val concrete = assertNotNull(artifact.result.concrete)
        val otherConcrete = concrete.copy(propertyId = PropertyId(value = "other.property"))
        val mixedResult = artifact.result.copy(concrete = otherConcrete)
        assertFailsWith<IllegalArgumentException> {
            artifact.copy(result = mixedResult)
        }

        val path = directory.resolve("campaign.json")
        artifact.write(path)
        val json = PropertyManifestJson.json.parseToJsonElement(Files.readString(path)).jsonObject
        val result = json.getValue("result").jsonObject
        val mismatchedConcrete = JsonObject(
            result.getValue("concrete").jsonObject + ("propertyId" to JsonPrimitive("other.property")),
        )
        val mismatchedResult = JsonObject(result + ("concrete" to mismatchedConcrete))
        Files.writeString(path, JsonObject(json + ("result" to mismatchedResult)).toString())

        assertFailsWith<IllegalArgumentException> { PropertyCampaignArtifact.read(path) }
    }

    private fun saveAndRead(artifact: PropertyCampaignArtifact): PropertyCampaignArtifact {
        val path = directory.resolve("campaign.json")
        artifact.write(path)

        return PropertyCampaignArtifact.read(path)
    }

    private fun symbolicProperty() = PropertyDefinition(
        id = PropertyId(value = "artifact.symbolic"),
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 0, max = 100))),
        predicate = TypeScriptEntryPoint(module = "DirectControls.ts", exportName = "isolatedFailure"),
        precondition = TypeScriptEntryPoint(module = "DirectControls.ts", exportName = "admits"),
    )
}
