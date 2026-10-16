package org.usvm.ts.pbt.dsl

import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.WitnessBinding
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReductionStatus
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearcher
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DslExternalWitnessTest {
    @TempDir
    lateinit var directory: Path

    private val configuration = WitnessReplayConfiguration(timeoutMillis = 15_000, validationReserveMillis = 3_000)

    @Test
    fun `compiled DSL symbolic inputs confirm the explicitly selected original check`() {
        val compiled = compile(threshold(includeEarlierCheck = false))
        val property = compiled.definition
        val roots = listOf(directory)
        val manifest = property.toManifest()
        val scene = loadEtsProjectAutoConvert(directory, provider = EtsIrProvider.TS_FRONTEND)
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = roots).map(manifest)
        val searcher = UsvmPropertySearcher(
            scene = scene,
            machineOptions = UMachineOptions(solverType = SolverType.YICES, timeout = 10.seconds),
        )

        val symbolic = searcher.search(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
        )
        val candidate = property.captureWitness(
            inputs = assertNotNull(symbolic.inputs),
            target = WitnessTarget(id = "threshold", expectedOutcome = WitnessOracleOutcome.FALSE),
            sources = sources(),
            provenance = "usvm:${symbolic.status}:${symbolic.target}",
        )
        val evidence = FastCheckBackend(sourceRoots = roots).confirmWitness(
            property = property,
            candidate = candidate,
            configuration = configuration,
        )

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, symbolic.status)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, evidence.status, evidence.diagnostic)
        assertEquals(candidate, evidence.candidate)
        assertEquals("threshold", evidence.originalReplay?.failure?.checkId)
        assertEquals(WitnessOracleOutcome.FALSE, evidence.originalReplay?.outcome)
        assertEquals("threshold", evidence.reducedReplay?.failure?.checkId)
        assertEquals(WitnessReductionStatus.EXHAUSTED, evidence.reduction.status)
    }

    @Test
    fun `external DSL witness actually shrinks without switching to an earlier failed check`() {
        val compiled = compile(threshold(includeEarlierCheck = true))

        val evidence = confirm(compiled, value = 80.0)

        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, evidence.status, evidence.diagnostic)
        assertEquals(inputs(value = 80.0), evidence.candidate.inputs)
        assertEquals(inputs(value = 15.0), evidence.reduction.inputs)
        assertEquals(WitnessReductionStatus.EXHAUSTED, evidence.reduction.status)
        assertTrue(evidence.reduction.acceptedShrinks > 0)
        assertEquals("threshold", evidence.originalReplay?.failure?.checkId)
        assertEquals(JsConcreteValue.number(81.0), evidence.originalReplay?.failure?.actual)
        assertEquals(JsConcreteValue.number(80.0), evidence.originalReplay?.failure?.expected)
        assertEquals("threshold", evidence.reducedReplay?.failure?.checkId)
        assertEquals(JsConcreteValue.number(16.0), evidence.reducedReplay?.failure?.actual)
        assertEquals(JsConcreteValue.number(15.0), evidence.reducedReplay?.failure?.expected)
        assertTrue(assertNotNull(evidence.reducedReplay).targetMatched)
    }

    @Test
    fun `wrong check domain and precondition do not confirm the selected DSL check`() {
        val compiled = compile(threshold(includeEarlierCheck = true))
        val cases = listOf(
            5.0 to WitnessReplayStatus.TARGET_MISMATCH,
            0.0 to WitnessReplayStatus.PRECONDITION_REJECTED,
            101.0 to WitnessReplayStatus.DOMAIN_REJECTED,
        )

        for ((value, status) in cases) {
            val evidence = confirm(compiled, value = value)

            assertEquals(status, evidence.status, "input=$value: ${evidence.diagnostic}")
            assertEquals(WitnessReductionStatus.NOT_ELIGIBLE, evidence.reduction.status)
            assertFalse(evidence.originalReplay?.targetMatched == true)
        }
    }

    @Test
    fun `named exceptions remain distinct from false and preserve the original exception while shrinking`() {
        val program = defineProperty(id = "witness.dsl-exception") {
            val fail = function<(Double) -> Double>(module = "subject.ts", exportName = "fail")
            val value by int(min = 0, max = 100)

            check(name = "threshold") { fail(value) lessThan 15 }
        }
        val compiled = compile(program)

        val wrongOutcome = confirm(compiled, value = 80.0)
        val evidence = confirm(compiled, value = 80.0, outcome = WitnessOracleOutcome.THREW)

        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, wrongOutcome.status)
        assertEquals(WitnessOracleOutcome.THREW, wrongOutcome.originalReplay?.outcome)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, evidence.status, evidence.diagnostic)
        assertEquals(inputs(value = 15.0), evidence.reduction.inputs)
        assertTrue(evidence.reduction.acceptedShrinks > 0)
        val failure = assertNotNull(evidence.reducedReplay?.failure)
        assertEquals("threshold", failure.checkId)
        assertEquals("TypeError", failure.errorName)
        assertEquals("original exception", failure.message)
        assertNull(failure.actual)
        assertNull(failure.expected)
    }

    @Test
    fun `exception before a named check cannot impersonate that check`() {
        val program = defineProperty(id = "witness.dsl-local-exception") {
            val fail = function<(Double) -> Double>(module = "subject.ts", exportName = "fail")
            val value by int(min = 0, max = 100)
            val result by value(fail(value))

            check(name = "threshold") { result lessThan 15 }
        }
        val compiled = compile(program)

        val evidence = confirm(compiled, value = 80.0, outcome = WitnessOracleOutcome.THREW)

        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, evidence.status)
        assertEquals(WitnessOracleOutcome.THREW, evidence.originalReplay?.outcome)
        assertEquals("TypeError", evidence.originalReplay?.failure?.errorName)
        assertNull(evidence.originalReplay?.failure?.checkId)
        assertEquals(WitnessReductionStatus.NOT_ELIGIBLE, evidence.reduction.status)
    }

    @Test
    fun `named target without a diagnostic companion still requires an executable binding`() {
        val compiled = compile(threshold(includeEarlierCheck = false))
        val property = compiled.definition.copy(
            predicate = compiled.definition.predicate.copy(checkResultExportName = null),
        )
        val withoutCompanion = compiled.copy(definition = property)

        val evidence = confirm(withoutCompanion, value = 80.0)

        assertEquals(WitnessReplayStatus.UNSUPPORTED_REPLAY, evidence.status)
        assertNull(evidence.originalReplay)
    }

    @Test
    fun `diagnostic companion does not satisfy a contextual witness binding`() {
        val compiled = compile(threshold(includeEarlierCheck = false))
        val candidate = compiled.definition.captureWitness(
            inputs = inputs(value = 80.0),
            target = WitnessTarget(
                id = "threshold",
                binding = WitnessBinding(pointId = "threshold", contextId = "selected-context", revision = "v1"),
            ),
            sources = sources(),
            provenance = "contextual-fixture",
        )

        val evidence = FastCheckBackend(sourceRoots = listOf(directory)).confirmWitness(
            property = compiled.definition,
            candidate = candidate,
            configuration = configuration,
        )

        assertEquals(WitnessReplayStatus.TARGET_MISMATCH, evidence.status)
        assertEquals("threshold", evidence.originalReplay?.failure?.checkId)
        assertFalse(assertNotNull(evidence.originalReplay).targetMatched)
    }

    private fun threshold(includeEarlierCheck: Boolean): DslProperty = defineProperty(id = "witness.dsl-threshold") {
        val threshold = function<(Double) -> Double>(module = "subject.ts", exportName = "threshold")
        val value by int(min = 0, max = 100)

        assume { value greaterOrEqual 5 }
        if (includeEarlierCheck) check(name = "earlier") { value greaterOrEqual 10 }
        check(name = "threshold") { threshold(value) equalTo value }
    }

    private fun compile(program: DslProperty): CompiledDslProperty {
        Files.writeString(directory.resolve("subject.ts"), SUBJECT)
        val compiled = PropertyTypeScriptCompiler().compile(
            program = program,
            generatedModule = GENERATED_MODULE,
            resolveModule = { module -> "./$module" },
        )
        Files.writeString(directory.resolve(GENERATED_MODULE), compiled.typeScript)

        return compiled
    }

    private fun confirm(
        compiled: CompiledDslProperty,
        value: Double,
        outcome: WitnessOracleOutcome = WitnessOracleOutcome.FALSE,
    ) = FastCheckBackend(sourceRoots = listOf(directory)).confirmWitness(
        property = compiled.definition,
        candidate = compiled.definition.captureWitness(
            inputs = inputs(value),
            target = WitnessTarget(id = "threshold", expectedOutcome = outcome),
            sources = sources(),
            provenance = "external-dsl-fixture",
        ),
        configuration = configuration,
    )

    private fun sources(): List<PropertyObservationSource> = listOf(GENERATED_MODULE, "subject.ts").map { module ->
        val bytes = Files.readAllBytes(directory.resolve(module))
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        PropertyObservationSource(module = module, sha256 = hash)
    }

    private fun inputs(value: Double): List<JsConcreteValue> = listOf(JsConcreteValue.number(value))

    private companion object {
        const val GENERATED_MODULE = "property.ts"
        val SUBJECT = """
            export function threshold(value: number): number {
                if (value < 15) return value;
                return value + 1;
            }
            export function fail(value: number): number {
                if (value < 15) throw new TypeError('different exception');
                throw new TypeError('original exception');
            }
        """.trimIndent()
    }
}
