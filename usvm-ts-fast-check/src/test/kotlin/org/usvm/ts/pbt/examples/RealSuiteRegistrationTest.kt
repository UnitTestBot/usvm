package org.usvm.ts.pbt.examples

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourceIdentity
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.ObservationAdmission
import org.usvm.ts.pbt.observation.ObservationOutcome
import org.usvm.ts.pbt.observation.ObservationPhase
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.ObservationValueStatus
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealSuiteRegistrationTest {
    @Test
    fun `original callback yields bounded input intermediate and outcome observations`() {
        val source = testResourcesRoot().resolve(MODULE)
        val hash = sourceHash(source)
        val definition = property(
            exportName = "originalUniqueOracle",
            sourceIdentity = PropertySourceIdentity(
                sourceSha256 = hash,
                buildSha256 = hash,
                buildScope = "direct TypeScript source input",
            ),
        )
        val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))
        val values = JsConcreteValue.Array(listOf(JsConcreteValue.number(1.0), JsConcreteValue.number(1.0)))

        val result = backend.run(
            property = definition,
            configuration = PropertyRunConfiguration(
                numRuns = 1,
                examples = listOf(listOf(values)),
                observationRequest = observationRequest(definition, hash),
            ),
        )
        val correct = property(
            exportName = "correctUniqueOracle",
            sourceIdentity = definition.sourceIdentity,
        )
        val correctResult = backend.run(
            property = correct,
            configuration = PropertyRunConfiguration(
                numRuns = 1,
                examples = listOf(listOf(values)),
                observationRequest = observationRequest(correct, hash),
            ),
        )

        val artifact = assertNotNull(result.observations)
        val invocation = artifact.invocations.first()
        val correctInvocation = assertNotNull(correctResult.observations).invocations.first()
        assertEquals(ObservationPhase.EXPLICIT, invocation.phase)
        assertEquals(ObservationAdmission.ADMITTED, invocation.admission)
        assertEquals(ObservationOutcome.THREW, invocation.outcome)
        assertEquals(ObservationValueStatus.CAPTURED, invocation.input.status)
        assertEquals(listOf("input-array", "filtered-array"), invocation.points.map { it.pointId })
        assertEquals(values, invocation.points[0].value.value)
        assertEquals(values, invocation.points[1].value.value)
        assertEquals(ObservationOutcome.HOLDS, correctInvocation.outcome)
        assertEquals(
            JsConcreteValue.Array(listOf(JsConcreteValue.number(1.0))),
            correctInvocation.points[1].value.value,
        )
        assertEquals(0, artifact.droppedPoints)
        assertTrue(artifact.captureTimeMillis >= 0)
        assertTrue(artifact.invocations.drop(1).all { it.phase == ObservationPhase.SHRINK })

        val replayPath = assertNotNull(result.replayPath)
        val replay = backend.run(
            property = definition,
            configuration = PropertyRunConfiguration(
                seed = result.seed,
                replayPath = replayPath,
                numRuns = 1,
                examples = listOf(listOf(values)),
                observationRequest = observationRequest(definition, hash),
            ),
        )

        assertTrue(assertNotNull(replay.observations).invocations.all { it.phase == ObservationPhase.REPLAY })

        val generated = backend.run(
            property = correct,
            configuration = PropertyRunConfiguration(
                seed = 42,
                numRuns = 3,
                observationRequest = observationRequest(correct, hash),
            ),
        )

        assertTrue(assertNotNull(generated.observations).invocations.all { it.phase == ObservationPhase.GENERATION })
    }

    @Test
    fun `changed selected source is rejected before import`() {
        val original = testResourcesRoot().resolve(MODULE)
        val hash = sourceHash(original)
        val root = Files.createTempDirectory("pbt-observation-stale-")
        val changed = root.resolve(MODULE)
        Files.createDirectories(changed.parent)
        Files.writeString(changed, Files.readString(original) + "\n// changed\n")
        val definition = property(exportName = "originalUniqueOracle", sourceIdentity = null)

        try {
            val error = kotlin.test.assertFailsWith<PbtBackendException> {
                FastCheckBackend(sourceRoots = listOf(root)).run(
                    property = definition,
                    configuration = PropertyRunConfiguration(
                        numRuns = 1,
                        observationRequest = observationRequest(definition, hash),
                    ),
                )
            }

            assertEquals("entrypoint.source-hash.mismatch", error.code)
        } finally {
            Files.delete(changed)
            Files.delete(changed.parent)
            Files.delete(changed.parent.parent)
            Files.delete(root)
        }
    }

    @Test
    fun `original fast-check uniqueness oracle retains its assertion and failure`() {
        val source = testResourcesRoot().resolve(MODULE)
        val sourceHash = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(source))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val definition = property(
            exportName = "originalUniqueOracle",
            sourceIdentity = PropertySourceIdentity(
                sourceSha256 = sourceHash,
                buildSha256 = sourceHash,
                buildScope = "direct TypeScript source input; transpiled output is not pinned",
            ),
        )
        val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))
        val explicitExample = listOf(
            JsConcreteValue.Array(
                listOf(
                    JsConcreteValue.number(1.0),
                    JsConcreteValue.number(1.0),
                ),
            ),
        )

        val manifest = PropertyManifestJson.decode(PropertyManifestJson.encode(definition.toManifest()))
        val failed = backend.run(definition, PropertyRunConfiguration(examples = listOf(explicitExample), numRuns = 1))
        val passed = backend.run(
            property(exportName = "correctUniqueOracle", sourceIdentity = definition.sourceIdentity),
            PropertyRunConfiguration(seed = 42, numRuns = 30),
        )

        assertEquals("fast-check.array-bias.unique", manifest.propertyId)
        assertEquals("no-duplicates", manifest.assertions.single().id)
        assertEquals(
            listOf("input-array", "filtered-array", "expected-set-size"),
            manifest.assertions.single().operands.map(PropertyOperand::id),
        )
        assertEquals(sourceHash, manifest.sourceIdentity?.sourceSha256)
        assertEquals(PropertyRunStatus.FAILURE, failed.status)
        assertEquals(PropertyFailureKind.PROPERTY, failed.failure?.kind)
        assertEquals("AssertionError", failed.failure?.errorName)
        assertNotNull(failed.counterexample)
        assertEquals(PropertyRunStatus.SUCCESS, passed.status)
    }

    private fun property(exportName: String, sourceIdentity: PropertySourceIdentity?): PropertyDefinition {
        val correct = exportName == "correctUniqueOracle"

        return PropertyDefinition(
            id = PropertyId("fast-check.array-bias.unique"),
            inputs = listOf(
                PropertyInput(
                    name = "values",
                    domain = ArrayDomain(IntegerDomain(min = 0, max = 10), minLength = 0, maxLength = 8),
                    generatorId = "fast-check.array-integers",
                ),
            ),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = exportName),
            assertions = listOf(
                PropertyAssertion(
                    id = "no-duplicates",
                    source = PropertySourcePoint(module = MODULE, line = if (correct) 34 else 20, column = 5),
                    testedCall = PropertySourcePoint(
                        module = MODULE,
                        line = if (correct) 31 else 18,
                        column = if (correct) 26 else 22,
                    ),
                    operands = listOf(
                        PropertyOperand(
                            id = "input-array",
                            source = PropertySourcePoint(module = MODULE, line = if (correct) 30 else 16, column = 5),
                        ),
                        PropertyOperand(
                            id = "filtered-array",
                            source = PropertySourcePoint(module = MODULE, line = if (correct) 32 else 19, column = 5),
                        ),
                        PropertyOperand(
                            id = "expected-set-size",
                            source = PropertySourcePoint(module = MODULE, line = if (correct) 34 else 20, column = 35),
                        ),
                    ),
                ),
            ),
            sourceIdentity = sourceIdentity,
        )
    }

    private fun observationRequest(definition: PropertyDefinition, hash: String): PropertyObservationRequest {
        val assertion = definition.assertions.single()
        val input = assertion.operands.first()
        val filtered = assertion.operands[1]

        return PropertyObservationRequest(
            points = listOf(
                PropertyObservationPoint(
                    id = input.id,
                    assertionId = assertion.id,
                    operandId = input.id,
                    source = input.source,
                    callSite = requireNotNull(assertion.testedCall),
                    kind = ObservationPointKind.ARGUMENT,
                    inputIndex = 0,
                ),
                PropertyObservationPoint(
                    id = filtered.id,
                    assertionId = assertion.id,
                    operandId = filtered.id,
                    source = filtered.source,
                    callSite = requireNotNull(assertion.testedCall),
                    kind = ObservationPointKind.INTERMEDIATE,
                ),
            ),
            sources = listOf(PropertyObservationSource(module = MODULE, sha256 = hash)),
        )
    }

    private fun sourceHash(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val MODULE = "properties/real/ArrayArbitraryProperty.ts"
    }
}
