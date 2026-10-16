package org.usvm.ts.pbt.inference

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReductionStatus
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.dsl.CompiledDslProperty
import org.usvm.ts.pbt.dsl.DslNumericLocalCapture
import org.usvm.ts.pbt.dsl.PropertyTypeScriptCompiler
import org.usvm.ts.pbt.dsl.defineProperty
import org.usvm.ts.pbt.dsl.invoke
import org.usvm.ts.pbt.dsl.lessOrEqual
import org.usvm.ts.pbt.dsl.notEqualTo
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmDslNumericLocalRefutation
import org.usvm.ts.pbt.usvm.UsvmDslNumericLocalRefuter
import org.usvm.ts.pbt.usvm.UsvmRelationRefutationStatus
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class DslNumericLocalRefutationTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `sampled equality is refuted after the store while both full original replays hold`() {
        val fixture = prepare()

        val refutation = fixture.refute()
        assertEquals(
            UsvmRelationRefutationStatus.REFUTATION_REACHED,
            refutation.status,
            refutation.diagnostics.toString(),
        )
        val candidate = refutation.captureWitness()
        val native = backend().confirmWitness(fixture.compiled.definition, candidate, REPLAY)

        assertEquals(listOf(417.0), refutation.inputs?.map { (it as JsConcreteValue.Number).toDouble() })
        assertEquals(ObservationBuildStatus.UNVERIFIED, fixture.inference.buildStatus)
        assertEquals(WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION, native.status, native.diagnostic)
        assertEquals(WitnessOracleOutcome.HOLDS, native.originalReplay?.outcome)
        assertEquals(WitnessOracleOutcome.HOLDS, native.reducedReplay?.outcome)
        assertEquals(candidate.target.binding, native.contextualRefutations.single().binding)
    }

    @Test
    fun `complete original named check failure takes priority over the local hypothesis`() {
        val fixture = prepare(delta = "+ 1")

        val refutation = fixture.refute()
        val native = backend().confirmWitness(fixture.compiled.definition, refutation.captureWitness(), REPLAY)

        assertEquals(UsvmRelationRefutationStatus.REFUTATION_REACHED, refutation.status)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, native.status, native.diagnostic)
        assertEquals(WitnessOracleOutcome.FALSE, native.originalReplay?.outcome)
        assertEquals("bound", native.originalReplay?.failure?.checkId)
        assertEquals(WitnessOracleOutcome.FALSE, native.reducedReplay?.outcome)
        assertTrue(native.contextualRefutations.isNotEmpty())
    }

    @Test
    fun `original input on the left preserves the direction of a non symmetric numeric relation`() {
        val fixture = prepare()
        val order = fixture.inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
        val reversed = order.copy(
            pair = ScalarObservationPair(order.pair.right, order.pair.left),
            evidence = order.evidence.map { it.copy(left = it.right, right = it.left) },
        )
        val inference = fixture.inference.copy(relations = listOf(reversed))
        val selected = fixture.copy(inference = inference, relation = reversed)

        val refutation = selected.refute()
        val native = backend().confirmWitness(fixture.compiled.definition, refutation.captureWitness(), REPLAY)

        assertEquals(false, refutation.binding.targets.single().resultIsLeft)
        assertEquals(UsvmRelationRefutationStatus.REFUTATION_REACHED, refutation.status)
        assertEquals(WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION, native.status, native.diagnostic)
        assertEquals(WitnessOracleOutcome.HOLDS, native.originalReplay?.outcome)
    }

    @Test
    fun `native shrinking retains the local relation and complete original oracle`() {
        val fixture = prepare(condition = "input >= 417 && input <= 420")
        val inputs = listOf(JsConcreteValue.number(value = 420.0))
        val candidate = fixture.refute().captureWitness().copy(inputs = inputs)

        val native = backend().confirmWitness(fixture.compiled.definition, candidate, REPLAY)

        assertEquals(WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION, native.status, native.diagnostic)
        assertEquals(WitnessReductionStatus.EXHAUSTED, native.reduction.status)
        assertTrue(native.reduction.acceptedShrinks > 0)
        assertEquals(listOf(JsConcreteValue.number(value = 417.0)), native.reduction.inputs)
        assertEquals(WitnessOracleOutcome.HOLDS, native.originalReplay?.outcome)
        assertEquals(WitnessOracleOutcome.HOLDS, native.reducedReplay?.outcome)
        assertTrue(assertNotNull(native.reducedReplay).targetMatched)
    }

    @Test
    fun `original admission excludes the only local refutation and exhausted search returns no candidate`() {
        val fixture = prepare(excludeRefutation = true)

        val refutation = fixture.refute()

        assertEquals(UsvmRelationRefutationStatus.NO_REFUTATION_REACHED, refutation.status)
        assertNull(refutation.inputs)
    }

    @Test
    fun `an exhausted auxiliary prefix does not execute an unsupported original suffix`() {
        val fixture = prepare(condition = "false", sparseSuffix = true)

        val refutation = fixture.refute()

        assertEquals(UsvmRelationRefutationStatus.NO_REFUTATION_REACHED, refutation.status)
        assertTrue(refutation.diagnostics.isEmpty())
        assertNull(refutation.inputs)
    }

    @Test
    fun `runtime limitation before the selected store and timeout remain distinct from exhaustion`() {
        val fixture = prepare(sparsePrefix = true)
        val limited = fixture.refute()
        val refuter = UsvmDslNumericLocalRefuter(
            fixture.scene,
            machineOptions = UMachineOptions(solverType = SolverType.YICES, timeout = Duration.ZERO),
        )

        val timeout = refuter.refute(fixture.compiled, fixture.mapping, CONCRETE, fixture.inference, fixture.relation)

        assertEquals(UsvmRelationRefutationStatus.UNSUPPORTED, limited.status)
        assertTrue(limited.diagnostics.any { it.code == "usvm.runtime.array-index-growth" })
        assertNull(limited.inputs)
        assertEquals(UsvmRelationRefutationStatus.TIMEOUT, timeout.status)
        assertTrue(timeout.timedOut)
        assertNull(timeout.inputs)
    }

    @Test
    fun `a changed compiler receipt cannot confirm the target and retains the full original outcome`() {
        val fixture = prepare()
        val candidate = fixture.refute().captureWitness()
        val target = assertNotNull(candidate.target.dslNumericLocalRelation)
        val wrong = target.copy(compiler = target.compiler.copy(nodeId = target.compiler.nodeId + 1))
        val changed = candidate.copy(target = candidate.target.copy(dslNumericLocalRelation = wrong))

        val native = backend().confirmWitness(fixture.compiled.definition, changed, REPLAY)

        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, native.status, native.diagnostic)
        assertEquals(WitnessOracleOutcome.HOLDS, native.originalReplay?.outcome)
        assertEquals(false, native.originalReplay?.targetMatched)
        assertEquals(candidate.inputs, native.candidate.inputs)
        assertTrue(native.contextualRefutations.isEmpty())
    }

    private fun Fixture.refute(): UsvmDslNumericLocalRefutation = UsvmDslNumericLocalRefuter(scene).refute(
        compiled,
        mapping,
        CONCRETE,
        inference,
        relation,
    )

    private fun backend(): FastCheckBackend = FastCheckBackend(sourceRoots = listOf(root))

    private fun prepare(
        delta: String = "- 1",
        excludeRefutation: Boolean = false,
        sparsePrefix: Boolean = false,
        condition: String = "input === 417",
        sparseSuffix: Boolean = false,
    ): Fixture {
        val prefix = if (sparsePrefix) "const cache: number[] = []; cache[1001] = input;" else ""
        val source = """
            export function transform(input: number): number {
                $prefix
                if ($condition) return input $delta;
                return input;
            }
            export function suffix(input: number): number {
                const cache: number[] = [];
                cache[1001] = input;
                return input;
            }
        """.trimIndent()
        Files.writeString(root.resolve("functions.ts"), source)
        val program = defineProperty(id = "dsl.local-refutation") {
            val transform = function<(Double) -> Double>(module = "functions.ts", exportName = "transform")
            val suffix = function<(Double) -> Double>(module = "functions.ts", exportName = "suffix")
            val input by int(min = 0, max = 1000)
            if (excludeRefutation) assume { input notEqualTo 417 }
            val result by value(transform(input))

            check(name = "bound") { result lessOrEqual input }
            if (sparseSuffix) check(name = "suffix") { suffix(result) lessOrEqual input }
        }
        val compiled = PropertyTypeScriptCompiler().compile(
            program,
            generatedModule = "generated.ts",
            resolveModule = { "./$it" },
            numericLocalCapture = DslNumericLocalCapture(localName = "result", checkName = "bound"),
        )
        Files.writeString(root.resolve("generated.ts"), compiled.typeScript)
        val capture = assertNotNull(compiled.numericLocalCapture)
        val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val request = capture.observationRequest().let { request ->
            request.copy(sources = request.sources + PropertyObservationSource(module = "functions.ts", sha256 = hash))
        }
        val observation = backend().run(
            compiled.definition,
            PropertyRunConfiguration(seed = 42, numRuns = 8, observationRequest = request),
        )
        val inference = inferScalarRelations(
            compiled.definition,
            assertNotNull(observation.observations),
            listOf(ScalarObservationPair(capture.result, capture.input)),
        )
        val relation = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val scene = loadEtsProjectAutoConvert(root, provider = EtsIrProvider.TS_FRONTEND)
        val mapping = PropertyEtsMapper(scene, sourceRoots = listOf(root)).map(compiled.definition.toManifest())

        return Fixture(compiled, scene, mapping, inference, relation)
    }

    private data class Fixture(
        val compiled: CompiledDslProperty,
        val scene: EtsScene,
        val mapping: PropertyEtsMappingArtifact,
        val inference: ScalarRelationInference,
        val relation: ScalarRelation,
    )

    private companion object {
        val CONCRETE = ProjectionCapability(level = ProjectionLevel.EXACT)
        val REPLAY = WitnessReplayConfiguration(timeoutMillis = 15_000, validationReserveMillis = 3_000)
    }
}
