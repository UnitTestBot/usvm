package org.usvm.ts.pbt.inference

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.backend.CapabilityDiagnostic
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReplayConfiguration
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessRole
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.observation.PropertyObservationRequest
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmEntryRefutationStatus
import org.usvm.ts.pbt.usvm.UsvmEntryRelationRefutation
import org.usvm.ts.pbt.usvm.UsvmEntryRelationRefuter
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearcher
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class EntryRelationRefutationTest {
    private val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))

    @Test
    fun `unknown call in original admission retains its reason before the auxiliary target`() {
        val inference = observe(predicate = "reflexive", precondition = "sliceAdmission")
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }

        val refutation = refute(inference, equality)
        val restored = Json.decodeFromString<List<CapabilityDiagnostic>>(Json.encodeToString(refutation.diagnostics))

        assertEquals(UsvmEntryRefutationStatus.UNSUPPORTED, refutation.status)
        assertNull(refutation.inputs)
        val diagnostic = restored.single { it.code == "usvm.unknown-call.path-stopped" }
        assertTrue("Array::slice" in diagnostic.message, diagnostic.message)
        assertTrue("reason=PARTIAL_APPROXIMATION" in diagnostic.message, diagnostic.message)
        assertTrue(diagnostic.path.contains("EntryRelations.ts:39:"), diagnostic.path)
        assertEquals(refutation.diagnostics, restored)
    }

    @Test
    fun `an auxiliary equality refutation is confirmed while the complete original property holds`() {
        val inference = observe(predicate = "reflexive")
        val relation = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val manifest = inference.property.toManifest()
        val focused = UsvmPropertySearcher(scene).searchFocusedOnEntryRelation(
            manifest,
            mapper.map(manifest),
            CONCRETE,
            inference,
            relation,
        )

        val refutation = refute(inference, relation)
        val candidate = refutation.captureWitness()
        val native = backend.confirmWitness(inference.property, candidate, CONFIGURATION)

        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, focused.search?.status)
        assertEquals(UsvmEntryRefutationStatus.REFUTATION_REACHED, refutation.status)
        assertEquals(EtsMappingStatus.EXACT, refutation.binding.status)
        assertEquals(WitnessRole.HYPOTHESIS, candidate.target.role)
        assertEquals(ObservationBuildStatus.UNVERIFIED, refutation.inference.buildStatus)
        assertEquals(WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION, native.status, native.diagnostic)
        assertEquals(WitnessOracleOutcome.HOLDS, native.originalReplay?.outcome)
        assertEquals(WitnessOracleOutcome.HOLDS, native.reducedReplay?.outcome)
        assertEquals(refutation.inputs, native.candidate.inputs)
        assertEquals(candidate.target.binding, native.contextualRefutations.single().binding)
    }

    @Test
    fun `an original oracle violation has priority over a confirmed entry hypothesis refutation`() {
        val cases = listOf("ordered" to WitnessOracleOutcome.FALSE, "throwing" to WitnessOracleOutcome.THREW)
        for ((predicate, outcome) in cases) {
            val inference = observe(predicate = predicate)
            val order = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }

            val refutation = refute(inference, order)
            val native = backend.confirmWitness(inference.property, refutation.captureWitness(), CONFIGURATION)

            assertEquals(UsvmEntryRefutationStatus.REFUTATION_REACHED, refutation.status)
            assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, native.status, native.diagnostic)
            assertEquals(outcome, native.originalReplay?.outcome)
            assertTrue(assertNotNull(native.originalReplay).targetMatched)
            assertTrue(native.contextualRefutations.isNotEmpty())
        }
    }

    @Test
    fun `the complete original precondition can reject every proposed refutation`() {
        val inference = observe(predicate = "reflexive", precondition = "ordered")
        val order = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }

        val refutation = refute(inference, order)

        assertEquals(UsvmEntryRefutationStatus.PRECONDITION_REJECTED, refutation.status)
        assertNull(refutation.inputs)
    }

    @Test
    fun `a runtime bound inside original admission remains unsupported rather than absence of refutations`() {
        val inference = observe(predicate = "reflexive", precondition = "sparseAdmission")
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }

        val refutation = refute(inference, equality)

        assertEquals(UsvmEntryRefutationStatus.UNSUPPORTED, refutation.status)
        assertNull(refutation.inputs)
        assertTrue(refutation.diagnostics.any { it.code == "usvm.runtime.array-index-growth" })
    }

    @Test
    fun `same entry equality can be refuted by NaN while the original property still holds`() {
        val points = listOf(LEFT, RIGHT.copy(inputIndex = 0))
        val inference = observe(predicate = "always", leftDomain = NumberDomain(), points = points, seed = 2)
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }

        val refutation = refute(inference, equality)
        val native = backend.confirmWitness(inference.property, refutation.captureWitness(), CONFIGURATION)

        assertEquals(UsvmEntryRefutationStatus.REFUTATION_REACHED, refutation.status)
        val first = assertNotNull(refutation.inputs).first() as JsConcreteValue.Number
        assertTrue(first.toDouble().isNaN())
        assertEquals(WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION, native.status, native.diagnostic)
        assertEquals(WitnessOracleOutcome.HOLDS, native.originalReplay?.outcome)
    }

    @Test
    fun `stale observation sources cannot activate a challenge and stale native sources preserve the candidate`() {
        val inference = observe(predicate = "reflexive")
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val stale = listOf(PropertyObservationSource(module = MODULE, sha256 = "0".repeat(n = 64)))

        val staleBinding = refute(inference.copy(sources = stale), equality)
        val refutation = refute(inference, equality)
        val candidate = refutation.captureWitness().copy(sources = stale)
        val native = backend.confirmWitness(inference.property, candidate, CONFIGURATION)

        assertEquals(UsvmEntryRefutationStatus.UNSUPPORTED, staleBinding.status)
        assertNull(staleBinding.inputs)
        assertEquals(WitnessReplayStatus.SOURCE_MISMATCH, native.status)
        assertEquals(refutation.inputs, native.candidate.inputs)
    }

    @Test
    fun `a timed out auxiliary search is not an exhausted relation`() {
        val inference = observe(predicate = "reflexive")
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val limited = UsvmEntryRelationRefuter(
            scene,
            machineOptions = UMachineOptions(solverType = SolverType.YICES, timeout = Duration.ZERO),
        )
        val manifest = inference.property.toManifest()

        val refutation = limited.refute(manifest, mapper.map(manifest), CONCRETE, inference, equality)

        assertEquals(UsvmEntryRefutationStatus.TIMEOUT, refutation.status)
        assertTrue(refutation.timedOut)
        assertNull(refutation.inputs)
    }

    @Test
    fun `swapped positional slots cannot activate an entry auxiliary target`() {
        val inference = observe(predicate = "reflexive")
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val manifest = inference.property.toManifest()
        val mapping = mapper.map(manifest)
        val target = mapping.predicate.targets.single()
        val inputs = target.bindings.inputs
        val swapped = inputs.mapIndexed { index, input -> input.copy(stackSlot = inputs[1 - index].stackSlot) }
        val changedTarget = target.copy(bindings = target.bindings.copy(inputs = swapped))
        val changed = mapping.copy(predicate = mapping.predicate.copy(targets = listOf(changedTarget)))

        val refutation = UsvmEntryRelationRefuter(scene).refute(manifest, changed, CONCRETE, inference, equality)

        assertEquals(UsvmEntryRefutationStatus.UNSUPPORTED, refutation.status)
        assertEquals("usvm.entry-relation.slots-mismatch", refutation.binding.diagnostics.single().code)
        assertNull(refutation.inputs)
    }

    private fun refute(inference: ScalarRelationInference, relation: ScalarRelation): UsvmEntryRelationRefutation {
        val manifest = inference.property.toManifest()

        return UsvmEntryRelationRefuter(scene).refute(manifest, mapper.map(manifest), CONCRETE, inference, relation)
    }

    private fun observe(
        predicate: String,
        precondition: String? = null,
        leftDomain: PropertyDomain = IntegerDomain(min = 0, max = 2),
        points: List<PropertyObservationPoint> = listOf(LEFT, RIGHT),
        seed: Int = 1,
    ): ScalarRelationInference {
        val property = PropertyDefinition(
            id = PropertyId("entry.auxiliary"),
            inputs = listOf(
                PropertyInput(name = "left", domain = leftDomain),
                PropertyInput(name = "right", domain = IntegerDomain(min = 0, max = 2)),
            ),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = predicate),
            precondition = precondition?.let { TypeScriptEntryPoint(module = MODULE, exportName = it) },
            assertions = listOf(
                PropertyAssertion(
                    id = "original",
                    source = CALL,
                    testedCall = CALL,
                    operands = points.map { PropertyOperand(id = it.operandId, source = it.source) },
                ),
            ),
        )

        val result = backend.run(
            property,
            PropertyRunConfiguration(
                seed = seed,
                numRuns = 2,
                observationRequest = PropertyObservationRequest(points = points, sources = sources),
            ),
        )

        return inferScalarRelations(
            property,
            assertNotNull(result.observations),
            listOf(ScalarObservationPair(points[0], points[1]))
        )
    }

    companion object {
        private const val MODULE = "properties/inference/EntryRelations.ts"
        private val CALL = PropertySourcePoint(module = MODULE, line = 1, column = 1)
        private val LEFT = PropertyObservationPoint(
            id = "left",
            assertionId = "original",
            operandId = "left",
            source = CALL,
            callSite = CALL,
            kind = ObservationPointKind.ENTRY_ARGUMENT,
            inputIndex = 0,
        )
        private val RIGHT = LEFT.copy(id = "right", operandId = "right", inputIndex = 1)
        private val CONCRETE = ProjectionCapability(level = ProjectionLevel.EXACT)
        private val CONFIGURATION = WitnessReplayConfiguration(timeoutMillis = 15_000, validationReserveMillis = 3_000)
        private lateinit var scene: EtsScene
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var sources: List<PropertyObservationSource>

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val root = testResourcesRoot()
            val source = root.resolve(MODULE)
            val bytes = Files.readAllBytes(source)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
            sources = listOf(PropertyObservationSource(module = MODULE, sha256 = hash))
            scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
            mapper = PropertyEtsMapper(scene, sourceRoots = listOf(root, source.parent))
        }
    }
}
