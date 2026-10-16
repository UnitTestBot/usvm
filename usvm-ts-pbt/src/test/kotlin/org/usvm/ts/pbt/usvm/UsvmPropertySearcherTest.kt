package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.machine.TsOptions
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.contains
import org.usvm.ts.pbt.testResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class UsvmPropertySearcherTest {
    @Test
    fun `reports when no violation is reachable`() {
        val manifest = manifest(predicateExport = "validProperty")

        val result = search(manifest)

        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
        assertEquals(PropertyId(manifest.propertyId), result.propertyId)
        assertNull(result.target)
        assertNull(result.inputs)
    }

    @Test
    fun `finds false predicate and resolves a candidate input`() {
        val manifest = manifest(predicateExport = "violatedProperty")

        val result = search(manifest)

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status)
        assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, result.target)
        assertEquals(listOf(JsConcreteValue.number(2.0)), result.inputs)
    }

    @Test
    fun `applies mapped precondition before searching the predicate`() {
        val manifest = manifest(
            predicateExport = "signedOneProperty",
            preconditionExport = "positive",
        )

        val result = search(manifest)

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status)
        assertEquals(listOf(JsConcreteValue.number(1.0)), result.inputs)
    }

    @Test
    fun `distinguishes rejected and exceptional preconditions from predicate violations`() {
        val rejected = manifest(
            predicateExport = "violatedProperty",
            preconditionExport = "falsePrecondition",
        )
        val exceptional = manifest(
            predicateExport = "violatedProperty",
            preconditionExport = "throwingPrecondition",
        )

        val rejectedResult = search(rejected)
        val exceptionalResult = search(exceptional)

        assertEquals(UsvmPropertySearchStatus.PRECONDITION_REJECTED, rejectedResult.status)
        assertEquals(UsvmPropertySearchStatus.PROPERTY_ERROR, exceptionalResult.status)
        assertNull(rejectedResult.target)
        assertNull(exceptionalResult.target)
        assertTrue(exceptionalResult.diagnostics.any { it.code == "usvm.precondition.threw" })
    }

    @Test
    fun `supports relational predicates with multiple calls`() {
        val manifest = manifest(predicateExport = "relationalProperty")

        val result = search(manifest)

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status)
        assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, result.target)
        assertNotNull(result.inputs)
    }

    @Test
    fun `classifies unexpected exceptions and supported assertion failures`() {
        val unexpected = search(manifest(predicateExport = "unexpectedException"))
        val assertion = search(manifest(predicateExport = "assertionFailure"))

        assertEquals(UsvmPropertyViolationTarget.UNEXPECTED_EXCEPTION, unexpected.target)
        assertEquals(UsvmPropertyViolationTarget.ASSERTION_FAILURE, assertion.target)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, unexpected.status)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, assertion.status)
    }

    @Test
    fun `resolves a symbolic string after reaching a violation`() {
        val manifest = manifest(
            predicateExport = "falseStringProperty",
            domain = StringDomain(minLength = 0, maxLength = 3),
        )

        val result = search(manifest)

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status)
        assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, result.target)
        val inputs = assertNotNull(result.inputs)
        assertTrue(inputs.single() in StringDomain(minLength = 0, maxLength = 3))
    }

    @Test
    fun `resolves string contents and nested array inputs`() {
        val stringDomain = StringDomain(minLength = 1, maxLength = 1)
        val nestedDomain = ArrayDomain(
            element = ArrayDomain(
                element = ConstantDomain(JsConcreteValue.number(2.0)),
                minLength = 1,
                maxLength = 1,
            ),
            minLength = 1,
            maxLength = 1,
        )
        val innerArray = JsConcreteValue.Array(elements = listOf(JsConcreteValue.number(2.0)))
        val expectedNested = listOf(JsConcreteValue.Array(elements = listOf(innerArray)))

        val string = search(manifest(predicateExport = "stringContentProperty", domain = stringDomain))
        val nested = search(manifest(predicateExport = "nestedArrayProperty", domain = nestedDomain))

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, string.status)
        assertEquals(listOf(JsConcreteValue.String(value = "a")), string.inputs)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, nested.status)
        assertEquals(expectedNested, nested.inputs)
    }

    @Test
    fun `preserves nested and optional array inputs through writes`() {
        val nestedDomain = ArrayDomain(
            element = ArrayDomain(
                element = ConstantDomain(JsConcreteValue.number(2.0)),
                minLength = 1,
                maxLength = 1,
            ),
            minLength = 1,
            maxLength = 1,
        )
        val optionalDomain = ArrayDomain(
            element = OptionalDomain(value = ConstantDomain(JsConcreteValue.number(2.0))),
            minLength = 1,
            maxLength = 1,
        )
        val innerArray = JsConcreteValue.Array(elements = listOf(JsConcreteValue.number(2.0)))
        val expectedNested = JsConcreteValue.Array(elements = listOf(innerArray))
        val expectedOptional = JsConcreteValue.Array(elements = listOf(JsConcreteValue.number(2.0)))

        val nested = search(manifest(predicateExport = "nestedArrayMutationProperty", domain = nestedDomain))
        val optional = search(
            manifest(predicateExport = "optionalNumberArrayMutationProperty", domain = optionalDomain),
        )

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, nested.status)
        assertEquals(listOf(expectedNested), nested.inputs)
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, optional.status)
        assertEquals(listOf(expectedOptional), optional.inputs)
    }

    @Test
    fun `resolves string array snapshots and preserves UTF-16 code units`() {
        val arrayDomain = ArrayDomain(
            element = StringDomain(minLength = 1, maxLength = 1),
            minLength = 1,
            maxLength = 1,
        )
        val loneSurrogate = manifest(
            predicateExport = "loneSurrogateProperty",
            domain = StringDomain(minLength = 1, maxLength = 1),
        )
        val supplementary = manifest(
            predicateExport = "supplementaryProperty",
            domain = StringDomain(minLength = 2, maxLength = 2),
        )

        val array = search(manifest(predicateExport = "stringArrayMutationProperty", domain = arrayDomain))
        val lone = search(loneSurrogate)
        val pair = search(supplementary)

        val expectedArray = JsConcreteValue.Array(elements = listOf(JsConcreteValue.String(value = "a")))
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, array.status)
        assertEquals(listOf(expectedArray), array.inputs)
        assertEquals(listOf(JsConcreteValue.String(value = "\uD800")), lone.inputs)
        assertEquals(listOf(JsConcreteValue.String(value = "\uD83D\uDE00")), pair.inputs)
    }

    @Test
    fun `projection respects an explicitly configured engine storage bound`() {
        val limited = UsvmPropertySearcher(scene = scene, tsOptions = TsOptions(maxArraySize = 2))
        val manifest = manifest(
            predicateExport = "falseStringProperty",
            domain = StringDomain(minLength = 3, maxLength = 3),
        )

        val result = limited.search(
            manifest = manifest,
            mapping = mapper.map(manifest),
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
        )

        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, result.status)
        assertNull(result.inputs)
    }

    @Test
    fun `preserves engine limitation reasons and a reached candidate`() {
        val unsupported = search(manifest(predicateExport = "unsupportedComputedProperty"))
        val mixed = search(
            manifest(predicateExport = "mixedComputedProperty", domain = IntegerDomain(min = 0, max = 1)),
        )

        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, unsupported.status)
        assertTrue(unsupported.diagnostics.any { it.code == "usvm.execution.unsupported" })
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, mixed.status)
        assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, mixed.target)
        assertEquals(listOf(JsConcreteValue.number(0.0)), mixed.inputs)
    }

    @Test
    fun `preserves a reached violation when another path is unsupported`() {
        val result = search(
            manifest(
                predicateExport = "mixedUnsupportedProperty",
                domain = IntegerDomain(min = 0, max = 1),
            ),
        )

        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, result.status)
        assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, result.target)
        assertEquals(listOf(JsConcreteValue.number(0.0)), result.inputs)
        assertTrue(result.diagnostics.any { it.code == "usvm.execution.unsupported" })
    }

    @Test
    fun `executes exception handlers and optional collections`() {
        val caughtDirect = search(manifest(predicateExport = "caughtDirectProperty"))
        val caughtHelper = search(manifest(predicateExport = "caughtHelperProperty"))
        val optionalArray = search(
            manifest(
                predicateExport = "optionalArrayProperty",
                domain = OptionalDomain(
                    ArrayDomain(
                        element = ConstantDomain(JsConcreteValue.number(2.0)),
                        minLength = 1,
                        maxLength = 1,
                    ),
                ),
            ),
        )

        listOf(caughtDirect, caughtHelper, optionalArray).forEach { result ->
            assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
            assertNull(result.target)
        }
    }

    @Test
    fun `resolves candidates for common Kotlin domains`() {
        val cases = listOf(
            "falseBooleanProperty" to BooleanDomain,
            "falseOptionalProperty" to OptionalDomain(IntegerDomain(min = -1, max = 1)),
            "falseTupleProperty" to TupleDomain(
                listOf(IntegerDomain(min = -1, max = 1), BooleanDomain),
            ),
            "falseNestedTupleProperty" to TupleDomain(
                listOf(OptionalDomain(IntegerDomain(min = -1, max = 1)), BooleanDomain),
            ),
            "falseArrayProperty" to ArrayDomain(
                element = IntegerDomain(min = -1, max = 1),
                minLength = 1,
                maxLength = 3,
            ),
        )

        cases.forEach { (predicateExport, domain) ->
            val result = search(manifest(predicateExport = predicateExport, domain = domain))

            assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, result.status, predicateExport)
            assertTrue(assertNotNull(result.inputs).single() in domain, predicateExport)
        }
    }

    @Test
    fun `reports async predicates as unsupported and non-boolean predicates as property errors`() {
        val async = manifest(
            predicateExport = "asyncValidProperty",
            executionKind = ExecutionKind.ASYNC,
        )
        val nonBoolean = manifest(predicateExport = "nonBooleanProperty")

        val asyncResult = search(async)
        val nonBooleanResult = search(nonBoolean)

        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, asyncResult.status)
        assertTrue(asyncResult.diagnostics.any { it.code == "usvm.predicate.async" })
        assertEquals(UsvmPropertySearchStatus.PROPERTY_ERROR, nonBooleanResult.status)
        assertTrue(nonBooleanResult.diagnostics.any { it.code == "usvm.predicate.result.non-boolean" })
    }

    @Test
    fun `distinguishes timeout from exhausted search`() {
        val manifest = manifest(predicateExport = "validProperty")
        val zeroTimeoutSearcher = UsvmPropertySearcher(
            scene = scene,
            machineOptions = UMachineOptions(solverType = SolverType.YICES, timeout = Duration.ZERO),
        )

        val result = zeroTimeoutSearcher.search(
            manifest = manifest,
            mapping = mapper.map(manifest),
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
        )

        assertEquals(UsvmPropertySearchStatus.TIMEOUT, result.status)
    }

    @Test
    fun `a caller deadline stops the machine before its independent timeout`() {
        val manifest = manifest(predicateExport = "violatedProperty")

        val result = searcher.search(
            manifest = manifest,
            mapping = mapper.map(manifest),
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            deadlineReached = { true },
        )

        assertEquals(UsvmPropertySearchStatus.TIMEOUT, result.status)
        assertNull(result.inputs)
    }

    private fun search(manifest: PropertyManifest): UsvmPropertySearchResult = searcher.search(
        manifest = manifest,
        mapping = mapper.map(manifest),
        concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
    )

    private fun manifest(
        predicateExport: String,
        preconditionExport: String? = null,
        executionKind: ExecutionKind = ExecutionKind.SYNC,
        domain: PropertyDomain = IntegerDomain(min = -3, max = 3),
    ) = PropertyManifest(
        propertyId = "usvm.search.$predicateExport.${executionKind.name.lowercase()}",
        inputs = listOf(PropertyInput(name = "value", domain = domain)),
        predicate = TypeScriptEntryPoint(
            module = "UsvmPropertySearchFixture.ts",
            exportName = predicateExport,
            executionKind = executionKind,
        ),
        precondition = preconditionExport?.let { exportName ->
            TypeScriptEntryPoint(
                module = "UsvmPropertySearchFixture.ts",
                exportName = exportName,
            )
        },
    )

    companion object {
        private lateinit var mapper: PropertyEtsMapper
        private lateinit var searcher: UsvmPropertySearcher
        private lateinit var scene: EtsScene

        @JvmStatic
        @BeforeAll
        fun loadFixture() {
            val source = testResourcePath("/usvm/UsvmPropertySearchFixture.ts")
            val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
            scene = EtsScene(listOf(file))

            mapper = PropertyEtsMapper(scene = scene, sourceRoots = listOf(source.parent))
            searcher = UsvmPropertySearcher(scene)
        }
    }
}
