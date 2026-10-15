package org.usvm.ts.pbt.inference

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.EtsMappingDiagnostic
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
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearcher
import org.usvm.ts.pbt.usvm.UsvmPropertyViolationTarget
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryRelationSearchTest {
    @Test
    fun `different native input observations guide the same independent original oracle`() {
        val property = property(exportName = "independent")
        val forward = capture(property, seed = 10)
        val backward = capture(property, seed = 6, pair = ScalarObservationPair(RIGHT, LEFT))
        val manifest = property.toManifest()

        for ((inference, expectedRight) in listOf(forward to 0.0, backward to 2.0)) {
            val relation = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
            val focused = searcher.searchFocusedOnEntryRelation(
                manifest,
                mapper.map(manifest),
                CONCRETE,
                inference,
                relation,
            )

            assertEquals(ScalarRelationStatus.SAMPLED, relation.status)
            assertEquals(EtsMappingStatus.EXACT, focused.binding.status, focused.binding.diagnostics.toString())
            assertEquals(ObservationBuildStatus.UNVERIFIED, focused.inference.buildStatus)
            val result = assertNotNull(focused.search, focused.binding.diagnostics.toString())
            assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status)
            assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, result.target)
            val inputs = assertNotNull(result.inputs)
            val values = inputs.map { (it as JsConcreteValue.Number).toDouble() }
            assertEquals(listOf(1.0, expectedRight), values)
            val bound = focused.binding.targets.single()
            assertEquals(bound.leftInputIndex + 1, bound.left.stackSlot)
            assertEquals(bound.rightInputIndex + 1, bound.right.stackSlot)

            val replay = FastCheckBackend(sourceRoots = listOf(testResourcesRoot())).run(
                property,
                PropertyRunConfiguration(seed = 1, numRuns = 1, examples = listOf(inputs)),
            )

            assertEquals(PropertyRunStatus.FAILURE, replay.status)
        }
    }

    @Test
    fun `refuting an input equality is not itself an original property failure`() {
        val property = property(exportName = "reflexive")
        val inference = capture(property)
        val equality = inference.relations.single { it.template == ScalarRelationTemplate.STRICT_EQUAL }
        val manifest = property.toManifest()

        val focused = searcher.searchFocusedOnEntryRelation(
            manifest,
            mapper.map(manifest),
            CONCRETE,
            inference,
            equality,
        )
        val unfocused = searcher.search(manifest, mapper.map(manifest), CONCRETE)

        assertEquals(ScalarRelationStatus.SAMPLED, equality.status)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, focused.search?.status)
        assertNull(focused.search?.inputs)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, unfocused.status)
        assertEquals(inference.runId, focused.inference.runId)
    }

    @Test
    fun `an infeasible focus is a scoped no-finding rather than an engine failure`() {
        val property = property(
            exportName = "reflexive",
            leftDomain = IntegerDomain(min = 0, max = 1),
            rightDomain = IntegerDomain(min = 2, max = 3),
        )
        val inference = capture(property)
        val order = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
        val manifest = property.toManifest()

        val focused = searcher.searchFocusedOnEntryRelation(manifest, mapper.map(manifest), CONCRETE, inference, order)

        assertEquals(ScalarRelationStatus.SAMPLED, order.status)
        assertEquals(EtsMappingStatus.EXACT, focused.binding.status)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, focused.search?.status)
    }

    @Test
    fun `the original precondition still rejects a focused hypothesis refutation`() {
        val property = property().copy(precondition = TypeScriptEntryPoint(module = MODULE, exportName = "ordered"))
        val inference = capture(property)
        val order = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
        val manifest = property.toManifest()

        val focused = searcher.searchFocusedOnEntryRelation(manifest, mapper.map(manifest), CONCRETE, inference, order)

        assertEquals(ScalarRelationStatus.SAMPLED, order.status)
        assertEquals(UsvmPropertySearchStatus.PRECONDITION_REJECTED, focused.search?.status)
        assertNull(focused.search?.inputs)
    }

    @Test
    fun `focused comparison uses JavaScript NaN semantics without replacing the original oracle`() {
        val domain = NumberDomain()
        val property = property(
            exportName = "reflexive",
            leftDomain = domain,
            rightDomain = IntegerDomain(min = 1, max = 1),
        )
        val inference = capture(property, seed = 2)
        val relation = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
        val manifest = property.toManifest()

        val focused = searcher.searchFocusedOnEntryRelation(
            manifest,
            mapper.map(manifest),
            CONCRETE,
            inference,
            relation,
        )

        assertEquals(ScalarRelationStatus.SAMPLED, relation.status)
        val result = assertNotNull(focused.search)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status)
        val left = assertNotNull(result.inputs).first() as JsConcreteValue.Number
        assertTrue(left.toDouble().isNaN())
    }

    @Test
    fun `changed support precondition source and ordinary argument labels cannot activate focus`() {
        val property = property()
        val inference = capture(property)
        val relation = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
        val manifest = property.toManifest()
        val changed = listOf(
            manifest.copy(inputs = manifest.inputs.map { it.copy(domain = IntegerDomain(min = -1, max = 1)) }),
            manifest.copy(precondition = TypeScriptEntryPoint(module = MODULE, exportName = "nonNegative")),
        )

        for (other in changed) {
            val result = searcher.searchFocusedOnEntryRelation(other, mapper.map(other), CONCRETE, inference, relation)

            assertEquals(EtsMappingStatus.UNSUPPORTED, result.binding.status)
            assertNull(result.search)
        }

        val ordinary = relation.copy(pair = relation.pair.copy(left = LEFT.copy(kind = ObservationPointKind.ARGUMENT)))
        val staleSource = PropertyObservationSource(module = MODULE, sha256 = "0".repeat(n = 64))
        val stale = inference.copy(sources = listOf(staleSource))
        val ordinaryResult = searcher.searchFocusedOnEntryRelation(
            manifest,
            mapper.map(manifest),
            CONCRETE,
            inference.copy(relations = listOf(ordinary)),
            ordinary,
        )
        val staleResult = searcher.searchFocusedOnEntryRelation(
            manifest,
            mapper.map(manifest),
            CONCRETE,
            stale,
            relation,
        )

        assertNull(ordinaryResult.search)
        assertNull(staleResult.search)
        val ordinaryDiagnostic = ordinaryResult.binding.diagnostics.single().code
        val staleDiagnostic = staleResult.binding.diagnostics.single().code
        assertTrue(ordinaryDiagnostic.endsWith("point-unsupported"))
        assertTrue(staleDiagnostic.endsWith("source-mismatch"))
    }

    @Test
    fun `invalid indices mismatched snapshots nested contexts and ambiguous entries cannot guide search`() {
        val property = property()
        val inference = capture(property)
        val relation = inference.relations.single { it.template == ScalarRelationTemplate.LESS_OR_EQUAL }
        val manifest = property.toManifest()
        val mapping = mapper.map(manifest)
        val first = relation.evidence.first()
        val wrongValue = first.left.copy(value = first.left.value.copy(value = JsConcreteValue.number(99.0)))
        val nested = first.invocation.copy(parentInvocationId = 0)
        val invalid = listOf(
            relation.copy(pair = relation.pair.copy(left = LEFT.copy(inputIndex = 99))),
            relation.copy(evidence = listOf(first.copy(left = wrongValue))),
            relation.copy(evidence = listOf(first.copy(invocation = nested))),
        )

        for (candidate in invalid) {
            val result = searcher.searchFocusedOnEntryRelation(
                manifest,
                mapping,
                CONCRETE,
                inference.copy(relations = listOf(candidate)),
                candidate,
            )

            assertEquals(EtsMappingStatus.UNSUPPORTED, result.binding.status)
            assertNull(result.search)
        }

        val ambiguousPredicate = mapping.predicate.copy(
            status = EtsMappingStatus.AMBIGUOUS,
            diagnostics = listOf(EtsMappingDiagnostic(code = "test.ambiguous", message = "Two frontend candidates")),
        )
        val ambiguousMapping = mapping.copy(predicate = ambiguousPredicate)

        val ambiguous = searcher.searchFocusedOnEntryRelation(
            manifest,
            ambiguousMapping,
            CONCRETE,
            inference,
            relation,
        )

        assertEquals(EtsMappingStatus.UNSUPPORTED, ambiguous.binding.status)
        assertNull(ambiguous.search)
        assertEquals("usvm.entry-relation.entry-ambiguous", ambiguous.binding.diagnostics.single().code)
    }

    private fun capture(
        property: PropertyDefinition,
        seed: Int = 1,
        pair: ScalarObservationPair = ScalarObservationPair(LEFT, RIGHT),
    ): ScalarRelationInference {
        val result = FastCheckBackend(sourceRoots = listOf(testResourcesRoot())).run(
            property,
            PropertyRunConfiguration(
                seed = seed,
                numRuns = 2,
                observationRequest = PropertyObservationRequest(points = listOf(LEFT, RIGHT), sources = sources),
            ),
        )
        val observations = assertNotNull(result.observations)

        return inferScalarRelations(property, observations, listOf(pair))
    }

    private fun property(
        exportName: String = "ordered",
        leftDomain: PropertyDomain = IntegerDomain(min = 0, max = 2),
        rightDomain: PropertyDomain = IntegerDomain(min = 0, max = 2),
    ) = PropertyDefinition(
        id = PropertyId("entry.relation"),
        inputs = listOf(
            PropertyInput(name = "left", domain = leftDomain),
            PropertyInput(name = "right", domain = rightDomain),
        ),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = exportName),
        assertions = listOf(
            PropertyAssertion(
                id = "original",
                source = CALL,
                testedCall = CALL,
                operands = listOf(
                    PropertyOperand(LEFT.operandId, LEFT.source),
                    PropertyOperand(RIGHT.operandId, RIGHT.source),
                ),
            ),
        ),
    )

    companion object {
        private const val MODULE = "properties/inference/EntryRelations.ts"
        private val CALL = PropertySourcePoint(module = MODULE, line = 2, column = 5)
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
        private lateinit var sources: List<PropertyObservationSource>
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var searcher: UsvmPropertySearcher

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val root = testResourcesRoot()
            val source = root.resolve(MODULE)
            val bytes = Files.readAllBytes(source)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
            sources = listOf(PropertyObservationSource(module = MODULE, sha256 = hash))
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            val scene = EtsScene(listOf(file))
            mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(root, source.parent))
            searcher = UsvmPropertySearcher(scene)
        }
    }
}
