package org.usvm.ts.pbt.dsl

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.inference.ScalarObservationPair
import org.usvm.ts.pbt.inference.ScalarRelationLimitation
import org.usvm.ts.pbt.inference.ScalarRelationStatus
import org.usvm.ts.pbt.inference.ScalarRelationTemplate
import org.usvm.ts.pbt.inference.inferScalarRelations
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.ObservationValueStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DslNumericLocalCaptureTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `real frontend retains original predicate and companion shared assignments`() {
        val compiled = compile()
        val binding = assertNotNull(compiled.numericLocalCapture).binding

        val file = loadEtsFileAutoConvert(root.resolve("generated.ts"), provider = EtsIrProvider.TS_FRONTEND)
        val scene = EtsScene(listOf(file))
        val mapping = PropertyEtsMapper(scene, sourceRoots = listOf(root)).map(compiled.definition.toManifest())
        val predicate = mapping.predicate.targets.single().method
        val owner = assertNotNull(predicate.enclosingClass)
        val companion = owner.methods.single { it.name == binding.companionExportName }

        assertEquals(EtsMappingStatus.EXACT, mapping.predicate.status)
        listOf(predicate, companion).forEach { method ->
            val selected = method.cfg.instructions.filterIsInstance<EtsAssignStmt>().filter { assignment ->
                (assignment.lhv as? EtsLocal)?.name == binding.localName
            }

            assertEquals(1, selected.size)
        }
    }

    @Test
    fun `native generation captures the compiler local and entry input for shared inference`() {
        val compiled = compile()
        val capture = assertNotNull(compiled.numericLocalCapture)

        val result = FastCheckBackend(sourceRoots = listOf(root)).run(
            compiled.definition,
            configuration(capture),
        )
        val observations = assertNotNull(result.observations)
        val inference = inferScalarRelations(
            compiled.definition,
            observations,
            listOf(ScalarObservationPair(capture.result, capture.input)),
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertEquals(ObservationBuildStatus.UNVERIFIED, observations.buildStatus)
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        assertEquals(ScalarRelationStatus.SAMPLED, equality.status)
        assertTrue(equality.distinctGenerationInputs.size >= 2)
        observations.invocations.forEach { invocation ->
            val value = invocation.points.single { it.pointId == capture.result.id }
            val input = invocation.points.single { it.pointId == capture.input.id }

            assertEquals(capture.binding, value.compilerLocal)
            assertEquals(ObservationValueStatus.CAPTURED, value.value.status)
            assertEquals(input.value.value, value.value.value)
            assertEquals(0, value.occurrence)
            assertTrue(input.eventOrdinal < value.eventOrdinal)
        }
    }

    @Test
    fun `ordinary predicate and diagnostic companion reuse one computed value across checks`() {
        val compiled = compile()
        val ordinary = compiled.definition.copy(
            predicate = compiled.definition.predicate.copy(checkResultExportName = null),
        )
        val backend = FastCheckBackend(sourceRoots = listOf(root))

        val diagnosticResult = backend.run(
            compiled.definition,
            configuration(assertNotNull(compiled.numericLocalCapture))
        )
        val ordinaryResult = backend.run(ordinary, PropertyRunConfiguration(seed = 42, numRuns = 8))

        assertEquals(PropertyRunStatus.SUCCESS, diagnosticResult.status)
        assertEquals(PropertyRunStatus.SUCCESS, ordinaryResult.status)
    }

    @Test
    fun `repeated nested and mismatched compiler receipts cannot supply sampled guidance`() {
        val compiled = compile()
        val capture = assertNotNull(compiled.numericLocalCapture)
        val run = FastCheckBackend(sourceRoots = listOf(root)).run(compiled.definition, configuration(capture))
        val original = assertNotNull(run.observations)
        val pairs = listOf(ScalarObservationPair(capture.result, capture.input))
        val repeatedInvocations = original.invocations.map { invocation ->
            val value = invocation.points.single { it.pointId == capture.result.id }

            invocation.copy(points = invocation.points + value.copy(occurrence = 1))
        }
        val nestedInvocations = original.invocations.map { it.copy(parentInvocationId = 1) }
        val wrongNodeInvocations = original.invocations.map { invocation ->
            val points = invocation.points.map { event ->
                if (event.compilerLocal == null) {
                    event
                } else {
                    event.copy(compilerLocal = capture.binding.copy(nodeId = capture.binding.nodeId + 1))
                }
            }

            invocation.copy(points = points)
        }

        val cases = listOf(
            original.copy(invocations = repeatedInvocations) to ScalarRelationLimitation.REPEATED_POINT,
            original.copy(invocations = nestedInvocations) to ScalarRelationLimitation.UNSUPPORTED_CONTEXT,
            original.copy(invocations = wrongNodeInvocations) to ScalarRelationLimitation.POINT_IDENTITY_MISMATCH,
        )
        cases.forEach { (observations, limitation) ->
            val inference = inferScalarRelations(compiled.definition, observations, pairs)

            assertTrue(inference.relations.all { it.status == ScalarRelationStatus.UNSUPPORTED })
            assertTrue(inference.relations.all { limitation in it.limitations })
        }
    }

    private fun configuration(capture: CompiledDslNumericLocalCapture): PropertyRunConfiguration =
        PropertyRunConfiguration(seed = 42, numRuns = 8, observationRequest = capture.observationRequest())

    private fun compile(): CompiledDslProperty {
        val source = """
            let calls = 0;
            export function reset(value: number): number { calls = 0; return value; }
            export function counted(value: number): number { calls += 1; return value; }
            export function count(value: number): number { return calls; }
        """.trimIndent()
        Files.writeString(root.resolve("calls.ts"), source)
        val program = defineProperty(id = "dsl.local-capture") {
            val reset = function<(Double) -> Double>(module = "calls.ts", exportName = "reset")
            val counted = function<(Double) -> Double>(module = "calls.ts", exportName = "counted")
            val count = function<(Double) -> Double>(module = "calls.ts", exportName = "count")
            val input by int(min = 1, max = 4)
            val initial by value(reset(input))
            val result by value(counted(initial))

            check(name = "bound") { result lessOrEqual input }
            check(name = "identity") { result equalTo input }
            check(name = "once") { count(input) equalTo 1 }
        }
        val compiled = PropertyTypeScriptCompiler().compile(
            program = program,
            generatedModule = "generated.ts",
            resolveModule = { "./$it" },
            numericLocalCapture = DslNumericLocalCapture(localName = "result", checkName = "bound"),
        )
        Files.writeString(root.resolve("generated.ts"), compiled.typeScript)

        return compiled
    }
}
