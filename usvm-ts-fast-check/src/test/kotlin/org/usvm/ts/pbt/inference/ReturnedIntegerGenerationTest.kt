package org.usvm.ts.pbt.inference

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.ReturnedInputGeneration
import org.usvm.ts.pbt.backend.ReturnedInputSeed
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.observation.ObservationAdmission
import org.usvm.ts.pbt.observation.ObservationOrigin
import org.usvm.ts.pbt.observation.ObservationOutcome
import org.usvm.ts.pbt.observation.ObservationPhase
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReturnedIntegerGenerationTest {
    private val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))
    private val inputs = listOf(JsConcreteValue.number(500.0), JsConcreteValue.number(900.0))
    private val configuration = PropertyRunConfiguration(seed = 42, numRuns = 16, observationRequest = observations())

    @Test
    fun `native admitted passing input changes fresh generation without changing support or oracle`() {
        val property = property()
        val confirmed = backend.run(property, configuration.copy(numRuns = 1, examples = listOf(inputs)))
        val source = assertNotNull(confirmed.observations)
        val invocation = source.invocations.first()
        assertEquals(PropertyRunStatus.SUCCESS, confirmed.status)
        assertEquals(ObservationAdmission.ADMITTED, invocation.admission)
        assertEquals(ObservationOutcome.HOLDS, invocation.outcome)
        val returned = ReturnedInputSeed(
            id = "admitted-passing-seed",
            inputs = inputs,
            sourceRunId = source.runId,
            sourceInvocationId = invocation.invocationId,
        )

        val control = backend.run(property, configuration)
        val run = backend.run(
            property,
            configuration.copy(returnedInputGeneration = ReturnedInputGeneration(seeds = listOf(returned), radius = 2)),
        )
        val artifact = assertNotNull(run.observations)
        val ordinary = artifact.invocations.filter { it.origin == ObservationOrigin.FAST_CHECK }
        val descendants = artifact.invocations.filter { it.origin == ObservationOrigin.RETURNED_NEIGHBORHOOD }
        val feedback = artifact.invocations.filter { it.returnedInput != null }

        assertEquals(PropertyRunStatus.SUCCESS, run.status)
        assertTrue(ordinary.isNotEmpty())
        assertTrue(descendants.any { it.admission == ObservationAdmission.ADMITTED })
        assertTrue(artifact.invocations.any { it.admission == ObservationAdmission.REJECTED })
        assertTrue(feedback.all { it.phase == ObservationPhase.GENERATION })
        assertTrue(feedback.all { it.returnedInput?.sourceRunId == source.runId })
        assertTrue(feedback.all { it.returnedInput?.seedId == returned.id })
        assertTrue(feedback.all { it.returnedInput?.sourceInvocationId == invocation.invocationId })
        assertTrue(descendants.all { it.input.value != JsConcreteValue.Array(inputs) })
        assertTrue(artifact.invocations.all { sample ->
            val values = (sample.input.value as JsConcreteValue.Array).elements
            values.zip(property.inputs).all { (value, input) -> value in input.domain }
        })
        assertTrue(feedback.all { sample ->
            val values = (sample.input.value as JsConcreteValue.Array).elements
                .map { (it as JsConcreteValue.Number).toDouble() }
            values[0] in 498.0..502.0 && values[1] in 898.0..902.0
        })
        assertTrue(assertNotNull(control.observations).invocations.all { it.returnedInput == null })
        assertTrue(artifact.invocations.map { it.input } != control.observations?.invocations?.map { it.input })
        assertEquals(0, artifact.droppedInvocations)
        assertEquals(0, artifact.droppedPoints)

        val feedbackOnly = artifact.copy(invocations = feedback)
        val inference = inferScalarRelations(property, feedbackOnly, listOf(ScalarObservationPair(LEFT, RIGHT)))
        val order = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
        assertEquals(ScalarRelationStatus.INSUFFICIENT, order.status)
        assertTrue(order.distinctGenerationInputs.isEmpty())
        assertTrue(order.evidence.isNotEmpty())
    }

    @Test
    fun `returned native shrink and replay preserve correlation and the original failing input`() {
        val returned = ReturnedInputSeed(
            id = "seed",
            inputs = inputs,
            sourceRunId = "previous-native-run",
            sourceInvocationId = 0,
        )
        val configuration = configuration.copy(
            numRuns = 64,
            returnedInputGeneration = ReturnedInputGeneration(seeds = listOf(returned), radius = 2),
        )
        val property = property().copy(predicate = TypeScriptEntryPoint(module = MODULE, exportName = "failsNearby"))

        val failure = backend.run(property, configuration)
        val replay = backend.run(property, configuration.copy(seed = failure.seed, replayPath = failure.replayPath))

        assertEquals(PropertyRunStatus.FAILURE, failure.status)
        assertEquals(failure.counterexample, replay.counterexample)
        val original = assertNotNull(failure.observations).invocations
        assertTrue(original.any { it.phase == ObservationPhase.SHRINK && it.returnedInput?.seedId == returned.id })
        assertTrue(assertNotNull(replay.observations).invocations.all { it.phase == ObservationPhase.REPLAY })
        assertTrue(replay.observations?.invocations?.any { it.returnedInput?.seedId == returned.id } == true)
    }

    @Test
    fun `unsupported numeric domains and invalid seed support fail explicitly`() {
        val returned = ReturnedInputSeed(id = "seed", inputs = inputs, sourceRunId = "previous", sourceInvocationId = 0)
        val returnedConfiguration = configuration.copy(
            returnedInputGeneration = ReturnedInputGeneration(seeds = listOf(returned)),
        )
        val unsupported = property().copy(
            inputs = property().inputs.map { it.copy(domain = NumberDomain()) },
        )

        val error = assertFailsWith<PbtBackendException> { backend.run(unsupported, returnedConfiguration) }
        val missingObservations = assertFailsWith<PbtBackendException> {
            backend.run(property(), returnedConfiguration.copy(observationRequest = null))
        }
        val invalid = assertFailsWith<PbtBackendException> {
            backend.run(
                property(),
                returnedConfiguration.copy(
                    returnedInputGeneration = ReturnedInputGeneration(
                        seeds = listOf(returned.copy(inputs = listOf(JsConcreteValue.number(-1.0), inputs[1])))),
                ),
            )
        }

        assertEquals("generation.returned.unsupported", error.code)
        assertEquals("generation.returned.invalid", missingObservations.code)
        assertEquals("generation.returned.invalid", invalid.code)
    }

    private fun property(): PropertyDefinition = PropertyDefinition(
        id = PropertyId("test.returned-integers"),
        inputs = listOf(
            PropertyInput(name = "left", domain = IntegerDomain(min = 0, max = 1000)),
            PropertyInput(name = "right", domain = IntegerDomain(min = 0, max = 1000)),
        ),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = "holds"),
        precondition = TypeScriptEntryPoint(module = MODULE, exportName = "admitted"),
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

    private fun observations(): PropertyObservationRequest {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(testResourcesRoot().resolve(MODULE)))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val sources = listOf(PropertyObservationSource(module = MODULE, sha256 = hash))

        return PropertyObservationRequest(points = listOf(LEFT, RIGHT), sources = sources)
    }

    private companion object {
        const val MODULE = "properties/inference/ReturnedIntegers.ts"
        val CALL = PropertySourcePoint(module = MODULE, line = 6, column = 5)
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
    }
}
