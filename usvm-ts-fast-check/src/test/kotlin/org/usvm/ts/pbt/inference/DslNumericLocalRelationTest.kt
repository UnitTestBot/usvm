package org.usvm.ts.pbt.inference

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.dsl.CompiledDslProperty
import org.usvm.ts.pbt.dsl.DslNumericLocalCapture
import org.usvm.ts.pbt.dsl.PropertyTypeScriptCompiler
import org.usvm.ts.pbt.dsl.defineProperty
import org.usvm.ts.pbt.dsl.invoke
import org.usvm.ts.pbt.dsl.lessOrEqual
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsMappingDiagnostic
import org.usvm.ts.pbt.mapping.EtsMappingResult
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.ObservationValueStatus
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.observation.PropertyPointEvent
import org.usvm.ts.pbt.usvm.bindDslNumericLocalRelation
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DslNumericLocalRelationTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `compiler program and actual native receipt bind the result after its original numeric store`() {
        val fixture = prepare()
        val capture = assertNotNull(fixture.compiled.numericLocalCapture)

        val binding = bindDslNumericLocalRelation(
            fixture.compiled,
            fixture.mapping,
            fixture.inference,
            fixture.relation
        )

        assertEquals(ScalarRelationStatus.SAMPLED, fixture.relation.status)
        assertEquals(EtsMappingStatus.EXACT, binding.status, binding.diagnostics.toString())
        assertEquals(ObservationBuildStatus.UNVERIFIED, fixture.inference.buildStatus)
        assertEquals(capture.binding, binding.targets.single().compiler)
        assertEquals("result", binding.targets.single().local.name)
        assertEquals(0, binding.targets.single().inputIndex)
    }

    @Test
    fun `forged local ABI node kind and wrapper cannot become compiler authority through a source hash`() {
        val fixture = prepare()
        val compiled = fixture.compiled
        val capture = assertNotNull(compiled.numericLocalCapture)
        val badBindings = listOf(
            capture.binding.copy(localName = "other"),
            capture.binding.copy(abi = "other-callback"),
            capture.binding.copy(nodeId = capture.binding.nodeId + 1),
            capture.binding.copy(occurrence = 1),
            capture.binding.copy(predicateAssignment = capture.binding.companionAssignment),
        )
        val badCaptures = badBindings.map { binding ->
            capture.copy(binding = binding, result = capture.result.copy(compilerLocal = binding))
        } + capture.copy(result = capture.result.copy(kind = ObservationPointKind.INTERMEDIATE))
        val badPrograms = badCaptures.map { compiled.copy(numericLocalCapture = it) } + listOf(
            compiled.copy(typeScript = compiled.typeScript.replace("const result =", "let result =")),
            compiled.copy(resolvedModules = emptyMap()),
        )

        badPrograms.forEach { changed ->
            val binding = bindDslNumericLocalRelation(changed, fixture.mapping, fixture.inference, fixture.relation)

            assertEquals(EtsMappingStatus.UNSUPPORTED, binding.status)
            assertTrue(binding.targets.isEmpty())
            assertEquals("usvm.dsl-local.compiler-mismatch", binding.diagnostics.single().code)
        }
    }

    @Test
    fun `the selected original input can precede the result in a relation`() {
        val fixture = prepare()
        val original = fixture.relation
        val evidence = original.evidence.map { it.copy(left = it.right, right = it.left) }
        val reversed = original.copy(
            pair = ScalarObservationPair(original.pair.right, original.pair.left),
            evidence = evidence
        )
        val inference = fixture.inference.copy(relations = listOf(reversed))

        val binding = bindDslNumericLocalRelation(fixture.compiled, fixture.mapping, inference, reversed)

        assertEquals(EtsMappingStatus.EXACT, binding.status)
        assertEquals(false, binding.targets.single().resultIsLeft)
    }

    @Test
    fun `repeated nested out of order wrong context and unavailable receipts cannot activate exact guidance`() {
        val fixture = prepare()
        val relation = fixture.relation
        val repeated = relation.copy(
            evidence = relation.evidence.map { evidence ->
                val duplicate = evidence.left.copy(occurrence = 1)
                val invocation = evidence.invocation.copy(points = evidence.invocation.points + duplicate)

                evidence.copy(invocation = invocation)
            }
        )
        val nested = relation.copy(
            evidence = relation.evidence.map { evidence ->
                evidence.copy(invocation = evidence.invocation.copy(parentInvocationId = 1))
            }
        )
        val wrongContext = relation.copy(
            evidence = relation.evidence.map { evidence ->
                val original = assertNotNull(evidence.invocation.callSite)
                val invocation = evidence.invocation.copy(callSite = original.copy(line = original.line + 1))

                evidence.copy(invocation = invocation)
            }
        )
        val wrongInput = relation.copy(
            evidence = relation.evidence.map { evidence ->
                val input = evidence.invocation.input.copy(
                    value = JsConcreteValue.Array(listOf(JsConcreteValue.number(-1.0)))
                )

                evidence.copy(invocation = evidence.invocation.copy(input = input))
            }
        )
        val unavailable = relation.changedResult { event ->
            event.copy(value = event.value.copy(status = ObservationValueStatus.UNSUPPORTED, value = null))
        }
        val wrongTime = relation.changedResult { event -> event.copy(eventOrdinal = 0) }
        val wrongLocal = relation.changedResult { event ->
            val compiler = assertNotNull(event.compilerLocal)

            event.copy(compilerLocal = compiler.copy(localName = "other"))
        }

        listOf(repeated, nested, wrongContext, wrongInput, unavailable, wrongTime, wrongLocal).forEach { changed ->
            val inference = fixture.inference.copy(relations = listOf(changed))

            val binding = bindDslNumericLocalRelation(fixture.compiled, fixture.mapping, inference, changed)

            assertEquals(EtsMappingStatus.UNSUPPORTED, binding.status)
            assertTrue(binding.targets.isEmpty())
        }
    }

    @Test
    fun `changed selected wrapper or helper source cannot activate a stale challenge`() {
        val fixture = prepare()
        val generated = root.resolve("generated.ts")
        Files.writeString(generated, fixture.compiled.typeScript + "\n// changed selected source\n")

        val staleWrapper = bindDslNumericLocalRelation(
            fixture.compiled,
            fixture.mapping,
            fixture.inference,
            fixture.relation
        )
        Files.writeString(generated, fixture.compiled.typeScript)
        Files.writeString(
            root.resolve("functions.ts"),
            "export function transform(input: number): number { return input; }"
        )
        val staleHelper = bindDslNumericLocalRelation(
            fixture.compiled,
            fixture.mapping,
            fixture.inference,
            fixture.relation
        )

        assertEquals(EtsMappingStatus.UNSUPPORTED, staleWrapper.status)
        assertEquals(EtsMappingStatus.UNSUPPORTED, staleHelper.status)
        assertTrue(staleWrapper.diagnostics.any { it.code == "usvm.dsl-local.source-stale" })
        assertTrue(staleHelper.diagnostics.any { it.code == "usvm.dsl-local.source-stale" })
    }

    @Test
    fun `ambiguous entries and missing source remain distinct from an exact point`() {
        val fixture = prepare()
        val predicate = fixture.mapping.predicate
        val diagnostic = EtsMappingDiagnostic(code = "test.ambiguous-entry", message = "Two original entries")
        val ambiguousPredicate = EtsMappingResult(
            status = EtsMappingStatus.AMBIGUOUS,
            targets = predicate.targets + predicate.targets,
            diagnostics = listOf(diagnostic),
        )
        val ambiguousMapping = fixture.mapping.copy(predicate = ambiguousPredicate)

        val ambiguous = bindDslNumericLocalRelation(
            fixture.compiled,
            ambiguousMapping,
            fixture.inference,
            fixture.relation
        )
        Files.delete(root.resolve("generated.ts"))
        val missing = bindDslNumericLocalRelation(
            fixture.compiled,
            fixture.mapping,
            fixture.inference,
            fixture.relation
        )

        assertEquals(EtsMappingStatus.AMBIGUOUS, ambiguous.status)
        assertEquals(2, ambiguous.targets.size)
        assertEquals(EtsMappingStatus.UNMAPPED, missing.status)
        assertTrue(missing.targets.isEmpty())
    }

    private fun ScalarRelation.changedResult(
        change: (PropertyPointEvent) -> PropertyPointEvent,
    ): ScalarRelation {
        val changed = evidence.map { evidence ->
            val result = change(evidence.left)
            val points = evidence.invocation.points.map { event -> if (event == evidence.left) result else event }

            evidence.copy(left = result, invocation = evidence.invocation.copy(points = points))
        }

        return copy(evidence = changed)
    }

    private fun prepare(): Fixture {
        val compiled = compile()
        val capture = assertNotNull(compiled.numericLocalCapture)
        val sources = capture.observationRequest().sources + source(module = "functions.ts")
        val request = capture.observationRequest().copy(sources = sources)
        val observation = FastCheckBackend(sourceRoots = listOf(root)).run(
            compiled.definition,
            PropertyRunConfiguration(seed = 42, numRuns = 8, observationRequest = request),
        )
        val inference = inferScalarRelations(
            compiled.definition,
            assertNotNull(observation.observations),
            listOf(ScalarObservationPair(capture.result, capture.input)),
        )
        val relation = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val files = listOf("functions.ts", "generated.ts").map { module ->
            loadEtsFileAutoConvert(root.resolve(module), provider = EtsIrProvider.TS_FRONTEND)
        }
        val scene = EtsScene(files)
        val mapping = PropertyEtsMapper(scene, sourceRoots = listOf(root)).map(compiled.definition.toManifest())

        return Fixture(compiled, mapping, inference, relation)
    }

    private fun source(module: String): PropertyObservationSource {
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(module)))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

        return PropertyObservationSource(module, hash)
    }

    private fun compile(): CompiledDslProperty {
        val source = """
            export function transform(input: number): number {
                if (input === 417) return input - 1;
                return input;
            }
        """.trimIndent()
        Files.writeString(root.resolve("functions.ts"), source)
        val program = defineProperty(id = "dsl.local-relation") {
            val transform = function<(Double) -> Double>(module = "functions.ts", exportName = "transform")
            val input by int(min = 0, max = 1000)
            val result by value(transform(input))

            check(name = "bound") { result lessOrEqual input }
        }
        val compiled = PropertyTypeScriptCompiler().compile(
            program,
            generatedModule = "generated.ts",
            resolveModule = { "./$it" },
            numericLocalCapture = DslNumericLocalCapture(localName = "result", checkName = "bound"),
        )
        Files.writeString(root.resolve("generated.ts"), compiled.typeScript)

        return compiled
    }

    private data class Fixture(
        val compiled: CompiledDslProperty,
        val mapping: PropertyEtsMappingArtifact,
        val inference: ScalarRelationInference,
        val relation: ScalarRelation,
    )
}
