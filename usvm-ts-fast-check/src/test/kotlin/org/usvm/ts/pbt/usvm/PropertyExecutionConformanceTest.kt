package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.BackendErrorKind
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.testResourcePath
import org.usvm.ts.pbt.testResourcesRoot
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PropertyExecutionConformanceTest {
    @Test
    fun `shared preconditions have the same concrete projection and search classification`() {
        val cases = listOf(
            ContractCase("truePrecondition", ContractOutcome.HOLDS),
            ContractCase("falsePrecondition", ContractOutcome.PRECONDITION_REJECTED),
            ContractCase(
                exportName = "throwingPrecondition",
                expected = ContractOutcome.PROPERTY_ERROR,
                expectedBackendError = BackendErrorExpectation(
                    code = "entrypoint.precondition.threw",
                    path = "manifest.precondition",
                ),
            ),
            ContractCase(
                exportName = "nonBooleanPrecondition",
                expected = ContractOutcome.PROPERTY_ERROR,
                expectedBackendError = BackendErrorExpectation(
                    code = "entrypoint.result.invalid",
                    path = "manifest.precondition.result",
                ),
            ),
        )

        cases.forEach { case ->
            val property = property(
                predicateExport = "alwaysTrue",
                preconditionExport = case.exportName,
            )
            val manifest = property.toManifest()
            val mapping = mapper.map(manifest)

            val concreteOutcome = concreteOutcome(property, case.expectedBackendError)
            val projectionOutcome = projectionOutcome(
                projector.analyzePrecondition(manifest, mapping, exactCapability),
            )
            val searchOutcome = searchOutcome(
                searcher.search(manifest, mapping, exactCapability),
            )

            assertEquals(case.expected, concreteOutcome, "${case.exportName}: concrete")
            assertEquals(case.expected, projectionOutcome, "${case.exportName}: projection")
            assertEquals(case.expected, searchOutcome, "${case.exportName}: search")
        }
    }

    @Test
    fun `shared predicates have the same concrete and search classification`() {
        val cases = listOf(
            ContractCase("falsePredicate", ContractOutcome.PREDICATE_VIOLATION),
            ContractCase("throwingPredicate", ContractOutcome.PREDICATE_VIOLATION),
            ContractCase("literalFalsePredicate", ContractOutcome.PREDICATE_VIOLATION),
            ContractCase("literalTruePredicate", ContractOutcome.HOLDS),
            ContractCase("neverPredicate", ContractOutcome.PREDICATE_VIOLATION),
            ContractCase(
                exportName = "nonBooleanPredicate",
                expected = ContractOutcome.PROPERTY_ERROR,
                expectedBackendError = BackendErrorExpectation(
                    code = "entrypoint.result.invalid",
                    path = "manifest.predicate.result",
                ),
            ),
        )

        cases.forEach { case ->
            val property = property(predicateExport = case.exportName)
            val manifest = property.toManifest()
            val mapping = mapper.map(manifest)

            val concreteOutcome = concreteOutcome(property, case.expectedBackendError)
            val searchOutcome = searchOutcome(
                searcher.search(manifest, mapping, exactCapability),
            )

            assertEquals(case.expected, concreteOutcome, "${case.exportName}: concrete")
            assertEquals(case.expected, searchOutcome, "${case.exportName}: search")
        }
    }

    @Test
    fun `symbolic candidates retain inputs from before predicate mutation`() {
        val domain = ArrayDomain(
            element = IntegerDomain(min = 1, max = 1),
            minLength = 1,
            maxLength = 1,
        )
        val property = property(
            predicateExport = "mutatesAndFails",
            domain = domain,
        )
        val manifest = property.toManifest()
        val mapping = mapper.map(manifest)

        val concreteResult = backend.run(property, configuration)
        val searchResult = searcher.search(manifest, mapping, exactCapability)

        val expectedInput = JsConcreteValue.Array(
            elements = listOf(JsConcreteValue.number(1.0)),
        )
        val symbolicInputs = assertNotNull(searchResult.inputs)
        assertEquals(PropertyFailureKind.PROPERTY, concreteResult.failure?.kind)
        assertEquals(listOf(expectedInput), concreteResult.counterexample)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, searchResult.status)
        assertEquals(listOf(expectedInput), symbolicInputs)
        assertTrue(symbolicInputs.single() in domain)

        val replay = backend.run(
            property = property,
            configuration = configuration.copy(
                numRuns = 1,
                examples = listOf(symbolicInputs),
            ),
        )

        assertEquals(PropertyFailureKind.PROPERTY, replay.failure?.kind)
        assertEquals(symbolicInputs, replay.counterexample)
    }

    private fun concreteOutcome(
        property: PropertyDefinition,
        expectedBackendError: BackendErrorExpectation?,
    ): ContractOutcome = try {
        val result = backend.run(property, configuration)

        when (result.status) {
            PropertyRunStatus.SUCCESS -> ContractOutcome.HOLDS
            PropertyRunStatus.FAILURE -> when (result.failure?.kind) {
                PropertyFailureKind.PROPERTY -> ContractOutcome.PREDICATE_VIOLATION
                PropertyFailureKind.PRECONDITION_EXHAUSTED -> ContractOutcome.PRECONDITION_REJECTED
                PropertyFailureKind.TIMEOUT, null -> error("Unexpected concrete result: $result")
            }
        }
    } catch (failure: PbtBackendException) {
        val expected = checkNotNull(expectedBackendError) {
            "Unexpected concrete backend error: ${failure.kind}/${failure.code} at ${failure.path}"
        }

        assertEquals(BackendErrorKind.ENTRY_POINT, failure.kind)
        assertEquals(expected.code, failure.code)
        assertEquals(expected.path, failure.path)

        ContractOutcome.PROPERTY_ERROR
    }

    private fun projectionOutcome(result: UsvmPreconditionResult): ContractOutcome = when (result.status) {
        UsvmPreconditionStatus.ACCEPTED -> ContractOutcome.HOLDS
        UsvmPreconditionStatus.REJECTED -> ContractOutcome.PRECONDITION_REJECTED
        UsvmPreconditionStatus.PROPERTY_ERROR -> ContractOutcome.PROPERTY_ERROR
        else -> error("Unexpected projection result: $result")
    }

    private fun searchOutcome(result: UsvmPropertySearchResult): ContractOutcome = when (result.status) {
        UsvmPropertySearchStatus.VIOLATION_REACHED -> ContractOutcome.PREDICATE_VIOLATION
        UsvmPropertySearchStatus.NO_VIOLATION_REACHED -> ContractOutcome.HOLDS
        UsvmPropertySearchStatus.PRECONDITION_REJECTED -> ContractOutcome.PRECONDITION_REJECTED
        UsvmPropertySearchStatus.PROPERTY_ERROR -> ContractOutcome.PROPERTY_ERROR
        else -> error("Unexpected search result: $result")
    }

    private fun property(
        predicateExport: String,
        preconditionExport: String? = null,
        domain: org.usvm.ts.pbt.model.PropertyDomain = IntegerDomain(min = 0, max = 0),
    ) = PropertyDefinition(
        id = PropertyId("contract.$predicateExport.${preconditionExport ?: "none"}"),
        inputs = listOf(
            PropertyInput(
                name = "value",
                domain = domain,
            ),
        ),
        predicate = TypeScriptEntryPoint(
            module = MODULE,
            exportName = predicateExport,
        ),
        precondition = preconditionExport?.let { exportName ->
            TypeScriptEntryPoint(
                module = MODULE,
                exportName = exportName,
            )
        },
    )

    private data class ContractCase(
        val exportName: String,
        val expected: ContractOutcome,
        val expectedBackendError: BackendErrorExpectation? = null,
    )

    private data class BackendErrorExpectation(
        val code: String,
        val path: String,
    )

    private enum class ContractOutcome {
        HOLDS,
        PRECONDITION_REJECTED,
        PREDICATE_VIOLATION,
        PROPERTY_ERROR,
    }

    companion object {
        private const val MODULE = "PropertyExecutionContract.ts"

        private val exactCapability = ProjectionCapability(level = ProjectionLevel.EXACT)
        private val configuration = PropertyRunConfiguration(
            seed = 42,
            numRuns = 1,
            timeoutMillis = 1_000,
        )
        private val fixtureDirectory = testResourcesRoot().resolve("properties/contract")
        private val backend = FastCheckBackend(sourceRoots = listOf(fixtureDirectory))
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var projector: UsvmPropertyProjector
        private lateinit var searcher: UsvmPropertySearcher

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/properties/contract/$MODULE")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            val scene = EtsScene(listOf(file))

            mapper = PropertyEtsMapper(
                scene = scene,
                sourceRoots = listOf(fixtureDirectory),
            )
            projector = UsvmPropertyProjector(scene)
            searcher = UsvmPropertySearcher(scene)
        }
    }
}
