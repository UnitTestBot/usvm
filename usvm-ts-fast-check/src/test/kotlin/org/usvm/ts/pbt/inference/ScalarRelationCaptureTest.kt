package org.usvm.ts.pbt.inference

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourceIdentity
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScalarRelationCaptureTest {
    @Test
    fun `original FastCheck generation observations supply a numeric order hypothesis`() {
        val source = testResourcesRoot().resolve(MODULE)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(source))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val callSite = PropertySourcePoint(module = MODULE, line = 3, column = 43)
        val input = PropertyObservationPoint(
            id = "input",
            assertionId = "non-negative",
            operandId = "input",
            source = PropertySourcePoint(module = MODULE, line = 2, column = 11),
            callSite = callSite,
            kind = ObservationPointKind.ARGUMENT,
            inputIndex = 0,
        )
        val result = PropertyObservationPoint(
            id = "result",
            assertionId = "non-negative",
            operandId = "result",
            source = PropertySourcePoint(module = MODULE, line = 3, column = 11),
            callSite = callSite,
            kind = ObservationPointKind.RETURN,
        )
        val property = PropertyDefinition(
            id = PropertyId("test.magnitude"),
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -1, max = 0))),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = "nonNegativeMagnitude"),
            assertions = listOf(
                PropertyAssertion(
                    id = "non-negative",
                    source = PropertySourcePoint(module = MODULE, line = 5, column = 5),
                    testedCall = callSite,
                    operands = listOf(
                        PropertyOperand(id = input.operandId, source = input.source),
                        PropertyOperand(id = result.operandId, source = result.source),
                    ),
                ),
            ),
            sourceIdentity = PropertySourceIdentity(
                sourceSha256 = hash,
                buildSha256 = hash,
                buildScope = "direct TypeScript source; executable build unverified",
            ),
        )
        val sources = listOf(PropertyObservationSource(module = MODULE, sha256 = hash))
        val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))

        val run = backend.run(
            property,
            PropertyRunConfiguration(
                seed = 42,
                numRuns = 16,
                observationRequest = PropertyObservationRequest(points = listOf(input, result), sources = sources),
            ),
        )
        val observations = assertNotNull(run.observations)
        val inference = inferScalarRelations(property, observations, listOf(ScalarObservationPair(input, result)))
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val order = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }

        assertEquals(PropertyRunStatus.SUCCESS, run.status)
        assertEquals(sources, inference.sources)
        assertEquals(ObservationBuildStatus.UNVERIFIED, inference.buildStatus)
        assertEquals(ScalarRelationStatus.REFUTED, equality.status)
        assertEquals(ScalarRelationStatus.SAMPLED, order.status)
        assertEquals(2, order.distinctGenerationInputs.size)
        assertTrue(order.evidence.all { it.holds })
    }

    private companion object {
        const val MODULE = "properties/inference/ScalarRelations.ts"
    }
}
